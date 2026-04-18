const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  const consoleMsgs = [];
  page.on('console', msg => consoleMsgs.push(msg.type() + ': ' + msg.text()));
  page.on('pageerror', err => consoleMsgs.push('PAGE_ERROR: ' + err.message));

  await page.goto('http://localhost:5173/login');
  console.log('Current URL:', page.url());

  await page.fill('input[placeholder="请输入用户名"]', 'admin');
  await page.fill('input[placeholder="请输入密码"]', 'admin123');

  await page.click('button.login-btn');

  await page.waitForTimeout(5000);

  console.log('After login URL:', page.url());
  console.log('Console messages:', JSON.stringify(consoleMsgs, null, 2));

  const token = await page.evaluate(() => localStorage.getItem('token'));
  console.log('Token in localStorage:', token ? token.substring(0, 30) + '...' : 'null');

  const pageContent = await page.evaluate(() => document.body.innerText.substring(0, 500));
  console.log('Page content (first 500):', pageContent);

  await browser.close();
})().catch(e => console.error('Test error:', e));
