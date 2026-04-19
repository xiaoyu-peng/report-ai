import { defineConfig, devices } from '@playwright/test'

/**
 * ReportAI 端到端回归：覆盖赛题五模块 + 本轮新增的 A0-A5 / B6-B9 / P1 / P2 特性。
 *
 * 运行前置：docker compose up（前端 3001，后端 8081）或 npm run dev（5173）。
 * 默认目标是 Docker 部署端口 3001 —— 真实演示环境。
 */
export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  timeout: 120_000,
  expect: { timeout: 10_000 },
  use: {
    // 默认打 vite dev（5173）—— docker 前端容器 20h 前构建，不含本轮新功能
    // 真想测 docker 部署：TEST_BASE_URL=http://localhost:3001 npx playwright test
    baseURL: process.env.TEST_BASE_URL || 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    headless: true,
    viewport: { width: 1440, height: 900 }
  },
  projects: [
    {
      // 用本机系统 Chrome 避免 playwright 内置浏览器下载失败（常见在墙后 / 公司网络）
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        channel: 'chrome'
      }
    }
  ]
})
