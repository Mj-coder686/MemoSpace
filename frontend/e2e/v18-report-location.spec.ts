import { expect, test, type Page } from '@playwright/test'

const baseURL = process.env.MEMOSPACE_E2E_BASE_URL || 'http://localhost:3000'
const currentUser = { id: 11, username: 'viewer', nickname: '查看者', public_id: '100000000011' }

async function installMock(page: Page) {
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
    else if (path === '/users/me/appearance') body = { backgroundColor:'#f5f2ec',backgroundBrightness:100,backgroundOverlay:0 }
    else if (path === '/map') body = []
    else if (path === '/spaces') body = []
    else if (path === '/memories/9') body = { id:9,creator_id:22,creator_nickname:'被查看者',title:'公开测试记忆',content:'需要被举报核查的内容',memory_type:'TEXT',visibility:'PUBLIC',occurred_at:'2026-09-20T12:00:00',spaces:[],media:[],comments:[],reactions:[] }
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
