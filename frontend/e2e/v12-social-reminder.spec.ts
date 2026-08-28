import { expect, test, type BrowserContext, type Page, type TestInfo } from '@playwright/test'
import { mkdir, writeFile } from 'node:fs/promises'
import { dirname } from 'node:path'

type Account = {
  role: 'A' | 'B' | 'C'
  username: string
  nickname: string
  password: string
  id: number
  publicId: string
  context: BrowserContext
  page: Page
}

type RequestEvidence = {
  actor: string
  method: string
  path: string
  status: number
  contentType: string
  byteLength: number
}

const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVR42mP8z8Dwn4GBgYGJAQoAHgQCAULPzWQAAAAASUVORK5CYII=',
  'base64',
)

const suffix = () => `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`

async function register(page: Page, seed: Pick<Account, 'username' | 'nickname' | 'password'>) {
  await page.goto('/register')
  await page.getByLabel('怎么称呼你').fill(seed.nickname)
  await page.getByLabel('用户名').fill(seed.username)
  await page.getByLabel('密码').fill(seed.password)
  await page.getByRole('button', { name: '开始记录' }).click()
  await expect(page).toHaveURL(/\/home(?:\?.*)?$/)
  const stored = await page.evaluate(() => ({
    token: localStorage.getItem('memospace_token'),
    user: JSON.parse(localStorage.getItem('memospace_user') || '{}'),
  }))
  expect(stored.token).toBeTruthy()
  expect(stored.user.publicId || stored.user.public_id).toMatch(/^\d{12}$/)
  return { id: Number(stored.user.id), publicId: String(stored.user.publicId || stored.user.public_id) }
}

async function browserRequest(
  account: Account,
  method: string,
  path: string,
  evidence: RequestEvidence[],
  body?: unknown,
) {
  const result = await account.page.evaluate(async ({ requestMethod, requestPath, requestBody }) => {
    const token = localStorage.getItem('memospace_token')
    const response = await fetch(requestPath, {
      method: requestMethod,
      cache: 'no-store',
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(requestBody === undefined ? {} : { 'Content-Type': 'application/json' }),
      },
      body: requestBody === undefined ? undefined : JSON.stringify(requestBody),
    })
    const contentType = response.headers.get('content-type') || ''
    const bytes = new Uint8Array(await response.arrayBuffer())
    let responseBody: unknown = null
    if (contentType.includes('json') && bytes.byteLength) {
      responseBody = JSON.parse(new TextDecoder().decode(bytes))
    }
    return { status: response.status, contentType, byteLength: bytes.byteLength, body: responseBody }
  }, { requestMethod: method, requestPath: path, requestBody: body })
  evidence.push({ actor: account.role, method, path, status: result.status, contentType: result.contentType, byteLength: result.byteLength })
  return result
}

async function shot(page: Page, testInfo: TestInfo, name: string) {
  const path = testInfo.outputPath(`${name}.png`)
  await page.screenshot({ path, fullPage: true })
  await testInfo.attach(name, { path, contentType: 'image/png' })
  return path
}

