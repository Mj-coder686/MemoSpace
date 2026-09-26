import { expect, test } from '@playwright/test'

test('shared controls provide tactile press feedback without layout animation', async ({ page }) => {
  await page.goto('/__design-system')
  const button = page.getByRole('button', { name: '保存记忆', exact: true }).first()
  await button.scrollIntoViewIfNeeded()
  const box = await button.boundingBox()
  expect(box).not.toBeNull()
  await page.mouse.move(box!.x + box!.width / 2, box!.y + box!.height / 2)
  await page.mouse.down()
  await page.waitForTimeout(40)
  expect(await button.evaluate((element) => getComputedStyle(element).transform)).not.toBe('none')
  await page.mouse.up()
})

test('coarse pointers receive 44px small-control targets', async ({ browser }) => {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true })
  const page = await context.newPage()
  await page.goto('/__design-system')
  const retry = page.getByRole('button', { name: '重试', exact: true })
  await expect(retry).toBeVisible()
  expect(await retry.evaluate((element) => Number.parseFloat(getComputedStyle(element).minHeight))).toBeGreaterThanOrEqual(44)
  await page.getByRole('button', { name: '打开确认框', exact: true }).click()
  const close = page.getByRole('button', { name: '关闭', exact: true })
  expect(await close.evaluate((element) => Number.parseFloat(getComputedStyle(element).height))).toBeGreaterThanOrEqual(44)
  await context.close()
})

test('browser surfaces and slowed overlay motion keep their state legible', async ({ page, context }) => {
  await page.goto('/__design-system')
  const rendering = await page.evaluate(() => ({
    smoothing: getComputedStyle(document.body).webkitFontSmoothing,
    headingWrap: getComputedStyle(document.querySelector('h1')!).textWrap,
    paragraphWrap: getComputedStyle(document.querySelector('.ds-body')!).textWrap,
  }))
  expect(rendering.smoothing).toBe('antialiased')
  expect(rendering.headingWrap).toBe('balance')
  expect(rendering.paragraphWrap).toBe('pretty')

  const outline = await page.evaluate(() => {
    const image = document.createElement('img')
    image.alt = '轮廓检查'
    image.src = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw=='
    document.body.append(image)
    return getComputedStyle(image).outlineStyle
  })
  expect(outline).toBe('solid')

  const session = await context.newCDPSession(page)
  await session.send('Animation.setPlaybackRate', { playbackRate: 0.1 })
  await page.getByRole('button', { name: '打开确认框', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '删除这条记忆？' })
  await expect(dialog).toBeVisible()
  await expect(page.getByRole('button', { name: '关闭', exact: true })).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden({ timeout: 5_000 })
})
