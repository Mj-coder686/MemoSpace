import { expect, test, type Page } from '@playwright/test'

const admin = { id:90,username:'memo_admin_01',nickname:'社区管理员一' }
const target = { id:22,public_id:'100000000022',username:'reported_user',nickname:'被举报用户',is_admin:false,account_status:'ACTIVE',violation_count:2,created_at:'2026-09-01T08:00:00' }

async function installAdminMock(page: Page, onResolve: (payload: any) => void) {
  await page.addInitScript(user => {
    localStorage.setItem('memospace_admin_token','v19-admin-token')
    localStorage.setItem('memospace_admin_user',JSON.stringify(user))
  }, admin)
  await page.route(/^https?:\/\/[^/]+\/api\//, async route => {
    const request=route.request();const path=new URL(request.url()).pathname.replace(/^\/api/,'')
    if(path==='/admin/reports/5/media/8'){
      await route.fulfill({status:200,contentType:'video/mp4',body:'mock-video-evidence'});return
    }
    let body:unknown={}
    if(path==='/admin/me')body=admin
    else if(path==='/admin/users')body={items:[target],total:1,page:1,size:20}
    else if(path==='/admin/audit')body=[]
    else if(path==='/admin/reports'&&request.method()==='GET')body={items:[{id:5,target_type:'MEMORY',target_id:9,reason_category:'VIOLENCE',description:'视频包含危险行为',status:'PENDING',created_at:'2026-09-20T10:00:00',reported_nickname:target.nickname,reported_username:target.username,reported_user_id:target.id,violation_count:2,account_status:'ACTIVE'}],total:1,page:1,size:20}
    else if(path==='/admin/reports/5'&&request.method()==='GET')body={id:5,target_type:'MEMORY',target_id:9,reason_category:'VIOLENCE',description:'视频包含危险行为',status:'PENDING',created_at:'2026-09-20T10:00:00',reporter_nickname:'举报用户',reported_nickname:target.nickname,reported_username:target.username,violation_count:2,target:{title:'危险视频',content:'请勿模仿'},media:[{file_id:8,mime_type:'video/mp4',original_name:'evidence.mp4',content_url:'/api/admin/reports/5/media/8'}]}
    else if(path==='/admin/reports/5/resolve'&&request.method()==='PUT'){onResolve(request.postDataJSON());body={id:5,status:'RESOLVED'}}
    await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(body)})
  })
}

test('管理员可核查视频证据并确认删除和禁言', async ({ page }) => {
  let resolved:any
  await installAdminMock(page,payload=>{resolved=payload})
  await page.goto('/admin')
  await expect(page.getByRole('heading',{name:'社区安全与账号管理'})).toBeVisible()
  await page.getByRole('button',{name:'查看详情'}).click()
  await expect(page.getByRole('heading',{name:'暴力危险举报详情'})).toBeVisible()
  await expect(page.locator('.evidence video')).toHaveCount(1)
  await page.getByLabel('账号处罚').selectOption('MUTE_7_DAYS')
  page.once('dialog',dialog=>dialog.accept())
  await page.getByRole('button',{name:'确认违规并执行'}).click()
  await expect(page.getByRole('status')).toContainText('举报处理结果已生效')
  expect(resolved).toMatchObject({decision:'CONFIRM',removeContent:true,penalty:'MUTE_7_DAYS'})
})

test('封号后的旧 Web 登录会退出并显示原因', async ({ page }) => {
  await page.addInitScript(() => {
    if (sessionStorage.getItem('v19-banned-seeded')) return
    sessionStorage.setItem('v19-banned-seeded','true')
    localStorage.setItem('memospace_token','banned-old-token')
    localStorage.setItem('memospace_user',JSON.stringify({id:33,username:'banned',nickname:'封号用户'}))
  })
  await page.route(/^https?:\/\/[^/]+\/api\//,async route=>{
    const path=new URL(route.request().url()).pathname
    if(path==='/api/home'){await route.fulfill({status:403,contentType:'application/json',body:JSON.stringify({message:'账号已被封禁，请联系管理员'})});return}
    await route.fulfill({status:200,contentType:'application/json',body:'[]'})
  })
  await page.goto('/home')
  await expect(page).toHaveURL(/\/login\?reason=banned$/)
  await expect(page.getByRole('alert')).toContainText('这个账号已被封禁')
  await expect.poll(()=>page.evaluate(()=>localStorage.getItem('memospace_token'))).toBeNull()
})
