import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

const feed = [
  { id: 801, creator_id: 8, title: '雨后散步', content: '路灯在水面上慢慢亮起来。', memory_type: 'TEXT', visibility: 'PUBLIC', occurred_at: '2026-09-24T19:30:00', creator_nickname: '小岚', comment_count: 2 },
  { id: 802, creator_id: 9, title: '旧书店下午', content: '找到一本写满批注的旧书。', memory_type: 'TEXT', visibility: 'PUBLIC', occurred_at: '2026-09-23T15:00:00', creator_nickname: '阿澄' },
]

const seedSession = async (page: Page) => {
  await page.addInitScript(() => {
    localStorage.setItem('memospace_token', 'community-design-test')
    localStorage.setItem('memospace_user', JSON.stringify({ id: 7, username: 'memory_friend', nickname: '阿遥', publicId: '10000007', bio: '认真收藏日常。', location: '武汉' }))
  })
}

const routeCommunity = async (page: Page) => {
  const feedScopes: string[] = []
  const followTargets: number[] = []
  await page.route('**/api/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    if (!url.pathname.startsWith('/api/')) return route.fallback()
    if (url.pathname === '/api/feed') {
      const scope = url.searchParams.get('scope') || 'latest'
      feedScopes.push(scope)
      return route.fulfill({ json: scope === 'following' ? [] : feed })
    }
    if (url.pathname === '/api/users/search') return route.fulfill({ json: [{ id: 8, public_id: '10000008', username: 'xiaolan', nickname: '小岚', avatar: '', bio: '一起认真生活。', location: '杭州', following: false }] })
    if (url.pathname === '/api/users/8') return route.fulfill({ json: { id: 8, public_id: '10000008', username: 'xiaolan', nickname: '小岚', avatar: '', bio: '一起认真生活。', location: '杭州', followers: 12, following: 7, public_memories: 1, is_following: false } })
    if (url.pathname === '/api/users/8/follow') { followTargets.push(8); return route.fulfill({ json: { following: true } }) }
    if (url.pathname === '/api/memories') return route.fulfill({ json: [{ id: 701, creator_id: 7, title: '我的清晨', content: '第一束光落在桌面。', memory_type: 'TEXT', visibility: 'PRIVATE', occurred_at: '2026-09-25T07:00:00', creator_nickname: '阿遥' }] })
    if (url.pathname.includes('/users/me/appearance')) return route.fulfill({ json: {} })
    return route.fulfill({ json: [] })
  })
  return { feedScopes, followTargets }
}

test('public feed keeps people search separate from memories and preserves relationship consent', async ({ page }) => {
  await seedSession(page)
  const capture = await routeCommunity(page)
  await page.goto('/explore')

  await expect(page.getByRole('heading', { name: '动态', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '雨后散步' })).toBeVisible()
  await page.getByRole('searchbox', { name: '寻找一个人' }).fill('小岚')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(page.getByRole('heading', { name: '寻找「小岚」' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '雨后散步' })).toBeVisible()
  await expect(page.getByText('关注只影响公共动态；建立关系需要对方另行接受。')).toBeVisible()

  await page.getByRole('button', { name: '关注', exact: true }).click()
  expect(capture.followTargets).toEqual([8])
  await page.getByRole('button', { name: '选择关系分类' }).click()
  await expect(page).toHaveURL(/\/relationships\?inviteUser=8&inviteName=/)
})

test('following feed has a truthful empty state without exposing private content', async ({ page }) => {
  await seedSession(page)
  const capture = await routeCommunity(page)
  await page.goto('/explore')
  await page.getByRole('tab', { name: '我关注的' }).click()

  await expect(page.getByRole('heading', { name: '关注的人还没有公开新记忆' })).toBeVisible()
  await expect(page.getByText('关注不会自动建立好友或关系')).toBeVisible()
  expect(capture.feedScopes).toContain('following')
})

test('other profile only renders public feed items and keeps follow and relationship actions distinct', async ({ page }) => {
  await seedSession(page)
  const capture = await routeCommunity(page)
  await page.setViewportSize({ width: 393, height: 852 })
  await page.goto('/user/8')

  await expect(page.getByRole('heading', { name: '小岚' })).toBeVisible()
  await expect(page.getByText('10000008')).toBeVisible()
  await expect(page.getByRole('heading', { name: '雨后散步' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '旧书店下午' })).toHaveCount(0)
  await expect(page.getByText('不暗示任何私人记忆数量')).toBeVisible()
  await page.getByRole('button', { name: '关注 TA' }).click()
  expect(capture.followTargets).toEqual([8])
  await expect(page.getByRole('button', { name: '取消关注' })).toBeVisible()
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze()
  expect(results.violations.filter(item => item.impact === 'serious' || item.impact === 'critical')).toEqual([])
})

test('own profile exposes private workspace entry points without a follow action', async ({ page }) => {
  await seedSession(page)
  await routeCommunity(page)
  await page.goto('/user/7')

  await expect(page.getByRole('heading', { name: '阿遥' })).toBeVisible()
  await expect(page.getByRole('button', { name: '编辑资料' })).toBeVisible()
  await expect(page.getByRole('button', { name: /记忆库/ })).toBeVisible()
  await expect(page.getByRole('button', { name: /相册/ })).toBeVisible()
  await expect(page.getByRole('button', { name: /空间/ })).toBeVisible()
  await expect(page.getByRole('button', { name: '关注 TA' })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '我的清晨' })).toBeVisible()
})
