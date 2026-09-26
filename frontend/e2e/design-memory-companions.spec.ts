import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

const pixel = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64')
const memories = [
  { id: 11, title: '江边的晚风', content: '赶在日落前到了江边。', memory_type: 'PHOTO', occurred_at: '2026-09-23T18:20:00', location: '江边', cover_file_id: 901, cover_mime_type: 'image/png' },
  { id: 12, title: '巷口的雨', content: '雨停后路灯亮了。', memory_type: 'VIDEO', occurred_at: '2026-09-12T20:10:00', location: '旧巷', cover_file_id: 902, cover_mime_type: 'video/mp4' },
  { id: 13, title: '八月的树影', content: '树影慢慢走过墙面。', memory_type: 'PHOTO', occurred_at: '2026-08-28T15:00:00', cover_file_id: 903, cover_mime_type: 'image/png' },
]
const places = [
  { id: 31, title: '山顶的风', location: '南山', latitude: 30.72, longitude: 104.02, occurred_at: '2026-09-18T17:30:00' },
  { id: 32, title: '河边散步', location: '锦江', latitude: 30.66, longitude: 104.08, occurred_at: '2026-09-21T19:00:00' },
]

const seedSession = async (page: Page, denyLocation = false) => {
  await page.addInitScript(({ deny }) => {
    localStorage.setItem('memospace_token', 'companion-design-test')
    localStorage.setItem('memospace_user', JSON.stringify({ id: 7, username: 'memory_friend', nickname: '阿遥', publicId: '10000007' }))
    if (deny) {
      Object.defineProperty(navigator, 'geolocation', { configurable: true, value: {
        getCurrentPosition: (_success: unknown, failure: (error: { code: number; PERMISSION_DENIED: number }) => void) => failure({ code: 1, PERMISSION_DENIED: 1 }),
      } })
    }
  }, { deny: denyLocation })
}

const routeCompanions = async (page: Page) => {
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (!url.pathname.startsWith('/api/')) return route.fallback()
    if (url.pathname === '/api/memories') return route.fulfill({ json: memories })
    if (url.pathname === '/api/calendar') return route.fulfill({ json: [{ memory_date: '2026-09-23', count: 2, preview: '江边的晚风' }] })
    if (url.pathname === '/api/calendar/day') return route.fulfill({ json: [memories[0]] })
    if (url.pathname === '/api/map') return route.fulfill({ json: places })
    if (/\/api\/files\/(901|903)\/content$/.test(url.pathname)) return route.fulfill({ contentType: 'image/png', body: pixel })
    if (url.pathname === '/api/files/902/content') return route.fulfill({ status: 200, contentType: 'video/mp4', body: Buffer.from([]) })
    if (url.pathname === '/api/spaces') return route.fulfill({ json: [] })
    if (url.pathname.includes('/users/me/appearance')) return route.fulfill({ json: {} })
    return route.fulfill({ json: [] })
  })
}

test('photo archive groups real media by month and starts a photo memory', async ({ page }) => {
  await seedSession(page)
  await routeCompanions(page)
  await page.goto('/photos')

  await expect(page.getByRole('heading', { name: '记忆相册' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '2026年 9月' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '2026年 8月' })).toBeVisible()
  await expect(page.getByRole('link', { name: '打开记忆：江边的晚风' })).toBeVisible()

  await page.locator('#main-content').getByRole('button', { name: '添加照片记忆' }).click()
  const dialog = page.getByRole('dialog', { name: '把这一刻留下来' })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByRole('button', { name: '照片 一张或多张照片' })).toHaveAttribute('aria-pressed', 'true')
  await expect(dialog.getByText('添加照片')).toBeVisible()
})

test('calendar distinguishes today, selected day, and date-level memories', async ({ page }) => {
  await seedSession(page)
  await routeCompanions(page)
  await page.goto('/calendar?date=2026-09-23')

  await expect(page.getByRole('heading', { name: '回到某一天' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '9 月' })).toBeVisible()
  await expect(page.getByRole('button', { name: /2026年9月23日，2条记忆/ })).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByRole('heading', { name: '9月23日' })).toBeVisible()
  await expect(page.getByRole('link', { name: '打开记忆：江边的晚风' })).toBeVisible()
  await expect(page.getByRole('button', { name: '上一个月' })).toBeVisible()
  await expect(page.getByRole('button', { name: '下一个月' })).toBeVisible()
  await expect(page.getByRole('button', { name: '今天', exact: true })).toBeVisible()
})

test('map keeps the place list usable and explains denied location access', async ({ page }) => {
  await seedSession(page, true)
  await routeCompanions(page)
  await page.goto('/map')

  await expect(page.getByRole('heading', { name: '我的足迹' })).toBeVisible()
  await page.locator('.map-place-list').getByRole('button', { name: /山顶的风/ }).click()
  await expect(page.getByRole('heading', { name: '山顶的风' })).toBeVisible()
  await expect(page.getByRole('link', { name: /查看这段记忆/ })).toHaveAttribute('href', '/memory/31')

  await page.getByRole('button', { name: '显示当前位置' }).click()
  await expect(page.getByText('定位没有成功')).toBeVisible()
  await expect(page.getByText('定位权限已被拒绝，请在浏览器或系统设置中允许后重试')).toBeVisible()
  await expect(page.locator('.map-place-list').getByRole('button', { name: /河边散步/ })).toBeVisible()
})

test('compact companion views fit the viewport and retain accessible controls', async ({ page }) => {
  await seedSession(page)
  await routeCompanions(page)
  await page.setViewportSize({ width: 393, height: 852 })
  await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' })

  for (const path of ['/photos', '/calendar?date=2026-09-23', '/map']) {
    await page.goto(path)
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  }

  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze()
  const blocking = results.violations.filter((violation) => violation.impact === 'serious' || violation.impact === 'critical')
  expect(blocking, blocking.map((violation) => `${violation.id}: ${violation.help}`).join('\n')).toEqual([])
})
