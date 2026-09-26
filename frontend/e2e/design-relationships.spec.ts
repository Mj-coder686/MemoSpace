import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

const baseCategories = [
  { id: 1, category_key: 'LOVER', name: '恋人', icon: 'heart', category_type: 'SYSTEM', is_visible: true, relationship_count: 1, primary_color: '#995a66', background_color: '#f5e6e9' },
  { id: 2, category_key: 'FAMILY', name: '家人', icon: 'home', category_type: 'SYSTEM', is_visible: true, relationship_count: 2, primary_color: '#716784', background_color: '#ece8f2' },
  { id: 3, category_key: 'CUSTOM_TRAVEL', name: '旅行搭子', icon: 'camera', category_type: 'CUSTOM', is_visible: false, relationship_count: 1, primary_color: '#60766a', background_color: '#e8f0eb' },
]

const seedSession = async (page: Page) => {
  await page.addInitScript(() => {
    localStorage.setItem('memospace_token', 'relationship-design-test')
    localStorage.setItem('memospace_user', JSON.stringify({ id: 7, username: 'memory_friend', nickname: '阿遥', publicId: '10000007' }))
  })
}

const routeRelationships = async (page: Page) => {
  let categories = structuredClone(baseCategories)
  let relationships = [{
    id: 41,
    status: 'ACTIVE',
    established_at: '2026-08-20T00:00:00Z',
    user_id: 8,
    username: 'xiaolan',
    nickname: '小岚',
    avatar: '',
    space_id: 71,
    space_name: '阿遥与小岚的拾光空间',
    categories: [categories[0]],
  }, {
    id: 39,
    status: 'ARCHIVED',
    user_id: 9,
    username: 'old_friend',
    nickname: '旧友',
    avatar: '',
    space_id: 69,
    space_name: '那年夏天',
    categories: [categories[1]],
  }]
  const invitationBodies: unknown[] = []
  const visibilityBodies: unknown[] = []
  const relationshipCategoryBodies: unknown[] = []
  const archivedRelationshipIds: number[] = []
  const anniversaryBodies: unknown[] = []
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (!url.pathname.startsWith('/api/')) return route.fallback()
    if (url.pathname === '/api/relationship-categories' && request.method() === 'GET') return route.fulfill({ json: categories })
    if (url.pathname === '/api/relationships' && request.method() === 'GET') return route.fulfill({ json: relationships })
    const relationshipCategories = url.pathname.match(/^\/api\/relationships\/(\d+)\/categories$/)
    if (relationshipCategories && request.method() === 'PUT') {
      const body = request.postDataJSON() as { categoryIds: number[] }
      relationshipCategoryBodies.push(body)
      const relationship = relationships.find(item => item.id === Number(relationshipCategories[1]))!
      relationship.categories = body.categoryIds.map(id => categories.find(category => category.id === id)!).filter(Boolean)
      return route.fulfill({ json: relationship })
    }
    const relationshipArchive = url.pathname.match(/^\/api\/relationships\/(\d+)$/)
    if (relationshipArchive && request.method() === 'DELETE') {
      const id = Number(relationshipArchive[1])
      archivedRelationshipIds.push(id)
      relationships = relationships.map(item => item.id === id ? { ...item, status: 'ARCHIVED' } : item)
      return route.fulfill({ json: { id, status: 'ARCHIVED' } })
    }
    if (url.pathname === '/api/spaces' && request.method() === 'GET') return route.fulfill({ json: [
      { id: 11, space_type: 'PERSONAL', name: '阿遥的私人记忆库', status: 'ACTIVE', preset_name: '纸页与时光', primary_color: '#7a6658', background_color: '#eee8df', memoryCount: 18, photoCount: 9, placeCount: 4 },
      { id: 71, space_type: 'RELATIONSHIP', relationship_id: 41, name: '阿遥与小岚的拾光空间', status: 'ACTIVE', preset_name: '柔和暮色', primary_color: '#995a66', background_color: '#f5e6e9', memoryCount: 12, photoCount: 7, placeCount: 3 },
      { id: 69, space_type: 'RELATIONSHIP', relationship_id: 39, name: '那年夏天', status: 'ARCHIVED', preset_name: '旧日来信', primary_color: '#716784', background_color: '#ece8f2', memoryCount: 5, photoCount: 2, placeCount: 1 },
    ] })
    if (url.pathname === '/api/spaces/71' && request.method() === 'GET') return route.fulfill({ json: {
      id: 71,
      space_type: 'RELATIONSHIP',
      relationship_id: 41,
      name: '阿遥与小岚的拾光空间',
      status: 'ACTIVE',
      created_at: '2026-08-20T00:00:00Z',
      primary_color: '#995a66',
      background_color: '#f5e6e9',
      text_color: '#372e31',
      memoryCount: 12,
      photoCount: 7,
      placeCount: 3,
      members: [{ id: 7, nickname: '阿遥', avatar: '' }, { id: 8, nickname: '小岚', avatar: '' }],
      anniversaries: [{ id: 301, title: '第一次见面', anniversary_date: '2026-08-20', repeat_yearly: true }],
    } })
    if (url.pathname === '/api/spaces/71/timeline') return route.fulfill({ json: [{ id: 501, title: '江边的晚风', content: '我们赶在日落前走到了江边。', memory_type: 'TEXT', occurred_at: '2026-08-24T18:30:00Z' }] })
    if (url.pathname === '/api/spaces/71/messages' && request.method() === 'GET') return route.fulfill({ json: [{ id: 601, nickname: '小岚', content: '下次还去这里。', created_at: '2026-08-25T10:00:00Z' }] })
    if (url.pathname === '/api/spaces/71/events') return route.fulfill({ json: [{ id: 701, name: '成都周末', start_at: '2026-08-24T00:00:00Z', location: '成都' }] })
    if (url.pathname === '/api/spaces/71/anniversaries' && request.method() === 'POST') {
      anniversaryBodies.push(request.postDataJSON())
      return route.fulfill({ json: { id: 302 } })
    }
    if (url.pathname === '/api/relationship-categories/1' && request.method() === 'GET') {
      return route.fulfill({ json: {
        ...categories[0],
        people: [{
          relationship_id: 41,
          relationship_type: 'LOVER',
          relationship_status: 'ACTIVE',
          established_at: '2026-08-20T00:00:00Z',
          user_id: 8,
          username: 'xiaolan',
          nickname: '小岚',
          avatar: '',
          bio: '一起把普通日子认真记下来。',
          location: '成都',
          space_id: 71,
          space_name: '阿遥与小岚的拾光空间',
          space_status: 'ACTIVE',
          memory_count: 12,
        }],
      } })
    }
    if (url.pathname === '/api/relationship-categories/reorder' && request.method() === 'PUT') {
      const ids = (request.postDataJSON() as { categoryIds: number[] }).categoryIds
      categories = ids.map((id) => categories.find((item) => item.id === id)!).filter(Boolean)
      return route.fulfill({ json: categories })
    }
    const visibility = url.pathname.match(/^\/api\/relationship-categories\/(\d+)\/visibility$/)
    if (visibility && request.method() === 'PUT') {
      const target = categories.find((item) => item.id === Number(visibility[1]))!
      const body = request.postDataJSON() as { visible: boolean }
      visibilityBodies.push(body)
      target.is_visible = Boolean(body.visible)
      return route.fulfill({ json: target })
    }
    if (url.pathname === '/api/relationship-categories' && request.method() === 'POST') {
      const body = request.postDataJSON() as { name: string; icon: string }
      const created = { id: 4, category_key: 'CUSTOM_NEW', category_type: 'CUSTOM', is_visible: true, relationship_count: 0, ...body }
      categories.push(created as typeof categories[number])
      return route.fulfill({ json: created })
    }
    if (url.pathname === '/api/users/search') return route.fulfill({ json: [{ id: 8, username: 'xiaolan', nickname: '小岚', location: '成都' }] })
    if (url.pathname === '/api/relationships/invitations' && request.method() === 'POST') {
      invitationBodies.push(request.postDataJSON())
      return route.fulfill({ json: { id: 91, status: 'PENDING', categoryName: '恋人', willReuseSpace: false } })
    }
    if (url.pathname === '/api/notifications') return route.fulfill({ json: [] })
    if (url.pathname.includes('/users/me/appearance')) return route.fulfill({ json: {
      background_color: '#f4efe7',
      background_file_id: null,
      background_brightness: 100,
      background_overlay: 0,
    } })
    return route.fulfill({ json: [] })
  })
  return { invitationBodies, visibilityBodies, relationshipCategoryBodies, archivedRelationshipIds, anniversaryBodies }
}

