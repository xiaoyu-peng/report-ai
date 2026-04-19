import { test, expect, Page } from '@playwright/test'

/**
 * ReportAI 回归测试：覆盖赛题五模块 + 本轮新增特性。
 * 对齐第一站的"步骤日志 + 断言"风格，方便评委回溯。
 *
 * 运行：npx playwright test
 */

const ADMIN_USER = 'admin'
const ADMIN_PASS = 'admin123'

async function login(page: Page) {
  await page.goto('/login')
  await page.waitForLoadState('networkidle')
  // 兼容两种输入框选择器（第一站项目用 placeholder 匹配，当前项目可能同名）
  const username = page.locator('input[placeholder*="用户名"]').first()
  const password = page.locator('input[placeholder*="密码"]').first()
  await username.fill(ADMIN_USER)
  await password.fill(ADMIN_PASS)
  // 登录按钮可能是"登 录"或"登录"
  const loginBtn = page.locator('button.login-btn, button:has-text("登录"), button:has-text("登 录")').first()
  await loginBtn.click()
  await page.waitForURL('**/dashboard**', { timeout: 15_000 })
}

test.describe('赛题回归 — 模块一 知识库构建与管理', () => {
  test('T1.1 知识库列表 + 分类筛选 + 新建对话框', async ({ page }) => {
    await login(page)

    await page.goto('/knowledge')
    await page.waitForLoadState('networkidle')

    // 分类 tab 应该存在（赛题 1.2：政策法规 / 行业报告 / 历史报告 / 媒体资讯）
    await expect(page.locator('.category-tabs')).toBeVisible({ timeout: 10_000 })
    for (const cat of ['政策法规', '行业报告', '历史报告', '媒体资讯']) {
      // 用 el-radio-button 作为更精确的锚，避免多重 DOM 节点触发 strict mode
      await expect(
        page.locator('.category-tabs .el-radio-button__inner', { hasText: cat })
      ).toBeVisible()
    }

    // 切到"行业报告"分类，验证筛选下沉到后端 —— 抓所有请求，判断有没有带 category=industry
    const bases: string[] = []
    page.on('request', r => {
      if (r.url().includes('/v1/knowledge/bases')) bases.push(r.url())
    })
    await page.locator('.category-tabs .el-radio-button__inner', { hasText: '行业报告' }).click()
    await page.waitForTimeout(1500) // 给 fetchList 时间发起请求
    const hitCategory = bases.some(u => u.includes('category=industry'))
    if (!hitCategory) {
      console.log('[请求抓包]', bases)
    }
    expect(hitCategory, '切换分类后前端应发起 ?category=industry 请求').toBe(true)
  })
})

test.describe('赛题回归 — 模块二/三 知识检索 + AI 生成', () => {
  test('T2.1 workspace 基础 UI 就位（模板/深度/高级检索）', async ({ page }) => {
    await login(page)
    await page.goto('/workspace')
    await page.waitForLoadState('networkidle')

    // 标题/主题/重点输入框
    await expect(page.locator('input[placeholder*="输入报告标题"], input[placeholder*="报告标题"]').first()).toBeVisible()
    await expect(page.locator('textarea[placeholder*="核心主题"], textarea[placeholder*="报告的核心主题"]').first()).toBeVisible()

    // 生成深度三档（P1 #17）
    await expect(page.locator('text=生成深度')).toBeVisible()
    await expect(page.locator('.el-radio-button:has-text("简洁")')).toBeVisible()
    await expect(page.locator('.el-radio-button:has-text("标准")')).toBeVisible()
    await expect(page.locator('.el-radio-button:has-text("深度")')).toBeVisible()
  })

  test('T2.2 选 KB 后高级检索面板（include/exclude）可用（自建 fixture）', async ({ page, request }) => {
    await login(page)
    const token = await page.evaluate(() => localStorage.getItem('token'))

    // 若库中没有任何 KB 就直接 POST 一个 test fixture，不依赖 UI 建库的前置
    const listResp = await request.get('/api/v1/knowledge/bases', {
      headers: { Authorization: `Bearer ${token}` }
    })
    const listJson = await listResp.json().catch(() => ({}))
    const existing = (listJson?.data?.records || listJson?.data || []) as any[]
    if (!Array.isArray(existing) || existing.length === 0) {
      await request.post('/api/v1/knowledge/bases', {
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        data: { name: 'E2E 测试知识库', description: 'playwright auto-created', category: 'industry' }
      })
    }

    await page.goto('/workspace')
    await page.waitForLoadState('networkidle')

    const kbSelect = page.locator('.el-form-item:has-text("选择知识库") .el-select').first()
    await expect(kbSelect).toBeVisible({ timeout: 10_000 })
    await kbSelect.click()
    const firstOption = page.locator('.el-select-dropdown__item').first()
    await expect(firstOption).toBeVisible({ timeout: 5_000 })
    await firstOption.click()

    // 选完 KB 后"检索条件"面板出现（赛题 2.3）
    await expect(page.locator('.el-form-item:has-text("检索条件")')).toBeVisible({ timeout: 5_000 })

    // + 添加 include 关键词
    await page.locator('button:has-text("+ 添加")').first().click()
    const inc = page.locator('input').nth(2) // 简单按顺序取
    // 实际上 include input 是 condition-row 里的
    const incInput = page.locator('.condition-row:has-text("补充关键词") input').last()
    await incInput.fill('AI')
    await incInput.press('Enter')
    await expect(page.locator('.condition-row:has-text("补充关键词") .el-tag:has-text("AI")')).toBeVisible()
  })
})

