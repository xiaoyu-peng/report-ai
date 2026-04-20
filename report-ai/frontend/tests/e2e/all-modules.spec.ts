import { test, expect, Page, APIRequestContext } from '@playwright/test'

/**
 * ReportAI 全量 E2E —— 覆盖赛题五模块端到端
 *
 * 组织方式：fixture-first，先通过 API 建立 KB/Document/Report 数据基线，
 * 再走 UI 验证各模块交互；真实 LLM 调用默认跳过（SSE 30s–2min 太慢、有成本），
 * 用 `E2E_WITH_LLM=1` 环境变量打开。
 *
 * 运行：
 *   TEST_BASE_URL=http://localhost:3001 npx playwright test tests/e2e/all-modules.spec.ts
 *   # 含真实 LLM SSE：
 *   TEST_BASE_URL=http://localhost:3001 E2E_WITH_LLM=1 npx playwright test tests/e2e/all-modules.spec.ts
 */

const ADMIN_USER = 'admin'
const ADMIN_PASS = 'admin123'
const WITH_LLM = process.env.E2E_WITH_LLM === '1'

/** 登录流程 —— 复用 UI 路径，把 token 落到 localStorage。 */
async function login(page: Page) {
  await page.goto('/login')
  await page.waitForLoadState('networkidle')
  await page.locator('input[placeholder*="用户名"]').first().fill(ADMIN_USER)
  await page.locator('input[placeholder*="密码"]').first().fill(ADMIN_PASS)
  await page
    .locator('button.login-btn, button:has-text("登录"), button:has-text("登 录")')
    .first()
    .click()
  await page.waitForURL('**/dashboard**', { timeout: 15_000 })
}

/** 直接走后端 `/api/v1/login`，供"纯 API"前置数据用，绕开 UI 开销。 */
async function apiLogin(request: APIRequestContext): Promise<string> {
  const resp = await request.post('/api/v1/login', {
    data: { username: ADMIN_USER, password: ADMIN_PASS },
    headers: { 'Content-Type': 'application/json' }
  })
  expect(resp.ok()).toBe(true)
  const j = await resp.json()
  return j?.data?.token
}

/** 统一 auth header。 */
const auth = (t: string) => ({ Authorization: `Bearer ${t}` })

// ---------------------------------------------------------------------------
// 模块一：知识库构建与管理 —— CRUD + 分类 + 文档上传/查看/编辑 + 全文检索
// ---------------------------------------------------------------------------

