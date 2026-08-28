import { expect, test, type BrowserContext, type Page, type TestInfo } from '@playwright/test'
import { mkdir, writeFile } from 'node:fs/promises'
import { dirname } from 'node:path'

type Account = {
  role: 'A' | 'B' | 'C'
  username: string
  nickname: string
  password: string
  context: BrowserContext
  page: Page
}

type FetchEvidence = {
  actor: string
  kind: 'memory' | 'file' | 'space' | 'other'
  url: string
  status: number
  contentType: string
  byteLength: number
}

type MediaNetworkEvidence = {
  actor: string
  method: string
  url: string
  status: number
  contentType: string
}

type MemoryFixture = {
  id: number
  fileId: number
  title: string
  visibility: 'PUBLIC' | 'PRIVATE' | 'RELATIONSHIP'
}

const PNG = Buffer.from(
  // A real 2 x 2 PNG. Keeping it inline makes the journey independent of repo fixtures.
  'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVR42mP8z8Dwn4GBgYGJAQoAHgQCAULPzWQAAAAASUVORK5CYII=',
  'base64',
)

const expectedAccess = {
  A: { PUBLIC: 200, PRIVATE: 200, RELATIONSHIP: 200 },
  B: { PUBLIC: 200, PRIVATE: 403, RELATIONSHIP: 200 },
  C: { PUBLIC: 200, PRIVATE: 403, RELATIONSHIP: 403 },
} as const

