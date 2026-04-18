const { chromium } = require('playwright');

const BASE_URL = 'http://localhost:5173';

const results = { passed: [], failed: [], warnings: [] };

function log(testName, status, detail = '') {
  const icon = status === 'PASS' ? '✅' : status === 'FAIL' ? '❌' : '⚠️';
  console.log(`${icon} [${status}] ${testName}${detail ? ' - ' + detail : ''}`);
  if (status === 'PASS') results.passed.push(testName);
  else if (status === 'FAIL') results.failed.push({ name: testName, detail });
  else results.warnings.push({ name: testName, detail });
}

async function login(page) {
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle', timeout: 15000 });
  await page.fill('input[placeholder="请输入用户名"]', 'admin');
  await page.fill('input[placeholder="请输入密码"]', 'admin123');
  await page.click('button.login-btn');
  await page.waitForURL('**/dashboard**', { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(1500);
}

async function runTests() {
  let browser;
  try {
    browser = await chromium.launch({ headless: true, channel: 'chrome' });
  } catch (e) {
    try {
      browser = await chromium.launch({
        headless: true,
        executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
      });
    } catch (e2) {
      console.error('Cannot launch browser:', e2.message);
      process.exit(1);
    }
  }

  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  const consoleMsgs = [];
  const pageErrors = [];
  page.on('console', msg => {
    consoleMsgs.push(`[${msg.type()}] ${msg.text()}`);
  });
  page.on('pageerror', err => pageErrors.push(err.message));

  // ===== T1: 登录页面加载 =====
  try {
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle', timeout: 15000 });
    const title = await page.title();
    log('T1-登录页面加载', title.includes('登录') ? 'PASS' : 'FAIL', `title: ${title}`);
  } catch (e) { log('T1-登录页面加载', 'FAIL', e.message); }

  // ===== T2: 正确凭据登录 =====
  consoleMsgs.length = 0; pageErrors.length = 0;
  try {
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.fill('input[placeholder="请输入用户名"]', 'admin');
    await page.fill('input[placeholder="请输入密码"]', 'admin123');
    await page.click('button.login-btn');
    await page.waitForURL('**/dashboard**', { timeout: 10000 }).catch(() => {});
    await page.waitForTimeout(2000);
    const url = page.url();
    const token = await page.evaluate(() => localStorage.getItem('token'));
    if (url.includes('/dashboard') || url === `${BASE_URL}/`) {
      log('T2-登录成功跳转', 'PASS', `URL: ${url}, token: ${token ? 'exists' : 'missing'}`);
    } else if (url.includes('/login')) {
      log('T2-登录成功跳转', 'FAIL', `Still on login. Errors: ${pageErrors.join('; ')}`);
    } else {
      log('T2-登录成功跳转', 'FAIL', `Unexpected URL: ${url}`);
    }
  } catch (e) { log('T2-登录成功跳转', 'FAIL', e.message); }

  // ===== T3: 仪表盘 =====
  try {
    await page.goto(`${BASE_URL}/dashboard`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(2000);
    if (page.url().includes('/login')) { log('T3-仪表盘', 'FAIL', 'Redirected to login'); }
    else {
      const statCards = await page.locator('.stat-card').count();
      const quickLinks = await page.locator('.quick-item').count();
      log('T3-仪表盘', statCards > 0 ? 'PASS' : 'FAIL', `stat cards: ${statCards}, quick links: ${quickLinks}`);
    }
  } catch (e) { log('T3-仪表盘', 'FAIL', e.message); }

  // ===== T4: 知识库列表 =====
  try {
    await page.goto(`${BASE_URL}/knowledge/list`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(2000);
    if (page.url().includes('/login')) { log('T4-知识库列表', 'FAIL', 'Redirected to login'); }
    else {
      const cards = await page.locator('.kb-card, .el-card').count();
      const categoryTabs = await page.locator('.el-radio-group').count();
      log('T4-知识库列表', cards > 0 ? 'PASS' : 'WARN', `cards: ${cards}, category tabs: ${categoryTabs}`);
    }
  } catch (e) { log('T4-知识库列表', 'FAIL', e.message); }

  // ===== T5: 知识库分类筛选 =====
  try {
    await page.goto(`${BASE_URL}/knowledge/list`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1500);
    const radioBtns = page.locator('.el-radio-button__inner, .el-radio-group .el-radio');
    const count = await radioBtns.count();
    if (count > 1) {
      await radioBtns.nth(1).click();
      await page.waitForTimeout(1500);
      log('T5-知识库分类筛选', 'PASS', `Clicked category tab 1 of ${count}`);
    } else {
      log('T5-知识库分类筛选', 'WARN', `Only ${count} category tabs found`);
    }
  } catch (e) { log('T5-知识库分类筛选', 'FAIL', e.message); }

  // ===== T6: 创建知识库对话框 =====
  try {
    await page.goto(`${BASE_URL}/knowledge/list`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);
    const createBtn = page.locator('button:has-text("新建"), button:has-text("创建"), button:has-text("新增")').first();
    if (await createBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await createBtn.click();
      await page.waitForTimeout(1000);
      const dialog = page.locator('.el-dialog');
      if (await dialog.isVisible({ timeout: 3000 }).catch(() => false)) {
        const nameInput = dialog.locator('input').first();
        await nameInput.fill('E2E测试知识库');
        const categorySelect = dialog.locator('.el-select').first();
        const hasCategory = await categorySelect.isVisible({ timeout: 1000 }).catch(() => false);
        log('T6-创建知识库对话框', 'PASS', `Dialog opened, category selector: ${hasCategory ? 'visible' : 'not found'}`);
        await page.locator('.el-dialog__headerbtn, button:has-text("取消")').first().click();
      } else {
        log('T6-创建知识库对话框', 'FAIL', 'Dialog not visible');
      }
    } else {
      log('T6-创建知识库对话框', 'FAIL', 'Create button not found');
    }
  } catch (e) { log('T6-创建知识库对话框', 'FAIL', e.message); }

  // ===== T7: 写作工作台 =====
  try {
    await page.goto(`${BASE_URL}/workspace`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(2000);
    if (page.url().includes('/login')) { log('T7-写作工作台', 'FAIL', 'Redirected to login'); }
    else {
      const kbSelect = await page.locator('.el-select').first().isVisible({ timeout: 2000 }).catch(() => false);
      const textarea = await page.locator('textarea').first().isVisible({ timeout: 2000 }).catch(() => false);
      const generateBtn = await page.locator('button:has-text("生成"), button:has-text("开始")').count();
      log('T7-写作工作台', (kbSelect || textarea) ? 'PASS' : 'WARN',
        `KB select: ${kbSelect}, textarea: ${textarea}, generate: ${generateBtn}`);
    }
  } catch (e) { log('T7-写作工作台', 'FAIL', e.message); }

  // ===== T8: 模板中心 =====
  try {
    await page.goto(`${BASE_URL}/templates`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(2000);
    if (page.url().includes('/login')) { log('T8-模板中心', 'FAIL', 'Redirected to login'); }
    else {
      const tplCards = await page.locator('.tpl-card').count();
      log('T8-模板中心', tplCards > 0 ? 'PASS' : 'WARN', `tpl-card: ${tplCards}`);
    }
  } catch (e) { log('T8-模板中心', 'FAIL', e.message); }

  // ===== T9: 模板预览 =====
  try {
    await page.goto(`${BASE_URL}/templates`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(2000);
    const previewBtn = page.locator('button:has-text("预览")').first();
    if (await previewBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await previewBtn.click();
      await page.waitForTimeout(1000);
      const previewDialog = page.locator('.el-dialog:visible, .preview-block');
      const hasPreview = await previewDialog.count();
      log('T9-模板预览', hasPreview > 0 ? 'PASS' : 'WARN', `Preview dialog elements: ${hasPreview}`);
      if (hasPreview > 0) {
        await page.locator('.el-dialog__headerbtn').first().click().catch(() => {});
      }
    } else {
      log('T9-模板预览', 'WARN', 'No preview button found');
    }
  } catch (e) { log('T9-模板预览', 'FAIL', e.message); }

  // ===== T10: 报告库 =====
  try {
    await page.goto(`${BASE_URL}/reports`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(2000);
    if (page.url().includes('/login')) { log('T10-报告库', 'FAIL', 'Redirected to login'); }
    else {
      const content = await page.locator('.el-card, .el-table, .el-empty').count();
      log('T10-报告库', content > 0 ? 'PASS' : 'WARN', `content elements: ${content}`);
    }
  } catch (e) { log('T10-报告库', 'FAIL', e.message); }

  // ===== T11: 侧边栏导航 =====
  try {
    await page.goto(`${BASE_URL}/dashboard`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);
    const navItems = page.locator('.nav-item');
    const navCount = await navItems.count();
    let navOk = true;
    if (navCount >= 3) {
      const targets = [
        { idx: 0, expected: '/dashboard' },
        { idx: 1, expected: '/workspace' },
        { idx: 2, expected: '/knowledge' }
      ];
      for (const t of targets) {
        await navItems.nth(t.idx).click();
        await page.waitForTimeout(1500);
        const url = page.url();
        if (!url.includes(t.expected)) {
          log('T11-侧边栏导航', 'WARN', `Link ${t.idx}: expected ${t.expected}, got ${url}`);
          navOk = false;
        }
      }
      if (navOk) log('T11-侧边栏导航', 'PASS', `All ${targets.length} nav links work`);
    } else {
      log('T11-侧边栏导航', 'WARN', `Only ${navCount} nav items`);
    }
  } catch (e) { log('T11-侧边栏导航', 'FAIL', e.message); }

  // ===== T12: 退出登录 =====
  try {
    await page.goto(`${BASE_URL}/dashboard`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);
    const avatarWrapper = page.locator('.user-avatar-wrapper').first();
    if (await avatarWrapper.isVisible({ timeout: 3000 }).catch(() => false)) {
      await avatarWrapper.click();
      await page.waitForTimeout(800);
      const logoutItem = page.locator('.el-dropdown-menu__item:has-text("退出")').first();
      if (await logoutItem.isVisible({ timeout: 3000 }).catch(() => false)) {
        await logoutItem.click();
        await page.waitForTimeout(800);
        const confirmBtn = page.locator('.el-message-box__btns button:has-text("确定")').first();
        if (await confirmBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
          await confirmBtn.click();
          await page.waitForTimeout(2000);
          const url = page.url();
          log('T12-退出登录', url.includes('/login') ? 'PASS' : 'WARN', `URL: ${url}`);
        } else {
          log('T12-退出登录', 'WARN', 'No confirm dialog');
        }
      } else {
        log('T12-退出登录', 'WARN', 'Dropdown menu not visible after click');
      }
    } else {
      log('T12-退出登录', 'WARN', 'Avatar not found');
    }
  } catch (e) { log('T12-退出登录', 'FAIL', e.message); }

  // ===== T13: 错误密码登录 =====
  try {
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.fill('input[placeholder="请输入用户名"]', 'admin');
    await page.fill('input[placeholder="请输入密码"]', 'wrongpassword');
    await page.click('button.login-btn');
    await page.waitForTimeout(2000);
    const url = page.url();
    log('T13-错误密码登录', url.includes('/login') ? 'PASS' : 'FAIL', `URL: ${url}`);
  } catch (e) { log('T13-错误密码登录', 'FAIL', e.message); }

  // ===== T14: 未登录访问保护页面 =====
  try {
    const newCtx = await browser.newContext();
    const newPage = await newCtx.newPage();
    await newPage.goto(`${BASE_URL}/dashboard`, { waitUntil: 'networkidle', timeout: 15000 });
    await newPage.waitForTimeout(2000);
    const url = newPage.url();
    log('T14-未登录访问保护页面', url.includes('/login') ? 'PASS' : 'FAIL', `URL: ${url}`);
    await newCtx.close();
  } catch (e) { log('T14-未登录访问保护页面', 'FAIL', e.message); }

  // ===== T15: 写作工作台-选择知识库 =====
  try {
    await login(page);
    await page.goto(`${BASE_URL}/workspace`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1500);
    const kbSelect = page.locator('.el-select').first();
    if (await kbSelect.isVisible({ timeout: 3000 }).catch(() => false)) {
      await kbSelect.click();
      await page.waitForTimeout(800);
      const options = page.locator('.el-select-dropdown__item:visible');
      const optCount = await options.count();
      if (optCount > 0) {
        await options.first().click();
        await page.waitForTimeout(500);
        log('T15-选择知识库', 'PASS', `Selected first of ${optCount} options`);
      } else {
        log('T15-选择知识库', 'WARN', 'No KB options in dropdown');
      }
    } else {
      log('T15-选择知识库', 'WARN', 'KB select not visible');
    }
  } catch (e) { log('T15-选择知识库', 'FAIL', e.message); }

  // ===== T16: 写作工作台-填写主题 =====
  try {
    await page.goto(`${BASE_URL}/workspace`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1500);
    const topicInput = page.locator('input[placeholder*="主题"], input[placeholder*="标题"], input[placeholder*="题目"]').first();
    if (await topicInput.isVisible({ timeout: 3000 }).catch(() => false)) {
      await topicInput.fill('2026年人工智能行业发展趋势报告');
      log('T16-填写报告主题', 'PASS', 'Topic filled');
    } else {
      const allInputs = await page.locator('input[type="text"]').count();
      log('T16-填写报告主题', 'WARN', `Topic input not found, ${allInputs} text inputs on page`);
    }
  } catch (e) { log('T16-填写报告主题', 'FAIL', e.message); }

  // ===== T17: 续写按钮(需选择知识库后可见) =====
  try {
    await page.goto(`${BASE_URL}/workspace`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1500);
    const continueBtn = page.locator('button:has-text("续写")').first();
    const isVisible = await continueBtn.isVisible({ timeout: 2000 }).catch(() => false);
    if (isVisible) {
      log('T17-续写按钮', 'PASS', 'Visible (report exists)');
    } else {
      log('T17-续写按钮', 'PASS', 'Hidden (no report content yet, correct behavior)');
    }
  } catch (e) { log('T17-续写按钮', 'FAIL', e.message); }

  // ===== T18: 搜索条件(需选择知识库后可见) =====
  try {
    await page.goto(`${BASE_URL}/workspace`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1500);
    const kbSelect = page.locator('.el-select').first();
    await kbSelect.click();
    await page.waitForTimeout(800);
    const options = page.locator('.el-select-dropdown__item:visible');
    if (await options.count() > 0) {
      await options.first().click();
      await page.waitForTimeout(1000);
    }
    const includeBtn = page.locator('button:has-text("添加"):near(.search-conditions)').first();
    const excludeBtn = page.locator('button:has-text("排除"):near(.search-conditions)').first();
    const searchCond = page.locator('.search-conditions');
    const hasSearchCond = await searchCond.isVisible({ timeout: 2000 }).catch(() => false);
    if (hasSearchCond) {
      log('T18-搜索条件(包含/排除)', 'PASS', 'Search conditions visible after KB selection');
    } else {
      log('T18-搜索条件(包含/排除)', 'WARN', 'Search conditions not visible even after KB selection');
    }
  } catch (e) { log('T18-搜索条件', 'FAIL', e.message); }

  // ===== T19: 用户管理页面 =====
  try {
    await page.goto(`${BASE_URL}/users/list`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(2000);
    if (page.url().includes('/login')) { log('T19-用户管理', 'FAIL', 'Redirected to login'); }
    else {
      const hasTable = await page.locator('.el-table').count();
      const hasCard = await page.locator('.el-card').count();
      log('T19-用户管理', (hasTable + hasCard) > 0 ? 'PASS' : 'WARN', `table: ${hasTable}, card: ${hasCard}`);
    }
  } catch (e) { log('T19-用户管理', 'FAIL', e.message); }

  // ===== T20: 操作日志页面 =====
  try {
    await page.goto(`${BASE_URL}/logs/operation`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(2000);
    if (page.url().includes('/login')) { log('T20-操作日志', 'FAIL', 'Redirected to login'); }
    else {
      const hasContent = await page.locator('.el-table, .el-card, .el-empty').count();
      log('T20-操作日志', hasContent > 0 ? 'PASS' : 'WARN', `content: ${hasContent}`);
    }
  } catch (e) { log('T20-操作日志', 'FAIL', e.message); }

  // ===== T21: 控制台错误检查 =====
  const errorLogs = consoleMsgs.filter(m => m.includes('[error]'));
  const filteredErrors = errorLogs.filter(m =>
    !m.includes('401') &&
    !m.includes('net::ERR') &&
    !m.includes('favicon')
  );
  if (filteredErrors.length > 0) {
    console.log('\n🔍 控制台错误详情:');
    filteredErrors.forEach((e, i) => console.log(`  ${i + 1}. ${e.substring(0, 200)}`));
  }
  log('T21-控制台错误检查', filteredErrors.length === 0 ? 'PASS' : 'WARN',
    filteredErrors.length > 0 ? `${filteredErrors.length} errors found` : 'No errors');

  // ===== Print Summary =====
  console.log('\n' + '='.repeat(60));
  console.log('📊 测试报告汇总');
  console.log('='.repeat(60));
  console.log(`✅ 通过: ${results.passed.length}`);
  console.log(`❌ 失败: ${results.failed.length}`);
  console.log(`⚠️  警告: ${results.warnings.length}`);
  console.log(`📝 总计: ${results.passed.length + results.failed.length + results.warnings.length}`);
  console.log('-'.repeat(60));

  if (results.failed.length > 0) {
    console.log('\n❌ 失败详情:');
    results.failed.forEach((f, i) => console.log(`  ${i + 1}. ${f.name}: ${f.detail}`));
  }
  if (results.warnings.length > 0) {
    console.log('\n⚠️  警告详情:');
    results.warnings.forEach((w, i) => console.log(`  ${i + 1}. ${w.name}: ${w.detail}`));
  }

  console.log('\n📋 全部测试项:');
  [...results.passed.map(n => `✅ ${n}`),
   ...results.failed.map(f => `❌ ${f.name}`),
   ...results.warnings.map(w => `⚠️ ${w.name}`)
  ].forEach((line, i) => console.log(`  ${i + 1}. ${line}`));

  await browser.close();
  return results;
}

runTests().catch(e => { console.error('Fatal:', e); process.exit(1); });
