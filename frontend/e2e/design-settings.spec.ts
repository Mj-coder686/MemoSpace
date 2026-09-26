import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

const user = { id: 7, username: 'memory_friend', nickname: '阿遥', publicId: '100000000007', public_id: '100000000007', avatar: '', bio: '认真收藏日常。', location: '武汉', gender: '', birthday: '' }

const seedSession = async (page: Page) => {
  await page.addInitScript((value) => {
    localStorage.setItem('memospace_token', 'settings-design-test')
    localStorage.setItem('memospace_user', JSON.stringify(value))
  }, user)
}

const routeSettings = async (page: Page, appearanceFails = false) => {
  const profileBodies: unknown[] = []
  const appearanceBodies: unknown[] = []
  const passwordBodies: unknown[] = []
  await page.route('**/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (!path.startsWith('/api/')) return route.fallback()
    if (path === '/api/users/me' && request.method() === 'GET') return route.fulfill({ json: user })
    if (path === '/api/users/me' && request.method() === 'PUT') { profileBodies.push(request.postDataJSON()); return route.fulfill({ json: user }) }
    if (path === '/api/users/me/appearance' && request.method() === 'GET') return route.fulfill({ json: { background_color: '#f4efe7', background_file_id: null, background_brightness: 90, background_overlay: 12 } })
    if (path === '/api/users/me/appearance' && request.method() === 'PUT') {
      appearanceBodies.push(request.postDataJSON())
      if (appearanceFails) return route.fulfill({ status: 500, json: { message: '背景暂时无法保存' } })
      return route.fulfill({ json: { background_color: '#e9e1d5', background_file_id: null, background_brightness: 82, background_overlay: 18 } })
    }
    if (path === '/api/users/me/password') { passwordBodies.push(request.postDataJSON()); return route.fulfill({ json: { message: '密码已更新' } }) }
    return route.fulfill({ json: {} })
  })
  return { profileBodies, appearanceBodies, passwordBodies }
}

test('settings keep identity, appearance, security, and session boundaries explicit', async ({ page }) => {
  await seedSession(page)
  const capture = await routeSettings(page)
  await page.goto('/settings')

  await expect(page.getByRole('heading', { name: '设置', exact: true })).toBeVisible()
  await expect(page.getByText('100000000007')).toBeVisible()
  await page.getByLabel('昵称').fill('阿遥的新名字')
  await page.getByRole('button', { name: '保存个人资料' }).click()
  await expect.poll(() => capture.profileBodies.length).toBe(1)
  expect(capture.profileBodies[0]).toMatchObject({ nickname: '阿遥的新名字', bio: '认真收藏日常。', location: '武汉' })

  await page.getByLabel('当前密码').fill('old-password')
  await page.getByLabel(/^新密码/).fill('new-password')
  await page.getByLabel('再次输入新密码').fill('new-password')
  await page.getByRole('button', { name: '更新密码' }).click()
  expect(capture.passwordBodies).toEqual([{ oldPassword: 'old-password', newPassword: 'new-password' }])
  await expect(page.getByText('下次登录请使用新密码')).toBeVisible()

  await page.getByRole('button', { name: '退出当前账号' }).click()
  const dialog = page.getByRole('dialog', { name: '退出当前账号？' })
  await expect(dialog).toContainText('所有云端数据都会保留')
})

test('failed appearance save restores the last server-backed value', async ({ page }) => {
  await seedSession(page)
  await routeSettings(page, true)
  await page.goto('/settings')

  const color = page.locator('#settings-appearance input[type="color"]')
  await expect(color).toHaveValue('#f4efe7')
  await color.fill('#112233')
  await page.getByRole('button', { name: '保存外观' }).click()
  await expect(page.getByText('背景暂时无法保存')).toBeVisible()
  await expect(color).toHaveValue('#f4efe7')
})

test('compact settings behave as grouped subpages and stay accessible', async ({ page }) => {
  await seedSession(page)
  await routeSettings(page)
  await page.setViewportSize({ width: 393, height: 852 })
  await page.goto('/settings')

  await expect(page.getByRole('heading', { name: '账号身份' })).toBeVisible()
  const headerOrder = await page.locator('.settings-page > header > div').evaluate(element => Array.from(element.children).map(child => child.getBoundingClientRect().top))
  expect(headerOrder[0]).toBeLessThan(headerOrder[1])
  expect(headerOrder[1]).toBeLessThan(headerOrder[2])
  await expect(page.getByRole('heading', { name: '外观' })).toBeHidden()
  await page.getByRole('button', { name: '外观', exact: true }).click()
  await expect(page.getByRole('heading', { name: '外观' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '账号身份' })).toBeHidden()
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze()
  expect(results.violations.filter(item => item.impact === 'serious' || item.impact === 'critical')).toEqual([])
})
