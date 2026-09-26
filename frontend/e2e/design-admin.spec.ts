import { expect, test, type Page } from '@playwright/test'

const seedAdmin = async (page: Page) => {
  await page.addInitScript(() => {
    localStorage.setItem('memospace_admin_token', 'admin-design-test')
    localStorage.setItem('memospace_admin_user', JSON.stringify({ id: 1, username: 'safety_admin', nickname: '安全管理员' }))
  })
}

const routeAdmin = async (page: Page) => {
  const resolutions: unknown[] = []
  const statuses: unknown[] = []
  await page.route('**/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (!path.startsWith('/api/')) return route.fallback()
    if (path === '/api/admin/me') return route.fulfill({ json: { id: 1, username: 'safety_admin', nickname: '安全管理员' } })
    if (path === '/api/admin/audit') return route.fulfill({ json: [{ id: 1, action_type: 'RESOLVE_REPORT', target_nickname: '小岚', detail: '确认违规并警告', created_at: '2026-09-24T10:00:00' }] })
    if (path === '/api/admin/reports') return route.fulfill({ json: { total: 1, items: [{ id: 91, target_type: 'MEMORY', reason_category: 'ILLEGAL', status: 'PENDING', created_at: '2026-09-24T09:00:00', reported_nickname: '小岚', reported_username: 'xiaolan', reported_user_id: 8, violation_count: 2, account_status: 'ACTIVE' }] } })
    if (path === '/api/admin/reports/91' && request.method() === 'GET') return route.fulfill({ json: { id: 91, target_type: 'MEMORY', reason_category: 'ILLEGAL', status: 'PENDING', created_at: '2026-09-24T09:00:00', reporter_nickname: '阿遥', reported_nickname: '小岚', reported_username: 'xiaolan', violation_count: 2, description: '疑似违法信息', target: { title: '被举报的公开记忆', content: '只展示举报关联的证据内容。' }, media: [] } })
    if (path === '/api/admin/reports/91/resolve') { resolutions.push(request.postDataJSON()); return route.fulfill({ json: {} }) }
    if (path === '/api/admin/users') return route.fulfill({ json: { total: 1, items: [{ id: 8, public_id: '100000000008', username: 'xiaolan', nickname: '小岚', is_admin: false, account_status: 'ACTIVE', violation_count: 2, created_at: '2026-08-01T00:00:00' }] } })
    if (path === '/api/admin/users/8/status') { statuses.push(request.postDataJSON()); return route.fulfill({ json: {} }) }
    return route.fulfill({ json: {} })
  })
  return { resolutions, statuses }
}

test('admin report handling is evidence-scoped and confirms the exact consequence', async ({ page }) => {
  await seedAdmin(page)
  const capture = await routeAdmin(page)
  await page.goto('/admin')

  await expect(page.getByRole('heading', { name: '社区安全与账号管理' })).toBeVisible()
  await expect(page.getByText('管理员不能浏览其他私人 Memory、共同空间、聊天或媒体')).toBeVisible()
  await expect(page.getByRole('link', { name: '返回网站' })).toHaveCount(0)
  await page.getByRole('button', { name: /违法或违禁/ }).click()
  const reportDialog = page.getByRole('dialog', { name: '违法或违禁举报详情' })
  await expect(reportDialog.getByText('只展示举报关联的证据内容。')).toBeVisible()
  await reportDialog.getByLabel('账号处罚').selectOption('MUTE_7_DAYS')
  await reportDialog.getByRole('button', { name: '确认违规并执行' }).click()
  const confirmDialog = page.getByRole('dialog', { name: '确认违规并执行处罚？' })
  await expect(confirmDialog).toContainText('删除被举报内容、禁言 7 天')
  await confirmDialog.getByRole('button', { name: '确认并记录' }).click()
  await expect.poll(() => capture.resolutions.length).toBe(1)
  expect(capture.resolutions[0]).toMatchObject({ decision: 'CONFIRM', removeContent: true, penalty: 'MUTE_7_DAYS' })
})

test('manual account penalties use an explicit object and consequence dialog', async ({ page }) => {
  await seedAdmin(page)
  const capture = await routeAdmin(page)
  await page.goto('/admin')
  await page.setViewportSize({ width: 393, height: 852 })
  await page.getByRole('button', { name: '用户目录' }).click()
  await page.getByRole('button', { name: '禁言', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '禁言 小岚 7 天？' })
  await expect(dialog).toContainText('仍可浏览，但 7 天内不能发布内容、评论或私聊')
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await dialog.getByRole('button', { name: '确认并记录' }).click()
  await expect.poll(() => capture.statuses.length).toBe(1)
  expect(capture.statuses[0]).toEqual({ action: 'MUTE_7_DAYS', note: '管理员从用户目录手动调整' })
})
