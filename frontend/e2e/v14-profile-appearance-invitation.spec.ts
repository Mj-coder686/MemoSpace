import { expect, test, type BrowserContext, type Page } from '@playwright/test'

const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVR42mP8z8Dwn4GBgYGJAQoAHgQCAULPzWQAAAAASUVORK5CYII=', 'base64')
const suffix = () => `${Date.now().toString(36)}${Math.random().toString(36).slice(2,7)}`

type Account = { page:Page;context:BrowserContext;id:number;publicId:string;nickname:string }

async function register(browser:any, username:string, nickname:string):Promise<Account>{
  const context=await browser.newContext();const page=await context.newPage()
  await page.goto('/login');await page.getByRole('link',{name:'创建账号'}).click();await page.getByLabel('怎么称呼你').fill(nickname);await page.getByLabel('用户名').fill(username);await page.getByLabel('密码').fill('MemoV14!2026');await page.getByRole('button',{name:'开始记录'}).click();await expect(page).toHaveURL(/\/home/)
  const user=await page.evaluate(()=>JSON.parse(localStorage.getItem('memospace_user')||'{}'))
  await expect(page.locator('.realtime-button')).toHaveClass(/connected/)
  return{page,context,id:Number(user.id),publicId:String(user.publicId||user.public_id),nickname}
}

async function api(page:Page, method:string, path:string, body?:unknown){
  return page.evaluate(async({method,path,body})=>{const token=localStorage.getItem('memospace_token');const response=await fetch(path,{method,headers:{Authorization:`Bearer ${token}`,...(body===undefined?{}:{'Content-Type':'application/json'})},body:body===undefined?undefined:JSON.stringify(body)});let data:any=null;try{data=await response.json()}catch{}return{status:response.status,data}}, {method,path,body})
}

