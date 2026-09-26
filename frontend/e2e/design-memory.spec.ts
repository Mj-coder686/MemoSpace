import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

const pixel = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64')

const memories = [
  {
    id: 11,
    title: '江边的晚风',
    content: '我们赶在日落前走到了江边，风里还有夏天的味道。',
    memory_type: 'PHOTO',
    visibility: 'RELATIONSHIP',
    occurred_at: '2026-08-24T18:20:00',
    location: '江边',
    creator_nickname: '阿遥',
    cover_file_id: 901,
    cover_mime_type: 'image/png',
    comment_count: 2,
  },
  {
    id: 12,
    title: '周末早餐',
    content: '很慢的一顿早餐。',
    memory_type: 'TEXT',
    visibility: 'PRIVATE',
    occurred_at: '2026-08-22T09:10:00',
    creator_nickname: '阿遥',
  },
]

const detail = {
  ...memories[0],
  creator_id: 7,
  media: [{ id: 1, file_id: 901, mime_type: 'image/png' }],
  spaces: [{ id: 2, name: '和小岚的共同空间' }],
  reactions: [{ reaction_type: '❤️', count: 3 }],
  comments: [{ id: 81, user_id: 8, nickname: '小岚', content: '我也记得那天的风。', created_at: '2026-08-24T20:00:00' }],
}

const seedSession = async (page: Page) => {
  await page.addInitScript(() => {
    localStorage.setItem('memospace_token', 'memory-design-test')
    localStorage.setItem('memospace_user', JSON.stringify({ id: 7, username: 'memory_friend', nickname: '阿遥', publicId: '10000007' }))
  })
}

const routeMemory = async (page: Page, options: { detailStatus?: number; commentFails?: boolean } = {}) => {
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (!url.pathname.startsWith('/api/')) return route.fallback()
    if (url.pathname === '/api/memories' && request.method() === 'GET') {
      const query = url.searchParams.get('q')?.trim()
      const result = query ? memories.filter((item) => `${item.title}${item.content}${item.location || ''}`.includes(query)) : memories
      return route.fulfill({ json: result })
    }
    if (url.pathname === '/api/memories/11' && request.method() === 'GET') {
      if (options.detailStatus) return route.fulfill({ status: options.detailStatus, json: { message: 'sensitive server detail must not leak' } })
      return route.fulfill({ json: detail })
    }
    if (url.pathname === '/api/memories/11/comments' && request.method() === 'POST') {
      if (options.commentFails) return route.fulfill({ status: 403, json: { message: '账号已禁言至 2026-09-30' } })
      return route.fulfill({ json: { id: 99 } })
    }
    if (url.pathname === '/api/files/901/content') return route.fulfill({ status: 200, contentType: 'image/png', body: pixel })
    if (url.pathname === '/api/spaces') return route.fulfill({ json: [] })
    if (url.pathname.includes('/users/me/appearance')) return route.fulfill({ json: {} })
    return route.fulfill({ json: [] })
  })
}

test('memory archive searches, filters, and opens a validated creation flow', async ({ page }) => {
  await seedSession(page)
  await routeMemory(page)
  await page.goto('/memories')

  await expect(page.getByRole('heading', { name: '我的记忆库' })).toBeVisible()
  await expect(page.getByText('2 条')).toBeVisible()
  await expect(page.getByRole('heading', { name: '江边的晚风' })).toBeVisible()

  await page.getByRole('searchbox', { name: '搜索记忆' }).fill('早餐')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(page).toHaveURL(/q=%E6%97%A9%E9%A4%90/)
  await expect(page.getByRole('heading', { name: '周末早餐' })).toHaveCount(1)
  await expect(page.getByRole('heading', { name: '周末早餐' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '江边的晚风' })).toHaveCount(0)
  await page.getByRole('button', { name: '清除搜索', exact: true }).last().click()
  await expect(page.getByText('2 条')).toBeVisible()

  await page.getByRole('button', { name: '文字', exact: true }).click()
  await expect(page.getByRole('heading', { name: '周末早餐' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '江边的晚风' })).toHaveCount(0)

  await page.locator('#main-content').getByRole('button', { name: '记录此刻', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '把这一刻留下来' })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByLabel('关系成员')).toBeDisabled()
  await dialog.getByRole('button', { name: '保存这段记忆' }).click()
  await expect(dialog.getByText('给这段记忆起个名字吧')).toBeVisible()
  await dialog.getByRole('button', { name: /照片/ }).click()
  await dialog.getByRole('button', { name: '保存这段记忆' }).click()
  await expect(dialog.getByText('照片记忆至少需要选择一张图片')).toBeVisible()
})

test('memory detail loads protected media and failed comments keep the draft', async ({ page }) => {
  await seedSession(page)
  await routeMemory(page, { commentFails: true })
  await page.goto('/memory/11')

  await expect(page.getByRole('heading', { name: '江边的晚风' })).toBeVisible()
  await expect(page.getByText('关系成员可见', { exact: true })).toBeVisible()
  await expect(page.getByText('仅所属共同空间的合法成员可见')).toBeVisible()
  await expect(page.getByRole('img', { name: '江边的晚风' })).toBeVisible()
  await expect(page.getByText('和小岚的共同空间')).toBeVisible()

  await page.getByRole('button', { name: '预览媒体：江边的晚风' }).click()
  await expect(page.getByRole('dialog', { name: '媒体预览' })).toBeVisible()
  await page.getByRole('dialog', { name: '媒体预览' }).getByRole('button', { name: '关闭' }).click()

  const composer = page.getByLabel('写下评论')
  await composer.fill('这句话在失败后也不能消失')
  await page.getByRole('button', { name: '发送评论' }).click()
  await expect(page.getByRole('alert')).toContainText('账号已禁言至 2026-09-30')
  await expect(composer).toHaveValue('这句话在失败后也不能消失')
})

test('forbidden memory does not expose server or content details', async ({ page }) => {
  await seedSession(page)
  await routeMemory(page, { detailStatus: 403 })
  await page.goto('/memory/11')

  await expect(page.getByRole('heading', { name: '无法访问这段记忆' })).toBeVisible()
  await expect(page.getByText('为保护隐私，这里不会显示更多内容信息。')).toBeVisible()
  await expect(page.getByText('sensitive server detail must not leak')).toHaveCount(0)
  await expect(page.getByText('江边的晚风')).toHaveCount(0)
})

test('compact memory archive and creation dialog fit the viewport without blocking accessibility issues', async ({ page }) => {
  await seedSession(page)
  await routeMemory(page)
  await page.setViewportSize({ width: 393, height: 852 })
  await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' })
  await page.goto('/memories')
  await expect(page.getByRole('heading', { name: '我的记忆库' })).toBeVisible()
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  await page.getByRole('button', { name: '记录此刻', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '把这一刻留下来' })
  await expect(dialog).toBeVisible()
  const box = await dialog.boundingBox()
  expect(box?.width).toBeGreaterThanOrEqual(392)
  expect(box?.height).toBeGreaterThanOrEqual(851)
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze()
  const blocking = results.violations.filter((violation) => violation.impact === 'serious' || violation.impact === 'critical')
  expect(blocking, blocking.map((violation) => `${violation.id}: ${violation.help}`).join('\n')).toEqual([])
})