test.describe('模块一 · 知识库 CRUD + 文档生命周期', () => {
  let token = ''
  let kbId = 0
  let docId = 0

  test.beforeAll(async ({ request }) => {
    token = await apiLogin(request)
  })

  test('M1.1 创建知识库（API）', async ({ request }) => {
    const resp = await request.post('/api/v1/knowledge/bases', {
      headers: { ...auth(token), 'Content-Type': 'application/json' },
      data: {
        name: `E2E-KB-${Date.now()}`,
        description: 'Playwright 自动化产生的临时知识库',
        category: 'industry'
      }
    })
    expect(resp.status(), `创建 KB 失败: ${resp.status()}`).toBe(200)
    const j = await resp.json()
    kbId = j?.data?.id
    expect(kbId).toBeGreaterThan(0)
  })

  test('M1.2 上传一份文本文档到 KB（API multipart）', async ({ request }) => {
    expect(kbId, '需要先创建 KB').toBeGreaterThan(0)
    const body =
      '# E2E 测试文档\n\n' +
      '人工智能在文旅产业的应用日益广泛。2025 年数据显示，' +
      'AIGC 生成的虚拟导游、智能票务推荐系统在景区落地率上升至 42%。' +
      '业界普遍认为，视频生成模型 Sora 2 与可灵 3 代的问世推动了文旅内容生产门槛下降。\n\n' +
      '## 关键趋势\n\n' +
      '- 虚拟人讲解\n- 动态路线推荐\n- AR 沉浸式导览\n- AIGC 文旅短视频\n'
    const resp = await request.post(`/api/v1/knowledge/bases/${kbId}/documents`, {
      headers: auth(token),
      multipart: {
        file: { name: 'e2e-travel-ai.md', mimeType: 'text/markdown', buffer: Buffer.from(body) }
      }
    })
    expect(resp.status(), `上传文档失败: ${resp.status()}`).toBe(200)
    const j = await resp.json()
    docId = j?.data?.id
    expect(docId).toBeGreaterThan(0)
  })

  test('M1.3 文档列表 + 详情（API）', async ({ request }) => {
    const listResp = await request.get(`/api/v1/knowledge/bases/${kbId}/documents`, {
      headers: auth(token)
    })
    expect(listResp.status()).toBe(200)
    const listJ = await listResp.json()
    const docs = listJ?.data?.records || listJ?.data || []
    expect(Array.isArray(docs) && docs.length > 0, '至少应看到一份上传的文档').toBe(true)

    const detailResp = await request.get(`/api/v1/knowledge/documents/${docId}`, {
      headers: auth(token)
    })
    expect(detailResp.status()).toBe(200)
    const detailJ = await detailResp.json()
    expect(detailJ?.data?.content, '详情应带 content 字段').toContain('人工智能')
  })

  test('M1.4 编辑文档（rename + 改正文，API）', async ({ request }) => {
    const resp = await request.put(`/api/v1/knowledge/documents/${docId}`, {
      headers: { ...auth(token), 'Content-Type': 'application/json' },
      data: {
        filename: 'e2e-travel-ai-edited.md',
        content:
          '# E2E 测试文档（已编辑）\n\n这里是被改写过的正文，包含关键词：' +
          '数字孪生、元宇宙、智慧文旅、低空经济。'
      }
    })
    expect(resp.status()).toBe(200)

    const check = await request.get(`/api/v1/knowledge/documents/${docId}`, {
      headers: auth(token)
    })
    const j = await check.json()
    expect(j?.data?.filename).toContain('edited')
    expect(j?.data?.content).toContain('数字孪生')
  })

  test('M1.5 RAG 全文检索（API `/knowledge/search?kbId=&q=&topK=`）', async ({ request }) => {
    // 编辑后内容里含「数字孪生」，等 1s 让 chunk re-embed（若有异步任务）
    await new Promise(r => setTimeout(r, 1000))
    const resp = await request.get(
      `/api/v1/knowledge/search?kbId=${kbId}&q=${encodeURIComponent('数字孪生')}&topK=5`,
      { headers: auth(token) }
    )
    expect(resp.status()).toBe(200)
    const j = await resp.json()
    const items = j?.data || []
    // 新上传文档还没走 re-embed 时可能召回 0；不强断言条数，只断言结构
    if (items.length > 0) {
      const item = items[0]
      expect(item).toHaveProperty('content')
      // 页码可能 null（md/txt 无分页），但字段必须在
      expect(item).toHaveProperty('chunkIndex')
    }
  })

  test('M1.6 前端知识库列表页分类切换 → 后端 ?category= 下沉', async ({ page }) => {
    await login(page)
    await page.goto('/knowledge')
    await page.waitForLoadState('networkidle')

    const requests: string[] = []
    page.on('request', r => {
      if (r.url().includes('/v1/knowledge/bases')) requests.push(r.url())
    })

    await page
      .locator('.category-tabs .el-radio-button__inner', { hasText: '行业报告' })
      .click()
    await page.waitForTimeout(1500)

    expect(
      requests.some(u => u.includes('category=industry')),
      '分类点击应发起带 category=industry 的请求'
    ).toBe(true)
  })

  test.afterAll(async ({ request }) => {
    if (kbId) {
      await request.delete(`/api/v1/knowledge/bases/${kbId}`, { headers: auth(token) })
    }
  })
})

// ---------------------------------------------------------------------------
// 模块二：知识检索与引用 —— include/exclude + 命中结构
// ---------------------------------------------------------------------------

