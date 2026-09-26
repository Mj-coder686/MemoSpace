import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

const seedSession = async (page: import('@playwright/test').Page) => {
  await page.addInitScript(() => {
    localStorage.setItem('memospace_token', 'design-shell-test')
    localStorage.setItem('memospace_user', JSON.stringify({ id: 1, username: 'shell', nickname: '拾光用户', publicId: '10000001' }))
  })
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) return route.fallback()
    if (url.pathname.includes('/users/me/appearance')) return route.fulfill({ json: {} })
    return route.fulfill({ json: [] })
  })
}

test('shell switches at 840px without duplicate navigation', async ({ page }) => {
  await page.setViewportSize({ width: 840, height: 800 })
  await page.goto('/__shell')
  await expect(page.locator('.shell-web-topbar')).toBeVisible()
  await expect(page.locator('.shell-compact-topbar')).toBeHidden()
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.setViewportSize({ width: 839, height: 800 })
  await expect(page.locator('.shell-web-topbar')).toBeHidden()
  await expect(page.locator('.shell-compact-topbar')).toBeVisible()
  await expect(page.locator('.shell-bottom-nav')).toBeVisible()
  await expect(page.locator('.quick-dock')).toHaveCount(0)
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})

test('back navigation restores the previous route scroll position', async ({ page }) => {
  await seedSession(page)
  await page.setViewportSize({ width: 1280, height: 800 })
  await page.goto('/__shell')
  await expect(page.getByRole('heading', { name: '跨端壳层检查页' })).toBeVisible()
  await page.waitForFunction(() => (document.scrollingElement?.scrollHeight || 0) > window.innerHeight + 700)
  await page.waitForTimeout(100)
  await page.evaluate(() => window.scrollTo(0, 700))
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(650)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '记忆' }).click()
  await expect(page).toHaveURL(/\/memories$/)
  await page.goBack()
  await expect(page).toHaveURL(/\/__shell$/)
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(650)
})

test('compact navigation changes routes without a document reload', async ({ page }) => {
  await seedSession(page)
  await page.setViewportSize({ width: 393, height: 852 })
  await page.goto('/memories')
  await expect(page.getByRole('navigation', { name: '当前分区导航' })).toContainText('时间轴')
  const navigationCount = await page.evaluate(() => performance.getEntriesByType('navigation').length)
  await page.getByRole('navigation', { name: '底部导航' }).getByRole('link', { name: '关系' }).click()
  await expect(page).toHaveURL(/\/relationships$/)
  await expect(page.getByRole('navigation', { name: '当前分区导航' })).toContainText('分类')
  await expect.poll(() => page.evaluate(() => performance.getEntriesByType('navigation').length)).toBe(navigationCount)
})

test('compact shell and personal sheet have no blocking axe violations', async ({ page }) => {
  await page.setViewportSize({ width: 393, height: 852 })
  await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' })
  await page.goto('/__shell')
  await page.waitForTimeout(400)
  await page.getByRole('button', { name: '打开个人菜单' }).click()
  await expect(page.getByRole('dialog', { name: '个人' })).toBeVisible()
  await page.waitForTimeout(350)
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze()
  const blocking = results.violations.filter((violation) => violation.impact === 'serious' || violation.impact === 'critical')
  expect(blocking, blocking.map((violation) => `${violation.id}: ${violation.help}`).join('\n')).toEqual([])
})
