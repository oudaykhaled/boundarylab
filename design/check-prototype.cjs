// Run with Playwright available on NODE_PATH; no production dependency is needed.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { pathToFileURL } = require('node:url');
(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1280, height: 1100 } });
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  await page.goto(pathToFileURL(path.join(__dirname, 'index.html')).href);
  await page.screenshot({ path: path.join(__dirname, 'preview.png'), fullPage: true });
  for (let i = 0; i < 3; i++) await page.locator(`#file-${i}`).uncheck();
  assert(await page.locator('#primary').isDisabled(), 'Empty selection blocks processing');
  for (let i = 0; i < 3; i++) await page.locator(`#file-${i}`).check();
  await page.locator('#primary').click();
  await page.waitForFunction(() => document.querySelector('#state-title').textContent === 'Pack ready');
  assert.equal(await page.locator('#history-count').textContent(), '1');
  await page.locator('#export-scenario').selectOption('cancel'); await page.locator('#primary').click();
  assert.match(await page.locator('#state-copy').textContent(), /No export was confirmed/);
  await page.locator('#export-scenario').selectOption('fail'); await page.locator('#primary').click();
  assert.equal(await page.locator('#state-title').textContent(), 'Export failed');
  await page.locator('#export-scenario').selectOption('download');
  const downloadWait = page.waitForEvent('download'); await page.locator('#primary').click();
  const download = await downloadWait;
  const manifest = JSON.parse(fs.readFileSync(await download.path(), 'utf8'));
  assert.equal(manifest.files.length, 3);
  for (const file of manifest.files) {
    const bytes = fs.readFileSync(path.join(__dirname, '../examples/fieldpack/fixtures', file.name));
    assert.equal(file.size, bytes.length); assert.equal(file.sha256, crypto.createHash('sha256').update(bytes).digest('hex'));
  }
  assert.equal(await page.locator('#state-title').textContent(), 'Sample download requested');
  for (const [scenario, title] of [['failure', 'Pack could not be prepared'], ['lost', 'Connection lost']]) {
    await page.locator('#new').click(); await page.locator('#scenario').selectOption(scenario); await page.locator('#primary').click();
    await page.waitForFunction(t => document.querySelector('#state-title').textContent === t, title);
    assert(await page.locator('#primary').isHidden(), 'Incomplete attempt cannot export');
  }
  await page.locator('#new').click(); await page.locator('#scenario').selectOption('success'); await page.locator('#primary').click();
  await page.locator('#cancel').click();
  assert.equal(await page.locator('#state-title').textContent(), 'Cancellation requested');
  assert(await page.locator('#new').isHidden(), 'Pending cleanup cannot start another attempt');
  await page.waitForFunction(() => document.querySelector('#state-title').textContent === 'Preparation cancelled');
  assert(await page.locator('#primary').isHidden());
  await page.locator('#history-open').click(); assert.equal(await page.locator('#history-list li').count(), 4);
  await page.keyboard.press('Escape'); assert(await page.locator('#history').isHidden());
  assert.equal(await page.evaluate(() => document.activeElement.id), 'history-open');
  await page.reload(); assert.equal(await page.locator('#history-count').textContent(), '0');
  assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
  await page.setViewportSize({ width: 320, height: 900 });
  assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), '320px reflow');
  await page.locator('#rtl').click(); assert.equal(await page.locator('#device').getAttribute('dir'), 'rtl');
  assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), 'RTL 320px reflow');
  await page.locator('#rtl').click();
  await page.evaluate(() => document.documentElement.style.fontSize = '32px');
  assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), '200% text at 320px');
  assert(await page.evaluate(() => [...document.querySelectorAll('.topline, .steps, .section-head, .summary')].every(el => el.scrollWidth <= el.clientWidth)), 'Internal controls are not clipped at 200%');
  await page.screenshot({ path: path.join(__dirname, 'reflow-320-text-200.png'), fullPage: true });
  assert.deepEqual(errors, []); console.log('PASS: selection, success, cancellation pending/terminal, failed/lost, export cancel/fail/download, fixture digest parity, history/reset/focus, RTL, 320px/200% reflow, no browser errors.');
  await browser.close();
})().catch(error => { console.error(error); process.exitCode = 1; });
