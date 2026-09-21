import { expect, test, type Page } from '@playwright/test'

const baseURL = process.env.MEMOSPACE_E2E_BASE_URL || 'http://localhost:3000'
const currentUser = { id: 11, username: 'viewer', nickname: '查看者', public_id: '100000000011' }

async function installMock(page: Page, onCreate?: (payload: any) => void) {
  await page.addInitScript(user => {
    localStorage.setItem('memospace_demo_session_cleanup_v1', '1')
    localStorage.setItem('memospace_token', 'v18-token')
    localStorage.setItem('memospace_user', JSON.stringify(user))
  }, currentUser)
  await page.routeWebSocket('**/ws/chat', () => {})
  await page.route(/^https?:\/\/[^/]+\/api\//, async route => {
    const request = route.request(); const path = new URL(request.url()).pathname.replace(/^\/api/, '')
    let body: unknown = {}
    if (path === '/users/me') body = currentUser
    else if (path === '/notifications') body = []
    else if (path === '/relationships/invitations') body = []
    else if (path === '/reports/mine') body = [
      { id:1,target_type:'MEMORY',target_id:9,reason_category:'ILLEGAL',description:'请管理员核查',status:'PENDING',created_at:'2026-09-20T12:00:00' },
      { id:2,target_type:'COMMENT',target_id:18,reason_category:'HARASSMENT',status:'RESOLVED',resolution_action:'DELETE+WARNING',created_at:'2026-09-19T12:00:00',reviewed_at:'2026-09-20T09:00:00' },
    ]
    else if (path === '/users/me/appearance') body = { backgroundColor:'#f5f2ec',backgroundBrightness:100,backgroundOverlay:0 }
    else if (path === '/map') body = []
    else if (path === '/spaces') body = [{ id:3,name:'共同测试空间',space_type:'RELATIONSHIP',status:'ACTIVE' }]
    else if (path === '/memories/9') body = { id:9,creator_id:22,creator_nickname:'被查看者',title:'公开测试记忆',content:'需要被举报核查的内容',memory_type:'TEXT',visibility:'PUBLIC',occurred_at:'2026-09-20T12:00:00',spaces:[],media:[],comments:[],reactions:[] }
    else if (path === '/memories/77') body = { id:77,creator_id:11,creator_nickname:'查看者',title:'定位测试 Memory',content:'',memory_type:'LOCATION',visibility:'PRIVATE',occurred_at:'2026-09-20T12:00:00',location:'31.230416, 121.473701',spaces:[],media:[],comments:[],reactions:[] }
    else if (path === '/memories' && request.method() === 'POST') { const payload=request.postDataJSON();onCreate?.(payload);body={id:77} }
    else if (path === '/reports' && request.method() === 'POST') body = { id:1,status:'PENDING' }
    await route.fulfill({ status:200,contentType:'application/json',body:JSON.stringify(body) })
  })
}

test('用户主动授权后可读取当前位置', async ({ page, context }) => {
  await context.grantPermissions(['geolocation'], { origin: new URL(baseURL).origin })
  await context.setGeolocation({ latitude:31.230416,longitude:121.473701,accuracy:18 })
  await installMock(page)
  await page.goto('/map')
  await page.getByRole('button',{name:'定位到我'}).click()
  await expect(page.getByText(/31\.230416, 121\.473701/)).toBeVisible()
  await expect(page.getByTitle('我现在的位置')).toBeVisible()
})

test('可举报他人的公开记忆并获得提交反馈', async ({ page }) => {
  await installMock(page)
  await page.goto('/memory/9')
  await page.getByRole('button',{name:'举报这条记忆'}).click()
  await expect(page.getByRole('heading',{name:'举报这条记忆'})).toBeVisible()
  await page.getByLabel('补充说明').fill('这是一条端到端举报验证')
  await page.getByRole('button',{name:'提交举报'}).click()
  await expect(page.getByText('举报已提交，管理员会在后台核查。')).toBeVisible()
})

test('消息中心可查看举报处理进度', async ({ page }) => {
  await installMock(page)
  await page.goto('/notifications')
  await page.getByRole('button',{name:/我的举报/}).click()
  await expect(page.getByText('等待核查')).toBeVisible()
  await expect(page.getByText('内容已删除 · 已警告')).toBeVisible()
})

test('创建地点 Memory 会提交用户主动获取的坐标', async ({ page, context }) => {
  await context.grantPermissions(['geolocation'], { origin: new URL(baseURL).origin })
  await context.setGeolocation({ latitude:31.230416,longitude:121.473701,accuracy:18 })
  let created: any
  await installMock(page, payload => { created = payload })
  await page.goto('/map')
  await page.getByRole('button',{name:'记录此刻'}).click()
  await page.getByRole('button',{name:'地点',exact:true}).click()
  await page.getByLabel('标题').fill('定位测试 Memory')
  await page.getByRole('button',{name:'获取当前位置'}).click()
  await page.getByRole('button',{name:'保存这段记忆'}).click()
  await expect(page).toHaveURL(/\/memory\/77$/)
  expect(created.latitude).toBeCloseTo(31.230416,6)
  expect(created.longitude).toBeCloseTo(121.473701,6)
  expect(created.memoryType).toBe('LOCATION')
})