test('relationship categories explain boundaries and hiding never implies deletion', async ({ page }) => {
  await seedSession(page)
  await routeRelationships(page)
  await page.goto('/relationships')

  await expect(page.getByRole('heading', { name: '关系分类' })).toBeVisible()
  await expect(page.getByText('好友用于联系')).toBeVisible()
  await expect(page.getByText('关系用于共同生活记录')).toBeVisible()
  await expect(page.getByRole('link', { name: '打开恋人分类' })).toBeVisible()

  await page.getByRole('button', { name: '隐藏' }).first().click()
  await expect(page.getByText('「恋人」已隐藏；关系、共同空间和记忆均未删除。')).toBeVisible()
  await expect(page.getByRole('link', { name: '打开恋人分类' })).toHaveCount(0)

  await page.getByText('已隐藏分类', { exact: true }).click()
  await expect(page.getByText('隐藏不等于删除')).toBeVisible()
  await expect(page.getByRole('button', { name: '恢复显示' }).first()).toBeVisible()
})

test('relationship invitation follows search, category, message, and confirmation order', async ({ page }) => {
  await seedSession(page)
  const capture = await routeRelationships(page)
  await page.goto('/relationships')
  await page.getByRole('button', { name: '绑定一段关系' }).click()

  const dialog = page.getByRole('dialog', { name: '绑定一段关系' })
  await expect(dialog).toBeVisible()
  await dialog.getByLabel('昵称或用户名').fill('小岚')
  await dialog.getByRole('button', { name: '搜索', exact: true }).click()
  await dialog.getByRole('option', { name: /小岚/ }).click()
  await dialog.getByLabel('恋人').check()
  await dialog.getByLabel('邀请留言').fill('一起保存我们走过的地方。')
  await dialog.getByRole('button', { name: '发送绑定邀请' }).click()

  await expect(dialog).toHaveCount(0)
  await expect(page.getByText(/对方接受后会创建或关联双方唯一的共同空间/)).toBeVisible()
  expect(capture.invitationBodies).toEqual([{ receiverId: 8, categoryId: 1, message: '一起保存我们走过的地方。' }])
})

