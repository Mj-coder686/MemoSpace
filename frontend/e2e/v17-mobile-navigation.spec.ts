import { expect, test, type Page } from '@playwright/test'

test.use({ viewport: { width: 390, height: 844 } })

const demo = { id: 1, username: 'demo', nickname: '拾光者', public_id: '123400001234', publicId: '123400001234' }
const mia = { id: 2, username: 'mia', nickname: '米娅', public_id: '567800009876' }

async function installApiMock(page: Page) {
  await page.routeWebSocket('**/ws/chat', socket => {
    socket.onMessage(message => {
      try {
        const event = JSON.parse(String(message))
        if (event.type === 'AUTH') socket.send(JSON.stringify({ type: 'AUTH_OK', onlineFriendIds: [] }))
      } catch { /* protocol parsing is covered by the realtime integration tests */ }
    })
  })
  await page.route('**/api/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace(/^\/api/, '')
    let body: unknown = {}

    if (path === '/auth/login' && request.method() === 'POST') body = { token: 'mobile-route-test-token', user: demo }
    else if (path === '/home') body = { stats: { memories: 0, spaces: 1, places: 0 }, recent: [], today: [], feed: [] }
    else if (path === '/spaces') body = [{ id: 1, name: '我的私人空间', space_type: 'PERSONAL', status: 'ACTIVE', memoryCount: 0, photoCount: 0, placeCount: 0 }]
    else if (path === '/notifications' && request.method() === 'GET') body = []
    else if (path === '/relationships/invitations') body = []
    else if (path === '/relationship-categories') body = [{ id: 1, name: '家人', icon: 'home', is_visible: true, relationship_count: 0 }]
    else if (path === '/relationships') body = []
    else if (path === '/friends') body = []
    else if (path === '/friends/requests') body = []
    else if (path === '/reminders') body = []
    else if (path === '/users/search') body = [mia]
    else if (path === '/users/me') body = demo
    else if (path.includes('/appearance')) body = { backgroundColor: '#f5f2ec', backgroundBrightness: 100, backgroundOverlay: 0 }

    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  })
}

async function login(page: Page) {
  await page.goto('/login')
  await page.getByLabel('用户名').fill('demo')
  await page.getByLabel('密码').fill('Memo123!')
  await page.getByRole('button', { name: '进入拾光空间' }).click()
  await expect(page).toHaveURL(/\/home$/)
  await expect(page.getByRole('heading', { name: /早安/ })).toBeVisible()
}

async function expectNoBlankPage(page: Page, heading: string | RegExp) {
  await expect(page.getByRole('heading', { name: heading }).first()).toBeVisible()
  const content = await page.locator('.page-wrap').innerText()
  expect(content.trim().length).toBeGreaterThan(20)
}

test('手机端主要入口可连续进入并保持可操作', async ({ page }) => {
  const runtimeErrors: string[] = []
  page.on('pageerror', error => runtimeErrors.push(error.message))
  page.on('console', message => {
    if (message.type() === 'error') runtimeErrors.push(message.text())
  })

  await installApiMock(page)
  await login(page)

  await page.getByLabel('好友聊天').click()
  await expect(page).toHaveURL(/\/friends$/)
  await expectNoBlankPage(page, '好友中心')

  await page.getByLabel('通知').click()
  await expect(page).toHaveURL(/\/notifications$/)
  await expectNoBlankPage(page, /消息|通知/)

  await page.locator('.avatar-button').click()
  await expect(page).toHaveURL(/\/user\/\d+$/)
  await expectNoBlankPage(page, /拾光者|个人主页/)
  await page.getByRole('link', { name: '编辑资料' }).click()
  await expect(page).toHaveURL(/\/settings$/)
  await expectNoBlankPage(page, '设置')
  await page.getByLabel('昵称').fill('拾光者-手机测试')
  await expect(page.getByLabel('昵称')).toHaveValue('拾光者-手机测试')

  await page.goto('/relationships')
  await page.getByRole('button', { name: '查看全部空间' }).click()
  await expect(page).toHaveURL(/\/spaces$/)
  await expectNoBlankPage(page, '记忆空间')

  await page.goto('/relationships')
  await page.getByRole('button', { name: '关系管理' }).click()
  await expect(page).toHaveURL(/\/relationships\/manage$/)
  await expectNoBlankPage(page, '关系管理')

  await page.goto('/reminders')
  await expectNoBlankPage(page, '重要提醒')
  await expect(page.getByText(/这里暂时没有提醒|进行中的提醒/).first()).toBeVisible()

  expect(runtimeErrors.filter(message => !message.includes('favicon'))).toEqual([])
})

test('好友搜索支持昵称和 Memo ID 片段并给出明确结果', async ({ page }) => {
  await installApiMock(page)
  await login(page)
  await page.goto('/friends')

  const search = page.getByLabel('搜索 Memo ID、昵称或用户名')
  await search.fill('mia')
  await page.getByRole('button', { name: '查找', exact: true }).click()
  await expect(page.locator('.friend-search-results')).toContainText('米娅')

  const memoId = await page.locator('.friend-search-results small').first().innerText()
  const fragment = memoId.replace(/\D/g, '').slice(-4)
  await search.fill(fragment)
  await page.getByRole('button', { name: '查找', exact: true }).click()
  await expect(page.locator('.friend-search-results')).toContainText(fragment)
})