test.describe('MemoSpace V1.2 social and reminder acceptance', () => {
  test('numeric Memo ID, friend consent, realtime chat, reminder image authorization and persistence', async ({ browser }, testInfo) => {
    const runId = suffix()
    const contexts: BrowserContext[] = []
    const accounts: Account[] = []
    const requestEvidence: RequestEvidence[] = []
    const phases: string[] = []
    const screenshots: string[] = []
    const consoleErrors: string[] = []
    const failedRequests: string[] = []
    const password = 'MemoV12!2026'
    let reminderId = 0
    let reminderImageId = 0
    let chatMessageId = 0

    try {
      for (const seed of [
        { role: 'A' as const, username: `v12a_${runId}`, nickname: `V12甲-${runId.slice(-4)}` },
        { role: 'B' as const, username: `v12b_${runId}`, nickname: `V12乙-${runId.slice(-4)}` },
        { role: 'C' as const, username: `v12c_${runId}`, nickname: `V12丙-${runId.slice(-4)}` },
      ]) {
        const context = await browser.newContext()
        contexts.push(context)
        const page = await context.newPage()
        page.on('console', message => {
          if (message.type() === 'error') consoleErrors.push(`${seed.role}: ${message.text()}`)
        })
        page.on('requestfailed', request => failedRequests.push(`${seed.role}: ${request.method()} ${request.url()} ${request.failure()?.errorText || ''}`))
        const identity = await register(page, { ...seed, password })
        accounts.push({ ...seed, password, ...identity, context, page })
      }
      const [a, b, c] = accounts
      expect(new Set(accounts.map(account => account.publicId)).size).toBe(3)
      phases.push('three accounts registered with distinct immutable-looking 12-digit Memo IDs')

      await a.page.goto('/friends')
      await expect(a.page.getByLabel('我的 Memo ID')).toContainText(a.publicId)
      await a.page.getByLabel('12 位 Memo ID').fill(b.publicId)
      await a.page.getByRole('button', { name: '查找', exact: true }).click()
      const searchResult = a.page.locator('.friend-search-results article').filter({ hasText: b.nickname })
      await expect(searchResult).toContainText(b.publicId)
      await searchResult.getByRole('button', { name: '申请好友' }).click()
      await expect(a.page.getByRole('status')).toContainText('好友申请已发送')

      await b.page.goto('/friends')
      const incoming = b.page.locator('.request-list article').filter({ hasText: a.nickname })
      await expect(incoming).toContainText(a.publicId)
      await incoming.getByRole('button', { name: '接受好友申请' }).click()
      await expect(b.page.getByRole('status')).toContainText('已成为好友')
      await expect(b.page.locator('.friend-card').filter({ hasText: a.nickname })).toBeVisible()
      await a.page.reload()
      await expect(a.page.locator('.friend-card').filter({ hasText: b.nickname })).toBeVisible()
      screenshots.push(await shot(a.page, testInfo, '01-friend-established'))
      phases.push('A searched B by Memo ID, sent a request, and B accepted it')

      const bFriendCard = b.page.locator('.friend-card').filter({ hasText: a.nickname })
      await bFriendCard.getByRole('button', { name: `设置${a.nickname}` }).click()
      const settings = b.page.locator('.friend-settings-modal')
      const allowReminder = settings.locator('.setting-toggle').filter({ hasText: '允许对方直接创建提醒' }).locator('input')
      await allowReminder.uncheck()
      await settings.getByRole('button', { name: '保存设置' }).click()
      await expect(b.page.getByRole('status')).toContainText('好友设置已保存')
      phases.push('B disabled direct reminders from A')

      await Promise.all([
        a.page.goto(`/chat/${b.id}`),
        b.page.goto(`/chat/${a.id}`),
      ])
      await expect(a.page.getByRole('heading', { name: b.nickname })).toBeVisible()
      await expect(b.page.getByRole('heading', { name: a.nickname })).toBeVisible()
      await expect.poll(async () => a.page.locator('.chat-person').innerText(), { timeout: 20_000 }).toContain('在线')
      const chatText = `WebSocket 实时消息 ${runId}`
      await a.page.getByLabel('聊天消息').fill(chatText)
      await a.page.getByRole('button', { name: '发送', exact: true }).click()
      await expect(b.page.locator('.chat-bubble').filter({ hasText: chatText })).toBeVisible()
      const history = await browserRequest(b, 'GET', `/api/friends/${a.id}/messages`, requestEvidence)
      expect(history.status).toBe(200)
      const historyItems = (history.body as { items: Array<{ id: number; content: string }> }).items
      const storedChat = historyItems.find(message => message.content === chatText)
      expect(storedChat).toBeTruthy()
      chatMessageId = Number(storedChat?.id)
      await b.page.reload()
      await expect(b.page.locator('.chat-bubble').filter({ hasText: chatText })).toBeVisible()
      const outsiderChat = await browserRequest(c, 'GET', `/api/friends/${a.id}/messages`, requestEvidence)
      expect(outsiderChat.status).toBe(403)
      screenshots.push(await shot(b.page, testInfo, '02-websocket-chat-persisted'))
      phases.push('A sent a WebSocket message, B received it live, reload preserved history, and C was denied')

      const reminderTitle = `待确认图片提醒 ${runId}`
      await a.page.goto('/reminders')
      await a.page.getByRole('button', { name: '提醒好友', exact: true }).click()
      const modal = a.page.locator('.reminder-modal')
      await modal.getByLabel('提醒标题').fill(reminderTitle)
      await modal.getByLabel('补充说明').fill('关闭直达权限后，这条提醒必须由接收者确认。')
      await modal.getByLabel('把提醒发给谁').selectOption(String(b.id))
      await modal.locator('input[type="file"]').setInputFiles({ name: `reminder-${runId}.png`, mimeType: 'image/png', buffer: PNG })
      await modal.getByRole('button', { name: '创建提醒' }).click()
      await expect(a.page.getByRole('status')).toContainText('提醒已发给好友')

      const aReminders = await browserRequest(a, 'GET', '/api/reminders', requestEvidence)
      const created = (aReminders.body as Array<Record<string, unknown>>).find(item => item.title === reminderTitle)
      expect(created).toBeTruthy()
      reminderId = Number(created?.id)
      reminderImageId = Number(created?.image_file_id)
      expect(reminderId).toBeGreaterThan(0)
      expect(reminderImageId).toBeGreaterThan(0)

      await b.page.goto('/reminders')
      await b.page.getByRole('button', { name: /待确认/ }).first().click()
      const pendingCard = b.page.locator('.reminder-card').filter({ hasText: reminderTitle })
      await expect(pendingCard).toContainText('等待确认')
      const pendingImage = pendingCard.locator('img')
      await expect(pendingImage).toBeVisible()
      await expect.poll(() => pendingImage.evaluate(node => (node as HTMLImageElement).naturalWidth)).toBeGreaterThan(0)
      const pendingImageFetch = await browserRequest(b, 'GET', `/api/files/${reminderImageId}/content`, requestEvidence)
      expect(pendingImageFetch.status).toBe(200)
      expect(pendingImageFetch.contentType).toContain('image/png')
      expect(pendingImageFetch.byteLength).toBeGreaterThan(8)
      const outsiderImageFetch = await browserRequest(c, 'GET', `/api/files/${reminderImageId}/content`, requestEvidence)
      expect(outsiderImageFetch.status).toBe(403)
      await pendingCard.getByRole('button', { name: '接受', exact: true }).click()
      await expect(b.page.getByRole('status')).toContainText('已接受这条提醒')
      await expect(b.page.locator('.reminder-card').filter({ hasText: reminderTitle })).toHaveCount(0)
      await b.page.getByRole('button', { name: '即将到来', exact: true }).click()
      await expect(b.page.locator('.reminder-card').filter({ hasText: reminderTitle })).not.toContainText('等待确认')
      screenshots.push(await shot(b.page, testInfo, '03-reminder-accepted-with-real-image'))
      phases.push('A assigned an image reminder, B saw it pending and accepted it, while C could not fetch the image')

      const dueTitle = `到期通知 ${runId}`
      const dueAt = await a.page.evaluate(() => {
        const value = new Date(Date.now() + 8_000)
        const pad = (part: number) => String(part).padStart(2, '0')
        return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
      })
      const dueCreate = await browserRequest(a, 'POST', '/api/reminders', requestEvidence, {
        title: dueTitle,
        reminderKind: 'TASK',
        scheduleType: 'ONCE',
        remindAt: dueAt,
        timezone: 'Asia/Shanghai',
      })
      expect(dueCreate.status).toBe(200)
      await expect.poll(async () => {
        const notifications = await browserRequest(a, 'GET', '/api/notifications', requestEvidence)
        if (notifications.status !== 200 || !Array.isArray(notifications.body)) return false
        return notifications.body.some((item: Record<string, unknown>) => String(item.title).includes(dueTitle))
      }, { timeout: 30_000, intervals: [1_000, 2_000, 3_000] }).toBe(true)
      phases.push('scheduler emitted a persisted notification for an actually due reminder')

      expect(consoleErrors.filter(message => !message.includes('403'))).toEqual([])
      expect(failedRequests).toEqual([])
    } finally {
      const evidencePath = testInfo.outputPath('v12-evidence.json')
      await mkdir(dirname(evidencePath), { recursive: true })
      await writeFile(evidencePath, `${JSON.stringify({
        runId,
        generatedAt: new Date().toISOString(),
        accounts: accounts.map(({ role, username, nickname, id, publicId }) => ({ role, username, nickname, id, publicId })),
        chatMessageId,
        reminderId,
        reminderImageId,
        phases,
        requestEvidence,
        consoleErrors,
        failedRequests,
        screenshots,
      }, null, 2)}\n`, 'utf8')
      await testInfo.attach('v12-evidence', { path: evidencePath, contentType: 'application/json' })
      await Promise.allSettled(contexts.map(context => context.close()))
    }
  })
})