test('custom category creation stays separate from relationship invitation', async ({ page }) => {
  await seedSession(page)
  await routeRelationships(page)
  await page.goto('/relationships')
  await page.getByRole('button', { name: '创建自定义分类' }).click()
  const dialog = page.getByRole('dialog', { name: '创建自定义分类' })
  await dialog.getByLabel('分类名称').fill('一起看展')
  await dialog.getByRole('button', { name: '同好' }).click()
  await dialog.getByRole('button', { name: '创建分类' }).click()
  await expect(dialog).toHaveCount(0)
  await expect(page.getByRole('link', { name: '打开一起看展分类' })).toBeVisible()
})

test('category detail exposes one shared space and hiding only changes category visibility', async ({ page }) => {
  await seedSession(page)
  const capture = await routeRelationships(page)
  await page.goto('/relationships/category/1')

  await expect(page.getByRole('heading', { name: '恋人', exact: true })).toBeVisible()
  await expect(page.getByText('每一段关系只对应双方唯一的共同空间')).toBeVisible()
  await expect(page.getByRole('link', { name: '查看小岚的主页' })).toHaveAttribute('href', '/user/8')
  const sharedSpace = page.getByRole('link').filter({ hasText: '双方唯一的共同空间' })
  await expect(sharedSpace).toHaveCount(1)
  await expect(sharedSpace).toHaveAttribute('href', '/space/71')
  await expect(sharedSpace).toContainText('12 段共同记忆')

  await page.getByRole('button', { name: '隐藏分类' }).click()
  await expect(page.getByText('隐藏只改变入口是否显示，不会解除关系，也不会删除共同空间或任何记忆。')).toBeVisible()
  await expect(sharedSpace).toBeVisible()
  expect(capture.visibilityBodies).toEqual([{ visible: false }])
})

test('relationship ledger keeps category tags separate from the one shared space and confirms archiving', async ({ page }) => {
  await seedSession(page)
  const capture = await routeRelationships(page)
  await page.goto('/relationships/manage')

  await expect(page.getByRole('heading', { name: '关系管理' })).toBeVisible()
  await expect(page.getByText('标签只增加分类入口，不会复制关系、共同空间或 Memory。')).toBeVisible()
  const oneSpace = page.getByRole('link').filter({ hasText: '双方唯一的共同空间' })
  await expect(oneSpace).toHaveCount(1)
  await expect(oneSpace).toHaveAttribute('href', '/space/71')

  await page.getByLabel('家人').check()
  await page.getByRole('button', { name: '保存分类' }).click()
  await expect(page.getByText('共同空间仍然是原来的那一个。')).toBeVisible()
  expect(capture.relationshipCategoryBodies).toEqual([{ categoryIds: [1, 2] }])

  await page.getByRole('button', { name: '解除关系' }).click()
  const dialog = page.getByRole('dialog', { name: '解除这段关系？' })
  await expect(dialog).toContainText('不会删除任何历史记忆')
  await dialog.getByRole('button', { name: '确认解除并封存' }).click()
  await expect(page.getByText('共同空间和历史记忆已封存保留。')).toBeVisible()
  expect(capture.archivedRelationshipIds).toEqual([41])
})