test('relationship delivery, avatar, account background, shared-space background and important dates',async({browser},testInfo)=>{
  const run=suffix();const contexts:BrowserContext[]=[];const errors:string[]=[]
  try{
    const a=await register(browser,`v14a_${run}`,`拾光甲-${run.slice(-4)}`);contexts.push(a.context)
    const b=await register(browser,`v14b_${run}`,`拾光乙-${run.slice(-4)}`);contexts.push(b.context)
    const c=await register(browser,`v14c_${run}`,`拾光丙-${run.slice(-4)}`);contexts.push(c.context)
    for(const account of[a,b,c])account.page.on('console',message=>{if(message.type()==='error')errors.push(`${account.nickname}: ${message.text()}`)})

    await a.page.goto('/friends');await a.page.getByLabel('12 位 Memo ID').fill(b.publicId);await a.page.getByRole('button',{name:'查找',exact:true}).click();await a.page.getByRole('button',{name:'申请好友'}).click()
    await expect(b.page.locator('.live-notice')).toContainText('收到好友申请');await b.page.locator('.live-notice').click();await expect(b.page).toHaveURL(/\/friends/)
    await b.page.getByRole('button',{name:'接受好友申请'}).click();await expect(b.page.getByText('已成为好友，现在可以开始聊天。')).toBeVisible()
    await expect(a.page.locator('.live-notice')).toContainText('好友申请已通过');await a.page.locator('.live-notice').click();await expect(a.page.getByRole('heading',{name:b.nickname,exact:true})).toBeVisible()

    const card=a.page.locator('.friend-card').filter({hasText:b.nickname});await card.getByRole('button',{name:'申请关系'}).click();await a.page.getByLabel('关系分类').selectOption({index:1});await a.page.getByRole('button',{name:'发送关系申请'}).click()
    await expect(b.page.locator('.live-notice')).toContainText('收到关系申请');await b.page.locator('.live-notice').click();await expect(b.page).toHaveURL(/\/notifications/);await expect(b.page.getByRole('heading',{name:/邀请你绑定为/})).toBeVisible();await b.page.getByRole('button',{name:'接受',exact:true}).click();await expect(b.page.getByText(/共同空间已经准备好了|已加入现有共同空间/)).toBeVisible()

    await a.page.goto('/settings');await a.page.locator('.profile-avatar-picker input[type=file]').setInputFiles({name:'avatar.png',mimeType:'image/png',buffer:PNG});await a.page.getByLabel('昵称').fill(`${a.nickname}-新头像`);await a.page.getByRole('button',{name:'保存个人资料'}).click();await expect(a.page.getByText('个人资料和头像已保存。')).toBeVisible();await expect(a.page.locator('.avatar-button img')).toBeVisible()

    const backgroundPicker=a.page.locator('.appearance-actions input[type=file]');await backgroundPicker.setInputFiles({name:'account-background.png',mimeType:'image/png',buffer:PNG});await a.page.getByLabel(/背景颜色/).evaluate((element:any)=>{element.value='#6a5d66';element.dispatchEvent(new Event('input',{bubbles:true}))});await a.page.getByRole('button',{name:'保存全站背景'}).click();await expect(a.page.getByText('全站背景已经保存，并会跟随这个账号。')).toBeVisible();expect(await a.page.evaluate(()=>getComputedStyle(document.documentElement).getPropertyValue('--user-background').trim())).toBe('#6a5d66');expect(await a.page.evaluate(()=>getComputedStyle(document.documentElement).getPropertyValue('--user-bg-image').trim())).not.toBe('none')
    await a.page.screenshot({path:testInfo.outputPath('account-appearance.png'),fullPage:true})

    const relationships=await api(a.page,'GET','/api/relationships');expect(relationships.status).toBe(200);const relationship=relationships.data[0];const spaceId=Number(relationship.space_id)
    await a.page.goto(`/space/${spaceId}`);await a.page.getByRole('button',{name:/自定义空间/}).click();await a.page.getByLabel('空间名称').fill('我们的暖色拾光');const spacePicker=a.page.locator('.space-bg-actions input[type=file]');await spacePicker.setInputFiles({name:'space-background.png',mimeType:'image/png',buffer:PNG});await a.page.getByRole('button',{name:'自动调节亮度'}).click();await a.page.getByRole('button',{name:'应用到共享空间'}).click();await expect(a.page.locator('.space-hero')).toHaveClass(/has-background/);await expect(a.page.locator('.space-hero h1')).toHaveText('我们的暖色拾光')
    await a.page.getByRole('button',{name:'添加纪念日'}).click();await a.page.getByLabel('名称').fill('第一次一起测试');await a.page.getByLabel('日期').fill('2026-08-28');await a.page.getByRole('button',{name:'保存纪念日'}).click();await expect(a.page.getByText('第一次一起测试')).toBeVisible();await a.page.getByTitle('创建年度提醒').click();await expect(a.page).toHaveURL(/\/reminders/);await expect(a.page.getByLabel('提醒标题')).toHaveValue('第一次一起测试');await expect(a.page.getByLabel('重复周期')).toHaveValue('YEARLY')

    await b.page.goto(`/space/${spaceId}`);await expect(b.page.locator('.space-hero')).toHaveClass(/has-background/);await expect(b.page.locator('.space-hero h1')).toHaveText('我们的暖色拾光');await b.page.screenshot({path:testInfo.outputPath('shared-space-member.png'),fullPage:true})
    const space=await api(a.page,'GET',`/api/spaces/${spaceId}`);const fileId=Number(space.data.background_file_id);expect(fileId).toBeGreaterThan(0);expect((await api(c.page,'GET',`/api/files/${fileId}/content`)).status).toBe(403)

    const now=new Date();const occurred=`${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}T12:30:00`;await api(a.page,'POST','/api/memories',{title:'日历里的真实记录',memoryType:'TEXT',occurredAt:occurred,visibility:'PRIVATE'});await a.page.goto(`/calendar?date=${occurred.slice(0,10)}`);await expect(a.page.getByText('日历里的真实记录')).toBeVisible()

    expect(errors.filter(item=>!item.includes('favicon')&&!item.includes('拾光丙-')&&!item.includes('status of 403'))).toEqual([])
  }finally{await Promise.all(contexts.map(context=>context.close()))}
})
