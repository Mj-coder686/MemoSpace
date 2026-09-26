import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

const seedSession = async (page: Page) => {
  await page.addInitScript(() => {
    localStorage.setItem('memospace_token', 'social-design-test')
    localStorage.setItem('memospace_user', JSON.stringify({ id: 7, username: 'memory_friend', nickname: '阿遥', publicId: '10000007' }))
  })
}

const routeSocial = async (page: Page) => {
  const relationshipBodies: unknown[] = []
  const reminderBodies: unknown[] = []
  const reminderActions: string[] = []
  let invitationPending = true
  await page.route('**/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (!path.startsWith('/api/')) return route.fallback()
    if (path === '/api/friends') return route.fulfill({ json: [{ friendship_id: 41, friend_id: 8, public_id: '10000008', username: 'xiaolan', nickname: '小岚', avatar: '', bio: '一起认真生活。', remark_name: '', allow_direct_reminders: true, mute_chat: false, created_at: '2026-08-20T00:00:00Z' }] })
    if (path === '/api/friends/requests') return route.fulfill({ json: [{ id: 91, direction: 'INCOMING', status: 'PENDING', message: '很高兴认识你。', created_at: '2026-09-20T10:00:00Z', sender_id: 9, sender_public_id: '10000009', sender_nickname: '阿澄', sender_avatar: '' }] })
    if (path === '/api/relationship-categories') return route.fulfill({ json: [{ id: 1, name: '恋人', icon: 'heart', is_visible: true }] })
    if (path === '/api/users/search') return route.fulfill({ json: [{ id: 10, public_id: '10000010', username: 'lin', nickname: '林一', avatar: '' }] })
    if (path === '/api/relationships/invitations' && request.method() === 'POST') { relationshipBodies.push(request.postDataJSON()); return route.fulfill({ json: { id: 101 } }) }
    if (path === '/api/relationships/invitations' && request.method() === 'GET') return route.fulfill({ json: invitationPending ? [{ id: 101, receiver_id: 7, sender_id: 8, sender_nickname: '小岚', sender_avatar: '', category_name: '死党', status: 'PENDING', message: '一起收藏我们的故事。' }] : [] })
    if (path === '/api/relationships/invitations/101/accept') { invitationPending = false; return route.fulfill({ json: { reusedSpace: false } }) }
    if (path === '/api/relationships') return route.fulfill({ json: [{ relationship_id: 61, other_nickname: '小岚', relationship_type: 'BESTIE' }] })
    if (path === '/api/reminders' && request.method() === 'GET') return route.fulfill({ json: [
      { id: 501, creator_id: 8, related_user_id: 8, title: '一起看展', note: '周末下午两点见。', reminder_kind: 'PLAN', schedule_type: 'ONCE', remind_at: '2026-10-12T14:00:00', next_trigger_at: '2026-10-12T14:00:00', status: 'ACTIVE', acceptance_status: 'PENDING', creator_nickname: '小岚' },
      { id: 502, creator_id: 7, title: '给植物浇水', reminder_kind: 'TASK', schedule_type: 'WEEKLY', remind_at: '2026-10-14T09:00:00', next_trigger_at: '2026-10-14T09:00:00', status: 'ACTIVE', acceptance_status: 'ACCEPTED' }
    ] })
    if (path === '/api/reminders' && request.method() === 'POST') { reminderBodies.push(request.postDataJSON()); return route.fulfill({ json: { id: 503 } }) }
    if (path.startsWith('/api/reminders/') && request.method() === 'POST') { reminderActions.push(path); return route.fulfill({ json: {} }) }
    if (path.startsWith('/api/reminders/') && request.method() === 'DELETE') { reminderActions.push(`DELETE ${path}`); return route.fulfill({ json: {} }) }
    if (path === '/api/friends/8/messages') return route.fulfill({ json: { items: [{ id: 301, senderId: 8, receiverId: 7, clientMessageId: 'history-1', content: '今晚记得看月亮。', sentAt: '2026-09-24T18:00:00Z', readAt: '2026-09-24T18:01:00Z' }], hasMore: false } })
    if (path === '/api/friends/8/messages/read') return route.fulfill({ json: {} })
    if (path === '/api/notifications') return route.fulfill({ json: [{ id: 701, notification_type: 'REMINDER_DUE', title: '该给植物浇水了', content: '你在每周提醒里安排了这件事。', created_at: new Date().toISOString(), is_read: false }] })
    if (path === '/api/notifications/read') return route.fulfill({ json: {} })
    if (path === '/api/reports/mine') return route.fulfill({ json: [] })
    if (path.includes('/users/me/appearance')) return route.fulfill({ json: {} })
    return route.fulfill({ json: {} })
  })
  return { relationshipBodies, reminderBodies, reminderActions }
}