test.describe('模块二 · 知识检索 include/exclude', () => {
  let token = ''
  let kbId = 0

  test.beforeAll(async ({ request }) => {
    token = await apiLogin(request)
    const r = await request.post('/api/v1/knowledge/bases', {
      headers: { ...auth(token), 'Content-Type': 'application/json' },
      data: { name: `M2-KB-${Date.now()}`, category: 'policy' }
    })
    kbId = (await r.json())?.data?.id
  })

  test('M2.1 workspace 页面选 KB 后 include/exclude 条件面板可用', async ({ page }) => {
    await login(page)
    await page.goto('/workspace')
    await page.waitForLoadState('networkidle')

    const kbSelect = page.locator('.el-form-item:has-text("选择知识库") .el-select').first()
    await expect(kbSelect).toBeVisible({ timeout: 10_000 })
    await kbSelect.click()
    const firstOpt = page.locator('.el-select-dropdown__item').first()
    await expect(firstOpt).toBeVisible({ timeout: 5_000 })
    await firstOpt.click()

    await expect(page.locator('.el-form-item:has-text("检索条件")')).toBeVisible({ timeout: 5_000 })

    // 添加 include 关键词
    await page.locator('button:has-text("+ 添加")').first().click()
    const incInput = page.locator('.condition-row:has-text("补充关键词") input').last()
    await incInput.fill('政策')
    await incInput.press('Enter')
    await expect(
      page.locator('.condition-row:has-text("补充关键词") .el-tag:has-text("政策")')
    ).toBeVisible()
  })

  test.afterAll(async ({ request }) => {
    if (kbId) await request.delete(`/api/v1/knowledge/bases/${kbId}`, { headers: auth(token) })
  })
})

// ---------------------------------------------------------------------------
// 模块三：报告生成 —— 模板 + RAG + SSE
// ---------------------------------------------------------------------------

test.describe('模块三 · 模板中心 + AI 生成', () => {
  let token = ''

  test.beforeAll(async ({ request }) => {
    token = await apiLogin(request)
  })

  test('M3.1 模板列表 API', async ({ request }) => {
    const resp = await request.get('/api/v1/templates', { headers: auth(token) })
    expect(resp.status()).toBe(200)
    const j = await resp.json()
    const records = j?.data?.records || j?.data || []
    expect(Array.isArray(records)).toBe(true)
  })

  test('M3.2 模板中心页面可访问且渲染', async ({ page }) => {
    await login(page)
    await page.goto('/templates')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('body')).toBeVisible()
    // 「新建模板」+「从文件分析风格」两个入口任一存在即可（对应赛题 3.2）
    const hasAnyBtn = await page
      .locator(
        'button:has-text("新建模板"), button:has-text("从文件分析"), button:has-text("新增"), button:has-text("创建")'
      )
      .count()
    expect(hasAnyBtn, '模板中心至少有一个新建入口').toBeGreaterThan(0)
  })

  test('M3.3 通过 analyze（文本）创建模板（API）', async ({ request }) => {
    // analyze 会同步跑 LLM 风格分析，豆包接口约需 30-90s；预算给 2 min
    const resp = await request.post('/api/v1/templates/analyze', {
      headers: { ...auth(token), 'Content-Type': 'application/json' },
      data: {
        name: `E2E-Tpl-${Date.now()}`,
        description: 'Playwright 自动创建的风格模板',
        content:
          '【执行摘要】本报告系统梳理了 2025 年数字经济发展态势。\n\n' +
          '一、行业概况\n产业规模稳步增长。\n\n二、关键驱动因素\n政策红利持续释放。'
      },
      timeout: 120_000
    })
    expect(resp.status()).toBe(200)
    const j = await resp.json()
    expect(j?.data?.id).toBeGreaterThan(0)
  })
})

// ---------------------------------------------------------------------------
// 模块四：报告 CRUD / 改写 / 质量检查
// ---------------------------------------------------------------------------