test('spaces index separates private, shared, and archived memory boundaries', async ({ page }) => {
  await seedSession(page)
  await routeRelationships(page)
  await page.goto('/spaces')

  await expect(page.getByRole('heading', { name: '记忆空间' })).toBeVisible()
  await expect(page.getByText('默认私密，只有你可以进入')).toBeVisible()
  await expect(page.getByRole('link', { name: '进入阿遥的私人记忆库' })).toHaveAttribute('href', '/space/11')
  await expect(page.getByText('每一段已建立的关系只有一个共同空间，不会因分类标签增加而重复。')).toBeVisible()
  await expect(page.getByRole('link', { name: '进入阿遥与小岚的拾光空间' })).toHaveAttribute('href', '/space/71')

  await page.getByText('已封存空间', { exact: true }).click()
  await expect(page.getByRole('link', { name: /那年夏天/ })).toHaveAttribute('href', '/space/69')
  await expect(page.getByText('5 段历史记忆被保留')).toBeVisible()
})

test('shared space detail keeps timeline, people, dates, messages, and archive consequence clear', async ({ page }) => {
  await seedSession(page)
  const capture = await routeRelationships(page)
  await page.goto('/space/71')

  await expect(page.getByRole('heading', { name: '阿遥与小岚的拾光空间' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '我们的时间轴' })).toBeVisible()
  await expect(page.getByRole('link', { name: /江边的晚风/ })).toHaveAttribute('href', '/memory/501')
  await expect(page.getByRole('heading', { name: '空间成员' })).toBeVisible()
  await expect(page.getByText('第一次见面')).toBeVisible()
  await expect(page.getByText('下次还去这里。')).toBeVisible()
  await expect(page.getByRole('link', { name: /成都周末/ })).toHaveAttribute('href', '/event/701')

  await page.getByRole('button', { name: '添加纪念日' }).click()
  const anniversaryDialog = page.getByRole('dialog', { name: '添加纪念日' })
  await anniversaryDialog.getByLabel('名称').fill('一起旅行')
  await anniversaryDialog.getByLabel('日期').fill('2026-09-20')
  await anniversaryDialog.getByRole('button', { name: '保存纪念日' }).click()
  expect(capture.anniversaryBodies).toEqual([{ title: '一起旅行', date: '2026-09-20', repeatYearly: true }])

  await page.getByRole('button', { name: '解除关系并封存空间' }).click()
  const archiveDialog = page.getByRole('dialog', { name: '解除关系并封存空间？' })
  await expect(archiveDialog).toContainText('已有空间、图片、留言和时间轴都会保留')
})

test('compact relationship flow fits the viewport and has no blocking accessibility violations', async ({ page }) => {
  await seedSession(page)
  await routeRelationships(page)
  await page.setViewportSize({ width: 393, height: 852 })
  await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' })
  await page.goto('/relationships')
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.getByRole('button', { name: '绑定一段关系' }).click()
  await expect(page.getByRole('dialog', { name: '绑定一段关系' })).toBeVisible()
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze()
  const blocking = results.violations.filter((violation) => violation.impact === 'serious' || violation.impact === 'critical')
  expect(blocking, blocking.map((violation) => `${violation.id}: ${violation.help}`).join('\n')).toEqual([])
})

test('system dark mode keeps custom light backgrounds and space themes readable', async ({ page }) => {
  await seedSession(page)
  await routeRelationships(page)
  await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' })

  for (const path of ['/spaces', '/relationships/category/1', '/space/71']) {
    await page.goto(path)
    await expect(page.locator('#main-content')).toBeVisible()
    await expect.poll(() => page.evaluate(() => getComputedStyle(document.documentElement).colorScheme)).toContain('dark')
    await expect.poll(() => page.evaluate(() => getComputedStyle(document.querySelector('.app-shell')!, '::after').backgroundImage)).not.toBe('none')

    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze()
    const contrast = results.violations.filter((violation) => violation.id === 'color-contrast')
    expect(contrast, contrast.flatMap((violation) => violation.nodes.map((node) => node.failureSummary || node.html)).join('\n')).toEqual([])
  }
})
