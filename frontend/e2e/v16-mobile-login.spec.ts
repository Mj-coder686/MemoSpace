import { expect, test } from '@playwright/test'

test.use({ viewport: { width: 390, height: 844 } })

test('登录页不再预填或展示演示账号', async ({ page }) => {
  await page.goto('/login')

  await expect(page.getByLabel('用户名')).toHaveValue('')
  await expect(page.getByLabel('密码')).toHaveValue('')
  await expect(page.getByText(/demo\s*\/\s*Memo123!/i)).toHaveCount(0)
})

test('旧演示账号会退出且不影响真实账号迁移', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('memospace_token', 'legacy-demo-token')
    localStorage.setItem('memospace_user', JSON.stringify({ id: 1, username: 'demo', nickname: '拾光者' }))
  })

  await page.goto('/')

  await expect(page).toHaveURL(/\/login$/)
  await expect.poll(() => page.evaluate(() => localStorage.getItem('memospace_token'))).toBeNull()
})

test('登录页不再要求填写局域网服务器地址', async ({ page }) => {
  await page.goto('/login')

  await expect(page.getByText('手机 APK 连接服务器设置')).toHaveCount(0)
  await expect(page.getByText('首次试用时，填入运行 Docker 的电脑地址')).toHaveCount(0)
  await expect(page.getByPlaceholder('http://192.168.x.x:18081')).toHaveCount(0)
})