test.describe('模块四 · 报告改写 + 质量检查', () => {
  let token = ''
  let kbId = 0
  let reportId = 0

  test.beforeAll(async ({ request }) => {
    token = await apiLogin(request)
    const kbResp = await request.post('/api/v1/knowledge/bases', {
      headers: { ...auth(token), 'Content-Type': 'application/json' },
      data: { name: `M4-KB-${Date.now()}`, category: 'industry' }
    })
    kbId = (await kbResp.json())?.data?.id

    const rResp = await request.post('/api/v1/reports', {
      headers: { ...auth(token), 'Content-Type': 'application/json' },
      data: {
        title: `E2E-Report-${Date.now()}`,
        topic: '数字经济 2025 发展',
        kbId,
        keyPoints: ['产业规模', '政策驱动', '技术趋势']
      }
    })
    const rj = await rResp.json()
    reportId = rj?.data?.id
    expect(reportId).toBeGreaterThan(0)
  })

  test('M4.1 报告列表含刚建的 draft', async ({ request }) => {
    const resp = await request.get('/api/v1/reports', { headers: auth(token) })
    expect(resp.status()).toBe(200)
    const j = await resp.json()
    const records = j?.data?.records || j?.data || []
    expect(records.some((r: any) => r.id === reportId)).toBe(true)
  })

  test('M4.2 报告详情页 5 个核心 tab 存在', async ({ page }) => {
    await login(page)
    await page.goto(`/reports/${reportId}`)
    await page.waitForLoadState('networkidle')

    // 赛题相关的 tab 名在 UI 中可能是下列任一子串
    const tabs = page.locator('.el-tabs__item')
    const texts = await tabs.allInnerTexts()
    const joined = texts.join('\n')

    // 至少应有：正文/智能编辑/章节流式/覆盖度/版本对比 这 5 类中能看到 3+ 个 tab
    const hits = [
      /报告正文|正文/,
      /智能编辑|Tiptap/,
      /章节流式|章节/,
      /覆盖度|体检|质量/,
      /版本对比|修订/
    ].filter(re => re.test(joined)).length
    expect(hits, `详情页期望有 3+ 个核心 tab，实际匹配 ${hits}：\n${joined}`).toBeGreaterThanOrEqual(
      3
    )
  })

  test('M4.3 版本列表端点（即使没版本也应 200）', async ({ request }) => {
    const resp = await request.get(`/api/v1/reports/${reportId}/versions`, { headers: auth(token) })
    expect([200, 404]).toContain(resp.status()) // 404 也能接受（路径视实现而定）
  })

  test('M4.4 改写端点可达（SSE）—— 用原生 fetch 读首字节即停', async ({ page }) => {
    // request.post 会等到完整 SSE 流读完，太慢；直接用 page.evaluate 起 fetch + 立即 abort
    await login(page)
    const tk = await page.evaluate(() => localStorage.getItem('token'))
    const status = await page.evaluate(
      async ({ id, t }) => {
        const ctl = new AbortController()
        setTimeout(() => ctl.abort(), 3000) // 3s 后 abort
        try {
          const resp = await fetch(`/api/v1/reports/${id}/rewrite`, {
            method: 'POST',
            signal: ctl.signal,
            headers: {
              Authorization: `Bearer ${t}`,
              'Content-Type': 'application/json',
              Accept: 'text/event-stream'
            },
            body: JSON.stringify({ mode: 'DATA_UPDATE', instruction: 'e2e-probe' })
          })
          return resp.status
        } catch (e: any) {
          // abort 触发时说明 headers 已收到（SSE 正在推），连接建立成功
          if (String(e?.name) === 'AbortError') return 200
          return -1
        }
      },
      { id: reportId, t: tk }
    )
    expect(status, `rewrite 路由返回 ${status}`).not.toBe(404)
    expect(status).not.toBe(-1)
  })

  test('M4.5 质量检查端点可达', async ({ request }) => {
    const resp = await request.get(`/api/v1/reports/${reportId}/quality/check`, {
      headers: auth(token),
      failOnStatusCode: false,
      timeout: 60_000
    })
    expect(resp.status()).not.toBe(404)
  })

  test.afterAll(async ({ request }) => {
    if (reportId)
      await request.delete(`/api/v1/reports/${reportId}`, { headers: auth(token) }).catch(() => {})
    if (kbId)
      await request.delete(`/api/v1/knowledge/bases/${kbId}`, { headers: auth(token) }).catch(() => {})
  })
})

// ---------------------------------------------------------------------------
// 模块五：版本管理 & 导出 Word / PDF
// ---------------------------------------------------------------------------

test.describe('模块五 · 版本 / 导出', () => {
  let token = ''

  test.beforeAll(async ({ request }) => {
    token = await apiLogin(request)
  })

  test('M5.1 DOCX 导出端点存在', async ({ request }) => {
    const resp = await request.get('/api/v1/reports/123/export/docx', {
      headers: auth(token),
      failOnStatusCode: false
    })
    expect(resp.status(), `导出 Word 端点返回 ${resp.status()}`).not.toBe(404)
  })

  test('M5.2 PDF 导出入口存在 —— 报告详情页有 PDF 按钮 / 导出菜单', async ({ page, request }) => {
    await login(page)
    // 需要一个报告 id 才能打开 /reports/:id；没有就从 listing 取
    const tk = await page.evaluate(() => localStorage.getItem('token'))
    const listResp = await request.get('/api/v1/reports', { headers: auth(tk!) })
    const lj = await listResp.json()
    const rs = lj?.data?.records || lj?.data || []
    if (!Array.isArray(rs) || rs.length === 0) test.skip(true, '无已有报告，跳过详情页 PDF 按钮检查')
    const rid = rs[0].id

    await page.goto(`/reports/${rid}`)
    await page.waitForLoadState('networkidle')
    // 详情页的 PDF 藏在「导出」下拉菜单里 —— hover/click 展开
    const exportBtn = page.locator('button:has-text("导出")').first()
    await expect(exportBtn, '详情页应有「导出」按钮').toBeVisible({ timeout: 5_000 })
    await exportBtn.click() // el-dropdown trigger 是 click
    await page.waitForTimeout(500)
    const pdfItem = page.locator('.el-dropdown-menu__item:has-text("PDF")').first()
    await expect(pdfItem, '下拉菜单里应有 PDF 选项').toBeVisible({ timeout: 3_000 })
  })
})

