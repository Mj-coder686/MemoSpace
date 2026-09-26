import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

const memories = [
  { id: 11, title: '江边的晚风', content: '我们赶在日落前走到了江边。', memory_type: 'TEXT', occurred_at: '2026-08-24T18:20:00', location: '江边', creator_nickname: '阿遥' },
  { id: 12, title: '周末早餐', content: '很慢的一顿早餐。', memory_type: 'TEXT', occurred_at: '2026-08-22T09:10:00', creator_nickname: '阿遥' },
  { id: 13, title: '雨停以后', content: '空气里有树叶的味道。', memory_type: 'TEXT', occurred_at: '2026-08-19T16:00:00', creator_nickname: '阿遥' },
]

const spaces = [
  { id: 1, name: '我的生活档案', space_type: 'PERSONAL', status: 'ACTIVE', memoryCount: 18, photoCount: 7, primary_color: '#716784' },
  { id: 2, name: '和小岚的共同空间', space_type: 'RELATIONSHIP', status: 'ACTIVE', memoryCount: 9, photoCount: 5, primary_color: '#995a66' },
]

const reminders = [
  { id: 1, title: '给妈妈打电话', reminder_kind: 'TASK', remind_at: '2026-09-23T20:00:00', status: 'ACTIVE', acceptance_status: 'ACCEPTED' },
  { id: 2, title: '小岚的生日', reminder_kind: 'BIRTHDAY', remind_at: '2026-10-08T09:00:00', status: 'ACTIVE', acceptance_status: 'ACCEPTED' },
]

const seedSession = async (page: import('@playwright/test').Page) => {
  await page.addInitScript(() => {
    localStorage.setItem('memospace_token', 'home-design-test')
    localStorage.setItem('memospace_user', JSON.stringify({ id: 7, username: 'memory_friend', nickname: '阿遥', publicId: '10000007' }))
  })
}

const routeHome = async (page: import('@playwright/test').Page, options: { homeFails?: boolean; empty?: boolean } = {}) => {
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) return route.fallback()
    if (url.pathname === '/api/home') {
      if (options.homeFails) return route.fulfill({ status: 503, json: { message: '记忆摘要服务暂时不可用' } })
      return route.fulfill({ json: options.empty
        ? { stats: { memories: 0, spaces: 1, places: 0 }, recent: [], today: [], feed: [] }
        : { stats: { memories: 18, spaces: 2, places: 4 }, recent: memories, today: [memories[1]], feed: [memories[2]] } })
    }
    if (url.pathname === '/api/spaces') return route.fulfill({ json: options.empty ? spaces.slice(0, 1) : spaces })
    if (url.pathname === '/api/reminders') return route.fulfill({ json: options.empty ? [] : reminders })
    if (url.pathname === '/api/notifications') return route.fulfill({ json: [] })
    if (url.pathname.includes('/users/me/appearance')) return route.fulfill({ json: {} })
    return route.fulfill({ json: [] })
  })
}

test('home is an editorial overview and its primary action opens the create flow', async ({ page }) => {
  await seedSession(page)
  await routeHome(page)
  await page.goto('/home')

  await expect(page.getByRole('heading', { name: /阿遥/ })).toBeVisible()
  await expect(page.getByRole('heading', { name: '最近记忆' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '接下来' })).toBeVisible()
  await expect(page.getByText('和小岚的共同空间')).toBeVisible()
  await expect(page.locator('.hero-stats')).toHaveCount(0)

  await page.locator('.home-welcome').getByRole('button', { name: '记录此刻' }).click()
  await expect(page.getByRole('dialog', { name: '把这一刻留下来' })).toBeVisible()
})

test('home keeps spaces and reminders usable when the summary endpoint fails', async ({ page }) => {
  await seedSession(page)
  await routeHome(page, { homeFails: true })
  await page.goto('/home')

  await expect(page.getByText('记忆摘要服务暂时不可用')).toBeVisible()
  await expect(page.getByText('和小岚的共同空间')).toBeVisible()
  await expect(page.getByText('给妈妈打电话')).toBeVisible()
  await expect(page.getByText('首页的记忆摘要加载失败，但空间和提醒仍可继续使用。')).toBeVisible()
})

test('new users see one staged first step instead of several empty panels', async ({ page }) => {
  await seedSession(page)
  await routeHome(page, { empty: true })
  await page.goto('/home')

  await expect(page.getByRole('heading', { name: '先留下一件今天不想忘记的事。' })).toBeVisible()
  await expect(page.getByRole('button', { name: '记录第一条记忆' })).toBeVisible()
  await expect(page.getByText('邀请重要的人', { exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '最近记忆' })).toHaveCount(0)
  await expect(page.locator('.ui-empty-state')).toHaveCount(0)
})

test('compact home fits the viewport and has no blocking accessibility violations', async ({ page }) => {
  await seedSession(page)
  await routeHome(page)
  await page.setViewportSize({ width: 393, height: 852 })
  await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' })
  await page.goto('/home')
  await expect(page.getByRole('heading', { name: '最近记忆' })).toBeVisible()
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze()
  const blocking = results.violations.filter((violation) => violation.impact === 'serious' || violation.impact === 'critical')
  expect(blocking, blocking.map((violation) => `${violation.id}: ${violation.help}`).join('\n')).toEqual([])
})