function uniqueSuffix() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`
}

async function registerViaUi(page: Page, account: Pick<Account, 'username' | 'nickname' | 'password'>) {
  await page.goto('/register')
  await page.getByLabel('怎么称呼你').fill(account.nickname)
  await page.getByLabel('用户名').fill(account.username)
  await page.getByLabel('密码').fill(account.password)
  await page.getByRole('button', { name: '开始记录' }).click()
  await expect(page).toHaveURL(/\/home(?:\?.*)?$/)
  await expect(page.getByRole('link', { name: '关系分类' }).first()).toBeVisible()
  await expect.poll(() => page.evaluate(() => Boolean(localStorage.getItem('memospace_token')))).toBe(true)
}

async function fetchInBrowser<T = unknown>(
  account: Account,
  path: string,
  evidence: FetchEvidence[],
): Promise<{ status: number; contentType: string; byteLength: number; body: T | null }> {
  const result = await account.page.evaluate(async (requestPath) => {
    const token = localStorage.getItem('memospace_token')
    const response = await fetch(requestPath, {
      cache: 'no-store',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    const contentType = response.headers.get('content-type') || ''
    const bytes = new Uint8Array(await response.arrayBuffer())
    let body: unknown = null
    if (contentType.includes('json') && bytes.length) {
      try { body = JSON.parse(new TextDecoder().decode(bytes)) } catch { body = null }
    }
    return { status: response.status, contentType, byteLength: bytes.byteLength, body }
  }, path)

  const kind = /\/memories\//.test(path)
    ? 'memory'
    : /\/files\//.test(path)
      ? 'file'
      : /\/spaces\//.test(path)
        ? 'space'
        : 'other'
  evidence.push({ actor: account.role, kind, url: path, ...result, body: undefined } as FetchEvidence)
  return result as { status: number; contentType: string; byteLength: number; body: T | null }
}

function observeMedia(account: Account, evidence: MediaNetworkEvidence[]) {
  account.page.on('response', response => {
    if (!/\/api\/files\/\d+\/content(?:\?|$)/.test(response.url())) return
    evidence.push({
      actor: account.role,
      method: response.request().method(),
      url: response.url(),
      status: response.status(),
      contentType: response.headers()['content-type'] || '',
    })
  })
}

async function screenshot(page: Page, testInfo: TestInfo, name: string) {
  const path = testInfo.outputPath(`${name}.png`)
  await page.screenshot({ path, fullPage: true })
  await testInfo.attach(name, { path, contentType: 'image/png' })
  return path
}

async function assertRenderedImage(page: Page, memoryId: number, title: string) {
  await page.goto(`/memory/${memoryId}`)
  await expect(page.getByRole('heading', { name: title, level: 1 })).toBeVisible()
  const image = page.locator('.media-gallery img').first()
  await image.scrollIntoViewIfNeeded()
  await expect(image).toBeVisible()
  await expect.poll(async () => image.evaluate(node => (node as HTMLImageElement).naturalWidth)).toBeGreaterThan(0)
  await expect.poll(async () => image.evaluate(node => (node as HTMLImageElement).naturalHeight)).toBeGreaterThan(0)
}

async function createPhotoMemory(
  account: Account,
  visibility: MemoryFixture['visibility'],
  title: string,
  relationshipSpaceName?: string,
): Promise<MemoryFixture> {
  await account.page.goto('/home')
  await account.page.getByRole('button', { name: '记录此刻' }).click()
  const modal = account.page.locator('.create-modal')
  await expect(modal.getByRole('heading', { name: '把这一刻留下来' })).toBeVisible()
  await modal.getByRole('button', { name: '照片', exact: true }).click()
  await modal.getByLabel('标题').fill(title)
  await modal.getByLabel('故事').fill(`V1.1 ${visibility} 媒体权限与真实加载验证。`)
  await modal.locator('input[type="file"]').setInputFiles({
    name: `${visibility.toLowerCase()}-${Date.now()}.png`,
    mimeType: 'image/png',
    buffer: PNG,
  })

  const label = visibility === 'PUBLIC' ? '公开' : visibility === 'PRIVATE' ? '仅自己' : '关系成员'
  await modal.getByLabel(label, { exact: true }).check()
  if (visibility === 'RELATIONSHIP') {
    if (!relationshipSpaceName) throw new Error('RELATIONSHIP Memory requires a relationship space')
    const spaceChoice = modal.locator('.check-pills label').filter({ hasText: relationshipSpaceName }).first()
    await expect(spaceChoice).toBeVisible()
    await spaceChoice.locator('input[type="checkbox"]').check()
  }

  await modal.getByRole('button', { name: '保存这段记忆' }).click()
  await account.page.waitForURL(/\/memory\/\d+$/)
  const match = account.page.url().match(/\/memory\/(\d+)$/)
  if (!match) throw new Error(`Could not read created memory id from ${account.page.url()}`)
  const id = Number(match[1])
  await assertRenderedImage(account.page, id, title)

  const detail = await account.page.evaluate(async memoryId => {
    const token = localStorage.getItem('memospace_token')
    const response = await fetch(`/api/memories/${memoryId}`, {
      headers: { Authorization: `Bearer ${token}` },
      cache: 'no-store',
    })
    return { status: response.status, body: await response.json() }
  }, id)
  expect(detail.status).toBe(200)
  expect(detail.body.media).toHaveLength(1)
  const fileId = Number(detail.body.media[0].file_id)
  expect(fileId).toBeGreaterThan(0)
  return { id, fileId, title, visibility }
}

test.describe('MemoSpace V1.1 product repair acceptance', () => {
  test('three-account relationship, real media authorization, and category retention journey', async ({ browser }, testInfo) => {
    const suffix = uniqueSuffix()
    const password = 'MemoV11!2026'
    const contexts: BrowserContext[] = []
    const accounts: Account[] = []
    const fetchEvidence: FetchEvidence[] = []
    const mediaNetwork: MediaNetworkEvidence[] = []
    const phases: string[] = []
    const screenshots: string[] = []
    const memories: MemoryFixture[] = []
    const customCategory = `旅行搭子-${suffix.slice(-4)}`
    let relationshipSpaceId = 0
    let relationshipSpaceName = ''
    let initialSpaceHref = ''

    try {
      for (const seed of [
        { role: 'A' as const, username: `v11a_${suffix}`, nickname: `V11甲-${suffix.slice(-4)}` },
        { role: 'B' as const, username: `v11b_${suffix}`, nickname: `V11乙-${suffix.slice(-4)}` },
        { role: 'C' as const, username: `v11c_${suffix}`, nickname: `V11丙-${suffix.slice(-4)}` },
      ]) {
        const context = await browser.newContext()
        contexts.push(context)
        const page = await context.newPage()
        const account: Account = { ...seed, password, context, page }
        accounts.push(account)
        observeMedia(account, mediaNetwork)
        await registerViaUi(page, account)
      }
      phases.push('three UI registrations complete')

      const [a, b, c] = accounts

      // A creates a custom category, then uses the default "恋人" category for the binding invitation.
      await a.page.goto('/relationships')
      await expect(a.page.getByRole('heading', { name: '关系分类', level: 1 })).toBeVisible()
      await expect(a.page.getByRole('button', { name: '打开恋人分类' })).toBeVisible()
      await expect(a.page.getByRole('button', { name: '打开死党分类' })).toBeVisible()
      await expect(a.page.getByRole('button', { name: '打开闺蜜分类' })).toBeVisible()
      await expect(a.page.getByRole('button', { name: '打开家人分类' })).toBeVisible()

      await a.page.getByLabel('自定义分类名称').fill(customCategory)
      await a.page.getByRole('button', { name: '创建分类' }).click()
      await expect(a.page.getByRole('status')).toContainText(`已创建「${customCategory}」`)
      await expect(a.page.getByRole('button', { name: `打开${customCategory}分类` })).toBeVisible()

      await a.page.getByLabel('搜索用户').fill(b.username)
      await a.page.getByRole('button', { name: '搜索', exact: true }).click()
      const bResult = a.page.locator('.people-results button').filter({ hasText: b.username })
      await expect(bResult).toBeVisible()
      await bResult.click()
      await a.page.getByLabel('选择关系分类').selectOption({ label: '恋人' })
      await a.page.getByLabel('邀请留言').fill('V1.1 三账号完整验收邀请')
      await a.page.getByRole('button', { name: '发送绑定邀请' }).click()
      await expect(a.page.getByRole('status')).toContainText('已向')
      phases.push('A searched B and sent lover invitation')

      await b.page.goto('/notifications')
      const invitation = b.page.locator('.notification-item').filter({ hasText: a.nickname })
      await expect(invitation).toContainText('恋人')
      await invitation.getByRole('button', { name: '接受', exact: true }).click()
      await expect(b.page.locator('.panel')).toContainText(/共同空间|现有共同空间/)
      phases.push('B accepted invitation')

      await a.page.goto('/relationships')
      await a.page.getByRole('button', { name: '打开恋人分类' }).click()
      await expect(a.page.getByRole('heading', { name: '恋人', exact: true, level: 1 })).toBeVisible()
      await expect(a.page.locator('.person-space-card').filter({ hasText: b.nickname })).toBeVisible()
      const loverSpaceLink = a.page.locator('.person-space-card').filter({ hasText: b.nickname }).locator('.shared-space-link')
      initialSpaceHref = (await loverSpaceLink.getAttribute('href')) || ''
      expect(initialSpaceHref).toMatch(/^\/space\/\d+$/)
      relationshipSpaceId = Number(initialSpaceHref.split('/').pop())
      relationshipSpaceName = (await loverSpaceLink.locator('b').textContent())?.trim() || ''
      expect(relationshipSpaceId).toBeGreaterThan(0)
      expect(relationshipSpaceName).not.toBe('')
      await loverSpaceLink.click()
      await expect(a.page).toHaveURL(new RegExp(`/space/${relationshipSpaceId}$`))
      await expect(a.page.getByRole('heading', { name: relationshipSpaceName, level: 1 })).toBeVisible()
      screenshots.push(await screenshot(a.page, testInfo, '01-relationship-space'))
      phases.push('A found B in lover category and entered their unique space')

      // The same relationship gets another label, and must still lead to the exact same space.
      await a.page.goto('/relationships/manage')
      const relationshipCard = a.page.locator('.relationship-manage-card').filter({ hasText: b.nickname })
      await expect(relationshipCard).toBeVisible()
      await relationshipCard.getByLabel(customCategory, { exact: true }).check()
      await relationshipCard.getByRole('button', { name: '保存分类' }).click()
      await expect(a.page.getByRole('status')).toContainText('共同空间仍然是原来的那一个')
      await a.page.goto('/relationships')
      await a.page.getByRole('button', { name: `打开${customCategory}分类` }).click()
      const customSpaceLink = a.page.locator('.person-space-card').filter({ hasText: b.nickname }).locator('.shared-space-link')
      await expect(customSpaceLink).toBeVisible()
      expect(await customSpaceLink.getAttribute('href')).toBe(initialSpaceHref)
      screenshots.push(await screenshot(a.page, testInfo, '02-multi-category-one-space'))
      phases.push('custom label points to the same relationship space')

      for (const visibility of ['PUBLIC', 'PRIVATE', 'RELATIONSHIP'] as const) {
        const title = `V11-${visibility}-${suffix}`
        const memory = await createPhotoMemory(
          a,
          visibility,
          title,
          visibility === 'RELATIONSHIP' ? relationshipSpaceName : undefined,
        )
        memories.push(memory)
        screenshots.push(await screenshot(a.page, testInfo, `03-${visibility.toLowerCase()}-media`))
      }
      phases.push('A uploaded and rendered PUBLIC, PRIVATE, and RELATIONSHIP PNG memories')

      // Legal viewers must render actual images, not just receive JSON metadata.
      const publicMemory = memories.find(item => item.visibility === 'PUBLIC')!
      const relationshipMemory = memories.find(item => item.visibility === 'RELATIONSHIP')!
      await assertRenderedImage(b.page, relationshipMemory.id, relationshipMemory.title)
      await assertRenderedImage(b.page, publicMemory.id, publicMemory.title)
      await assertRenderedImage(c.page, publicMemory.id, publicMemory.title)
      phases.push('B rendered relationship/public images and C rendered public image')

      // Check both Memory metadata and the real file body through browser fetch in all three contexts.
      for (const actor of accounts) {
        for (const memory of memories) {
          const expected = expectedAccess[actor.role][memory.visibility]
          const memoryResponse = await fetchInBrowser<Record<string, unknown>>(
            actor,
            `/api/memories/${memory.id}`,
            fetchEvidence,
          )
          expect(memoryResponse.status, `${actor.role} memory ${memory.visibility}`).toBe(expected)
          const fileResponse = await fetchInBrowser(
            actor,
            `/api/files/${memory.fileId}/content`,
            fetchEvidence,
          )
          expect(fileResponse.status, `${actor.role} file ${memory.visibility}`).toBe(expected)
          if (expected === 200) {
            expect(fileResponse.contentType).toContain('image/png')
            expect(fileResponse.byteLength).toBeGreaterThan(8)
          } else {
            expect(fileResponse.contentType).toContain('json')
          }
        }
      }
      phases.push('all 18 memory/file authorization checks matched the access matrix')

      // Hiding is a presentation preference only: the relationship space and media remain accessible.
      await a.page.goto('/relationships')
      await a.page.getByRole('button', { name: '隐藏恋人' }).click()
      await expect(a.page.getByRole('status')).toContainText('关系、共同空间和记忆均未删除')
      await expect(a.page.getByRole('button', { name: '打开恋人分类' })).toHaveCount(0)
      const hiddenLover = a.page.locator('.hidden-category-list article').filter({ hasText: '恋人' })
      await expect(hiddenLover).toContainText('段关系仍被完整保留')

      const spaceWhileHidden = await fetchInBrowser<Record<string, unknown>>(
        a,
        `/api/spaces/${relationshipSpaceId}`,
        fetchEvidence,
      )
      expect(spaceWhileHidden.status).toBe(200)
      const relationshipWhileHidden = await fetchInBrowser(
        b,
        `/api/memories/${relationshipMemory.id}`,
        fetchEvidence,
      )
      expect(relationshipWhileHidden.status).toBe(200)
      await a.page.goto(`/space/${relationshipSpaceId}`)
      await expect(a.page.getByRole('heading', { name: relationshipSpaceName, level: 1 })).toBeVisible()
      await expect(a.page.getByRole('link', { name: relationshipMemory.title })).toBeVisible()
      screenshots.push(await screenshot(a.page, testInfo, '04-hidden-category-data-survives'))

      await a.page.goto('/relationships')
      const restoreLover = a.page.locator('.hidden-category-list article').filter({ hasText: '恋人' })
      await restoreLover.getByRole('button', { name: '恢复显示' }).click()
      await expect(a.page.getByRole('status')).toContainText('已经重新显示')
      await a.page.getByRole('button', { name: '打开恋人分类' }).click()
      const restoredSpaceLink = a.page.locator('.person-space-card').filter({ hasText: b.nickname }).locator('.shared-space-link')
      await expect(restoredSpaceLink).toBeVisible()
      expect(await restoredSpaceLink.getAttribute('href')).toBe(initialSpaceHref)
      screenshots.push(await screenshot(a.page, testInfo, '05-restored-category-same-space'))
      phases.push('hidden category retained data and restored the original space entry')

      expect(mediaNetwork.some(item => item.actor === 'A' && item.status === 200 && item.contentType.includes('image/png'))).toBe(true)
      expect(mediaNetwork.some(item => item.actor === 'B' && item.status === 200 && item.contentType.includes('image/png'))).toBe(true)
      expect(mediaNetwork.some(item => item.actor === 'C' && item.status === 200 && item.contentType.includes('image/png'))).toBe(true)
    } finally {
      const evidence = {
        runId: suffix,
        generatedAt: new Date().toISOString(),
        baseURL: testInfo.project.use.baseURL,
        accounts: accounts.map(({ role, username, nickname }) => ({ role, username, nickname })),
        relationship: { spaceId: relationshipSpaceId, spaceName: relationshipSpaceName, href: initialSpaceHref },
        memories,
        expectedAccess,
        fetchEvidence,
        mediaNetwork,
        phases,
        screenshots,
      }
      const evidencePath = testInfo.outputPath('v11-evidence.json')
      await mkdir(dirname(evidencePath), { recursive: true })
      await writeFile(evidencePath, `${JSON.stringify(evidence, null, 2)}\n`, 'utf8')
      await testInfo.attach('v11-evidence', { path: evidencePath, contentType: 'application/json' })
      await Promise.allSettled(contexts.map(context => context.close()))
    }
  })
})
