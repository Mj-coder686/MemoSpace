import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

const assertNoSeriousViolations = async (page: import('@playwright/test').Page) => {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()
  const blocking = results.violations.filter((violation) => violation.impact === 'serious' || violation.impact === 'critical')
  expect(blocking, blocking.map((violation) => `${violation.id}: ${violation.help}`).join('\n')).toEqual([])
}

test('login starts empty, contains no demo credentials, and reports backend errors without clearing fields', async ({ page }) => {
  await page.route('**/api/auth/login', async (route) => {
    await route.fulfill({ status: 401, json: { message: '用户名或密码不正确' } })
  })
  await page.goto('/login')

  const username = page.getByLabel('用户名')
  const password = page.getByRole('textbox', { name: '密码', exact: true })
  await expect(username).toHaveValue('')
  await expect(password).toHaveValue('')
  await expect(page.locator('body')).not.toContainText(/demo|默认密码/i)

  await username.fill('real_friend')
  await password.fill('WrongPass1')
  await page.getByRole('button', { name: '进入拾光空间' }).click()
  await expect(page.getByRole('alert')).toContainText('用户名或密码不正确')
  await expect(username).toHaveValue('real_friend')
  await expect(password).toHaveValue('WrongPass1')
})

test('registration validates locally and submits the intended account', async ({ page }) => {
  let registerPayload: Record<string, string> | undefined
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) return route.fallback()
    if (url.pathname === '/api/auth/register') {
      registerPayload = route.request().postDataJSON()
      return route.fulfill({ json: { token: 'registered-token', user: { id: 8, username: 'memory_friend', nickname: '阿遥' } } })
    }
    if (url.pathname.includes('/users/me/appearance')) return route.fulfill({ json: {} })
    return route.fulfill({ json: [] })
  })

  await page.goto('/register')
  await page.getByRole('button', { name: '开始记录' }).click()
  await expect(page.getByText('请输入你希望被称呼的名字')).toBeVisible()
  await expect(page.getByText('请输入用户名')).toBeVisible()

  await page.getByLabel('怎么称呼你').fill('阿遥')
  await page.getByLabel('用户名').fill('memory_friend')
  await page.getByRole('textbox', { name: '密码', exact: true }).fill('MemoPass88')
  await page.getByRole('button', { name: '开始记录' }).click()
  await expect(page).toHaveURL(/\/home$/)
  expect(registerPayload).toEqual({ username: 'memory_friend', password: 'MemoPass88', nickname: '阿遥' })
})

test('mobile identity flow fits the viewport and has no blocking accessibility violations', async ({ page }) => {
  await page.setViewportSize({ width: 393, height: 852 })
  await page.emulateMedia({ colorScheme: 'light', reducedMotion: 'reduce' })
  await page.goto('/login?reason=banned')
  await expect(page.getByRole('alert')).toContainText('账号已被封禁')
  await expect(page.getByRole('button', { name: '进入拾光空间' })).toBeDisabled()
  await expect(page.locator('.identity-visual')).toBeVisible()
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await assertNoSeriousViolations(page)
})

test('admin login is separate, empty by default, and states its privacy boundary', async ({ page }) => {
  await page.goto('/admin/login')
  await expect(page.getByLabel('管理员账号')).toHaveValue('')
  await expect(page.getByLabel('管理员密码')).toHaveValue('')
  await expect(page.locator('body')).not.toContainText(/默认密码|demo/i)
  await expect(page.getByText('管理员不能浏览用户未被举报的私人记忆、图片或共享空间内容。')).toBeVisible()
  await assertNoSeriousViolations(page)
})