test('friends keep contact, relationship, and destructive data boundaries explicit', async ({ page }) => {
  await seedSession(page)
  const capture = await routeSocial(page)
  await page.goto('/friends')

  await expect(page.getByRole('heading', { name: '好友中心' })).toBeVisible()
  await expect(page.getByText('好友是聊天和日常提醒的基础；关系绑定仍然是独立的共同记忆关系。')).toBeVisible()
  await expect(page.getByText('10000007')).toBeVisible()
  await expect(page.getByRole('heading', { name: '小岚' })).toBeVisible()

  await page.getByRole('button', { name: '申请关系' }).click()
  const relationDialog = page.getByRole('dialog', { name: '和 小岚 建立关系' })
  await expect(relationDialog).toContainText('好友和关系是两层独立连接')
  await relationDialog.getByRole('button', { name: '发送关系申请' }).click()
  expect(capture.relationshipBodies).toEqual([{ receiverId: 8, categoryId: 1, message: '想和你建立一段共同记录的关系。' }])

  await page.getByRole('button', { name: '设置小岚' }).click()
  await page.getByRole('button', { name: '删除好友' }).click()
  const removeDialog = page.getByRole('dialog', { name: '删除这位好友？' })
  await expect(removeDialog).toContainText('已有关系绑定和共同空间不会因此删除')
})

test('chat preserves history and marks unsent realtime messages for retry', async ({ page }) => {
  await seedSession(page)
  await routeSocial(page)
  await page.setViewportSize({ width: 393, height: 852 })
  await page.goto('/chat/8')

  await expect(page.getByRole('heading', { name: '小岚' })).toBeVisible()
  await expect(page.getByText('今晚记得看月亮。')).toBeVisible()
  await expect(page.getByText('实时连接正在恢复')).toBeVisible()
  await page.getByLabel('聊天消息').fill('这条消息先保留。')
  await page.getByRole('button', { name: '发送消息' }).click()
  await expect(page.getByText('这条消息先保留。')).toBeVisible()
  await expect(page.getByRole('button', { name: '重新发送' })).toBeVisible()
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze()
  expect(results.violations.filter(item => item.impact === 'serious' || item.impact === 'critical')).toEqual([])
})

test('notifications prioritize invitations and complete the relationship response', async ({ page }) => {
  await seedSession(page)
  await routeSocial(page)
  await page.goto('/notifications')

  await expect(page.getByRole('heading', { name: '消息与通知' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '等待你的回应' })).toBeVisible()
  await expect(page.getByText('小岚 邀请你绑定为「死党」')).toBeVisible()
  await page.getByRole('button', { name: '接受', exact: true }).click()
  await expect(page.getByText('共同空间已经准备好了')).toBeVisible()
  await expect(page.getByText('该给植物浇水了')).toBeVisible()
})

test('reminders support accepting, assigning, and safe deletion', async ({ page }) => {
  await seedSession(page)
  const capture = await routeSocial(page)
  await page.goto('/reminders')

  await expect(page.getByRole('heading', { name: '重要提醒' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '一起看展' })).toBeVisible()
  await page.getByRole('button', { name: '接受', exact: true }).click()
  expect(capture.reminderActions).toContain('/api/reminders/501/accept')

  await page.getByRole('button', { name: '提醒好友' }).click()
  const createDialog = page.getByRole('dialog', { name: '新建提醒' })
  await createDialog.getByLabel('提醒标题').fill('记得带伞')
  await createDialog.getByLabel('把提醒发给谁').selectOption('8')
  await createDialog.getByRole('button', { name: '创建提醒' }).click()
  await expect.poll(() => capture.reminderBodies.length).toBe(1)
  expect(capture.reminderBodies[0]).toMatchObject({ title: '记得带伞', recipientUserId: 8 })

  await page.getByRole('button', { name: '删除提醒给植物浇水' }).click()
  const removeDialog = page.getByRole('dialog', { name: '删除这条提醒？' })
  await expect(removeDialog).toContainText('这个操作无法撤销')
  expect(capture.reminderActions.some(item => item.startsWith('DELETE'))).toBe(false)
  await removeDialog.getByRole('button', { name: '确认删除' }).click()
  expect(capture.reminderActions).toContain('DELETE /api/reminders/502')

  await page.setViewportSize({ width: 393, height: 852 })
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})
