import { defineConfig } from '@playwright/test'

const baseURL = process.env.MEMOSPACE_E2E_BASE_URL || 'http://localhost:3000'

export default defineConfig({
  testDir: './e2e',
  outputDir: './test-results/acceptance-artifacts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 180_000,
  expect: { timeout: 15_000 },
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['json', { outputFile: 'test-results/acceptance-report.json' }],
  ],
  use: {
    baseURL,
    browserName: 'chromium',
    channel: 'chrome',
    headless: process.env.MEMOSPACE_E2E_HEADLESS !== '0',
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    viewport: { width: 1440, height: 1000 },
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
  },
})
