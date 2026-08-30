import { expect, test } from '@playwright/test'

const adminUsername = process.env.MEMOSPACE_ADMIN_USERNAME || 'admin'
const adminPassword = process.env.MEMOSPACE_ADMIN_PASSWORD || 'MemoAdmin2026!'

test('administrator login, privacy boundary, user denial and smooth identity switch', async ({ browser }, testInfo) => {
  const adminContext = await browser.newContext()
  const adminPage = await adminContext.newPage()
  await adminPage.goto('/login')
  const switcher = adminPage.getByRole('navigation', { name: '登录身份切换' })
  await expect(switcher).toBeVisible()
  await switcher.getByRole('link', { name: '管理员' }).click()
  await expect(adminPage).toHaveURL(/\/admin\/login$/)
  await expect(adminPage.getByRole('heading', { name: '管理员登录' })).toBeVisible()
  await expect(adminPage.locator('.mode-slider')).toHaveCSS('transform', /matrix/)

  await adminPage.getByLabel('管理员账号').fill(adminUsername)
  await adminPage.getByLabel('管理员密码').fill(adminPassword)
  await adminPage.getByRole('button', { name: '进入管理员中心' }).click()
  await expect(adminPage).toHaveURL(/\/admin$/)
  await expect(adminPage.getByRole('heading', { name: '朋友账号管理' })).toBeVisible()
  await expect(adminPage.locator('.admin-user-row').first()).toBeVisible()
  await expect(adminPage.getByRole('button', { name: '修改 ID' }).first()).toBeVisible()
  await expect(adminPage.getByRole('button', { name: '重置密码' }).first()).toBeVisible()

  const adminId = await adminPage.evaluate(async () => {
    const token = localStorage.getItem('memospace_admin_token') || ''
    const response = await fetch('/api/admin/me', { headers: { Authorization: `Bearer ${token}` } })
    return Number((await response.json()).id)
  })

  const privacy = await adminPage.evaluate(async () => {
    const token = localStorage.getItem('memospace_admin_token') || ''
    const request = (path: string) => fetch(path, { headers: { Authorization: `Bearer ${token}` } }).then(async response => ({ status: response.status, body: await response.text() }))
    return Promise.all([request('/api/memories'), request('/api/files/1/content'), request('/api/spaces'), request('/api/notifications')])
  })
  expect(privacy.map(item => item.status)).toEqual([403, 403, 403, 403])
  expect(privacy.every(item => item.body.includes('管理员会话不能访问用户'))).toBe(true)

  await adminPage.getByRole('button', { name: '修改 ID' }).first().click()
  await expect(adminPage.getByRole('heading', { name: '修改 Memo ID' })).toBeVisible()
  await adminPage.getByRole('button', { name: '取消' }).click()
  await adminPage.screenshot({ path: testInfo.outputPath('admin-dashboard.png'), fullPage: true })

  const userContext = await browser.newContext()
  const userPage = await userContext.newPage()
  await userPage.goto('/login')
  await userPage.getByLabel('用户名').fill('demo')
  await userPage.getByLabel('密码').fill('Memo123!')
  await userPage.getByRole('button', { name: '进入拾光空间' }).click()
  await expect(userPage).toHaveURL(/\/home$/)
  const normalToken = await userPage.evaluate(() => localStorage.getItem('memospace_token') || '')
  const denied = await userPage.evaluate(async token => (await fetch('/api/admin/users', { headers: { Authorization: `Bearer ${token}` } })).status, normalToken)
  expect(denied).toBe(403)
  const adminHidden = await userPage.evaluate(async ({ token, adminId, adminUsername }) => {
    const headers = { Authorization: `Bearer ${token}` }
    const search = await fetch(`/api/users/search?q=${encodeURIComponent(adminUsername)}`, { headers })
    const profile = await fetch(`/api/users/${adminId}`, { headers })
    const follow = await fetch(`/api/users/${adminId}/follow`, { method: 'POST', headers })
    return { results: await search.json(), profile: profile.status, follow: follow.status }
  }, { token: normalToken, adminId, adminUsername })
  expect(adminHidden.results).toEqual([])
  expect(adminHidden.profile).toBe(404)
  expect(adminHidden.follow).toBe(404)

  await Promise.all([adminContext.close(), userContext.close()])
})