test.describe('赛题回归 — 模块四 改写 / 续写', () => {
  test('T4.1 报告库 + 版本对比 tab（自建 draft fixture）', async ({ page, request }) => {
    await login(page)
    const token = await page.evaluate(() => localStorage.getItem('token'))

    // 拿一个 KB 当 foreign key
    const kbListResp = await request.get('/api/v1/knowledge/bases', {
      headers: { Authorization: `Bearer ${token}` }
    })
    const kbListJson = await kbListResp.json().catch(() => ({}))
    const kbs = (kbListJson?.data?.records || kbListJson?.data || []) as any[]
    if (kbs.length === 0) {
      const created = await request.post('/api/v1/knowledge/bases', {
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        data: { name: 'E2E fixture KB', category: 'other' }
      })
      const cj = await created.json().catch(() => ({}))
      kbs.push(cj.data)
    }
    const kbId = kbs[0]?.id
    expect(kbId, '必须有一个 KB').toBeTruthy()

    // 创建一个 draft 报告（不跑 LLM，只建 meta）
    const createResp = await request.post('/api/v1/reports', {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: { title: 'E2E 测试报告', topic: '测试主题', kbId, keyPoints: ['要点 A', '要点 B'] }
    })
    expect(createResp.ok(), '创建报告应成功').toBe(true)
    const createJson = await createResp.json()
    const reportId = createJson?.data?.id
    expect(reportId).toBeTruthy()

    // 打开详情页
    await page.goto(`/reports/${reportId}`)
    await page.waitForLoadState('networkidle')

    // "版本对比" tab 应存在
    const diffTab = page.locator('.el-tabs__item:has-text("版本对比")')
    await expect(diffTab).toBeVisible({ timeout: 10_000 })
    await diffTab.click()

    // diff 控制区存在，就算暂时无版本（新建 draft 还没生成）也要有骨架
    await expect(page.locator('.diff-controls')).toBeVisible()
    await expect(page.locator('text=对比版本')).toBeVisible()
  })
})

test.describe('赛题回归 — 系统健康度 + 关键路由', () => {
  test('T5.1 各主要路由无控制台报错', async ({ page }) => {
    const errors: string[] = []
    page.on('pageerror', err => errors.push(`pageerror: ${err.message}`))
    page.on('console', msg => {
      if (msg.type() === 'error') {
        const t = msg.text()
        // Element Plus 和 Vite 的 dev-only warning 不算；favicon / 网络 abort 不算
        if (!t.includes('favicon') && !t.includes('DevTools') && !t.includes('[vue-i18n]')) {
          errors.push(`[console.error] ${t}`)
        }
      }
    })

    await login(page)
    for (const path of ['/dashboard', '/knowledge', '/workspace', '/templates', '/reports']) {
      await page.goto(path)
      await page.waitForLoadState('networkidle')
      await page.waitForTimeout(500)
    }

    if (errors.length > 0) {
      console.log('[console errors]')
      for (const e of errors) console.log('  ', e)
    }
    expect(errors, `发现 ${errors.length} 条控制台错误：\n${errors.join('\n')}`).toHaveLength(0)
  })

  test('T5.2 导出 Word 端点存在（不是 404）', async ({ page, request }) => {
    await login(page)
    // 用业务 token 直接打接口验证路径。登录时 token 已经写入 localStorage。
    const token = await page.evaluate(() => localStorage.getItem('token'))
    expect(token, 'token 必须在 localStorage').not.toBeNull()

    // 用 fake ID 123 也没关系，重要的是路由存在 → 应返回 400/404/500 业务错，而不是 404 路由不存在
    const resp = await request.get('/api/v1/reports/123/export/docx', {
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false
    })
    // 不接受 404（路由不存在）。200/400/500 都说明路由在、只是业务层有问题。
    expect(resp.status(), `导出端点返回 ${resp.status()}`).not.toBe(404)
  })

  test('T5.3 质量检查端点存在（赛题 3.4）', async ({ page, request }) => {
    await login(page)
    const token = await page.evaluate(() => localStorage.getItem('token'))
    const resp = await request.get('/api/v1/reports/123/quality/check', {
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false
    })
    expect(resp.status(), `质量检查端点返回 ${resp.status()}`).not.toBe(404)
  })
})