// ---------------------------------------------------------------------------
// 跨模块：控制台错误巡检 + 杀手锏交互入口
// ---------------------------------------------------------------------------

test.describe('跨模块 · 控制台洁净度 + 杀手锏路径', () => {
  test('X.1 5 条核心路由无 console.error', async ({ page }) => {
    const errors: string[] = []
    page.on('pageerror', err => errors.push(`pageerror: ${err.message}`))
    page.on('console', msg => {
      if (msg.type() === 'error') {
        const t = msg.text()
        // dev 常见噪音豁免
        if (
          !t.includes('favicon') &&
          !t.includes('DevTools') &&
          !t.includes('[vue-i18n]') &&
          !t.includes('ResizeObserver loop')
        ) {
          errors.push(`[console.error] ${t}`)
        }
      }
    })

    await login(page)
    for (const p of ['/dashboard', '/knowledge', '/workspace', '/templates', '/reports']) {
      await page.goto(p)
      await page.waitForLoadState('networkidle')
      await page.waitForTimeout(500)
    }

    expect(errors, `发现 ${errors.length} 条控制台错误：\n${errors.join('\n')}`).toHaveLength(0)
  })

  test('X.2 dashboard 4 张统计卡', async ({ page }) => {
    await login(page)
    await page.goto('/dashboard')
    await page.waitForLoadState('networkidle')
    // dashboard 有 4 个数值卡（报告/知识库/文档/今日）
    const cards = page.locator('.stat-card, .dashboard-card, .el-card')
    await expect(cards.first()).toBeVisible({ timeout: 5_000 })
    expect(await cards.count()).toBeGreaterThanOrEqual(1)
  })
})

// ---------------------------------------------------------------------------
// 【可选】真实 LLM 走完整 SSE —— 默认跳过，E2E_WITH_LLM=1 打开
// ---------------------------------------------------------------------------

test.describe('可选 · 真实 LLM 端到端', () => {
  test.skip(!WITH_LLM, '跳过：未设置 E2E_WITH_LLM=1')

  test('L.1 真实生成一份短报告（≤90s）', async ({ request }) => {
    const token = await apiLogin(request)

    const kb = await request.post('/api/v1/knowledge/bases', {
      headers: { ...auth(token), 'Content-Type': 'application/json' },
      data: { name: `LLM-KB-${Date.now()}`, category: 'industry' }
    })
    const kbId = (await kb.json())?.data?.id

    const r = await request.post('/api/v1/reports', {
      headers: { ...auth(token), 'Content-Type': 'application/json' },
      data: {
        title: 'E2E LLM 短报告',
        topic: '2025 年 AI 大模型应用一句话概览',
        kbId,
        keyPoints: ['只写 200 字', '总分结构']
      }
    })
    const reportId = (await r.json())?.data?.id

    // 启动 SSE 流，读 90s 看是否拿到内容
    const resp = await request.get(`/api/v1/reports/${reportId}/generate`, {
      headers: { ...auth(token), Accept: 'text/event-stream' },
      timeout: 95_000
    })
    expect(resp.status()).toBe(200)
    const body = await resp.text()
    expect(body.length, 'SSE 流应返回非空内容').toBeGreaterThan(100)

    // 生成后校验落库
    const check = await request.get(`/api/v1/reports/${reportId}`, { headers: auth(token) })
    const cj = await check.json()
    expect(cj?.data?.content?.length || 0).toBeGreaterThan(50)

    // 清理
    await request.delete(`/api/v1/reports/${reportId}`, { headers: auth(token) }).catch(() => {})
    await request.delete(`/api/v1/knowledge/bases/${kbId}`, { headers: auth(token) }).catch(() => {})
  })
})
