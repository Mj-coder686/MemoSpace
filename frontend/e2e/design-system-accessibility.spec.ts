import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

const assertNoSeriousViolations = async (page: Parameters<typeof AxeBuilder>[0]['page']) => {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()
  const blocking = results.violations.filter((violation) => violation.impact === 'serious' || violation.impact === 'critical')
  expect(blocking, blocking.map((violation) => `${violation.id}: ${violation.help}`).join('\n')).toEqual([])
}

for (const scenario of [
  { name: 'expanded light', width: 1440, height: 1000, colorScheme: 'light' as const },
  { name: 'compact dark', width: 320, height: 800, colorScheme: 'dark' as const },
]) {
  test(`${scenario.name} component matrix has no blocking accessibility violations`, async ({ browser }) => {
    const context = await browser.newContext({
      viewport: { width: scenario.width, height: scenario.height },
      colorScheme: scenario.colorScheme,
      reducedMotion: 'reduce',
    })
    const page = await context.newPage()
    await page.goto('/__design-system')
    await expect(page.locator('.ui-progress')).toBeVisible()
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await assertNoSeriousViolations(page)
    await context.close()
  })
}

test('dialog traps focus and remains accessible', async ({ page }) => {
  await page.goto('/__design-system')
  await page.getByRole('button', { name: '打开确认框', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '删除这条记忆？' })
  await expect(dialog).toBeVisible()
  await page.waitForTimeout(350)
  await assertNoSeriousViolations(page)
  await page.getByRole('button', { name: '确认删除', exact: true }).focus()
  await page.keyboard.press('Tab')
  await expect(page.getByRole('button', { name: '关闭', exact: true })).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()
})
