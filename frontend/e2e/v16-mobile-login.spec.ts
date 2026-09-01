import { expect, test } from '@playwright/test'

test.use({ viewport: { width: 390, height: 844 } })

test('手机尺寸下可展开服务器连接设置', async ({ page }) => {
  await page.goto('/login')

  const connectionEntry = page.getByRole('button', { name: '手机 APK 连接服务器设置' })
  await expect(connectionEntry).toBeVisible()
  await connectionEntry.click()

  await expect(page.getByRole('textbox', { name: '服务器地址' })).toBeVisible()
  await expect(page.getByText('首次试用时，填入运行 Docker 的电脑地址')).toBeVisible()
})
