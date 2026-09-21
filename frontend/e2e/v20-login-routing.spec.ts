import { expect, test } from '@playwright/test'

test.use({ viewport: { width: 390, height: 844 } })

test('管理员入口使用可靠跳转且普通入口给出明确引导', async ({ page }) => {
  await page.route('**/api/auth/login', route => route.fulfill({
    status: 403,
    contentType: 'application/json',
    body: JSON.stringify({ message: '管理员账号请从管理员入口登录' }),
  }))

  await page.goto('/login')
  await page.getByLabel('用户名').fill('memo_admin_01')
  await page.getByLabel('密码').fill('not-a-real-password')
  await page.getByRole('button', { name: '进入拾光空间' }).click()
  await expect(page.getByRole('alert')).toContainText('请点击上方“管理员”入口登录')

  const adminEntry = page.getByRole('link', { name: '管理员', exact: true })
  await expect(adminEntry).toHaveAttribute('href', '/admin/login')
  await adminEntry.click()
  await expect(page).toHaveURL(/\/admin\/login$/)
  await expect(page.getByRole('heading', { name: '管理员登录' })).toBeVisible()
})

test('网络异常时显示可理解的中文提示', async ({ page }) => {
  await page.route('**/api/auth/login', route => route.abort('failed'))
  await page.goto('/login')
  await page.getByLabel('用户名').fill('temporary_user')
  await page.getByLabel('密码').fill('temporary-password')
  await page.getByRole('button', { name: '进入拾光空间' }).click()
  await expect(page.getByRole('alert')).toContainText('暂时无法连接服务器')
})
