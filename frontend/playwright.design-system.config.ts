import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  testMatch: ['design-system-accessibility.spec.ts', 'design-polish.spec.ts', 'design-shell.spec.ts', 'design-identity.spec.ts', 'design-home.spec.ts', 'design-memory.spec.ts', 'design-memory-companions.spec.ts', 'design-relationships.spec.ts', 'design-social.spec.ts', 'design-community.spec.ts', 'design-settings.spec.ts', 'design-admin.spec.ts'],
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  reporter: [['list']],
  use: {
    baseURL: 'http://127.0.0.1:4173',
    browserName: 'chromium',
    channel: 'chrome',
    headless: true,
    locale: 'zh-CN',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 4173',
    url: 'http://127.0.0.1:4173/__design-system',
    reuseExistingServer: true,
    timeout: 30_000,
  },
})
