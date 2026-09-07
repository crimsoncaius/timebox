async page => {
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 });
    await page.goto('http://127.0.0.1:5176/battle-plan?task=14');
    const status = page.getByRole('combobox', { name: 'Status', exact: true });
    await status.waitFor();
    await status.selectOption('open');
    await page.getByRole('button', { name: 'Save', exact: true }).click();
    await page.getByRole('dialog').waitFor({ state: 'hidden' });
    const taskName = { name: 'QA fix3 blocked task', exact: true };
    await page.getByRole('region', { name: 'Open tasks', exact: true }).getByRole('heading', taskName).click();
    await status.selectOption('blocked');
    await page.getByRole('button', { name: 'Save', exact: true }).click();
    await page.getByRole('dialog').waitFor({ state: 'hidden' });
    const blocked = page.getByRole('region', { name: 'Blocked tasks', exact: true });
    const card = blocked.locator('article').filter({ has: page.getByRole('heading', taskName) });
    if (!await card.getByText('Blocked', { exact: true }).isVisible()) throw new Error('Missing Blocked badge');
    await blocked.getByRole('heading', taskName).click();
    if (await status.inputValue() !== 'blocked') throw new Error('Reopened editor lost Blocked');
    await page.getByRole('textbox', { name: 'Description', exact: true }).fill(`QA finding3 verified at ${width}px`);
    await page.getByRole('button', { name: 'Save', exact: true }).click();
    await page.getByRole('dialog').waitFor({ state: 'hidden' });
    await blocked.getByRole('heading', taskName).click();
    await page.reload();
    await status.waitFor();
    if (await status.inputValue() !== 'blocked') throw new Error('Refresh or description save lost Blocked');
    await status.scrollIntoViewIfNeeded();
    await page.screenshot({ path: `artifacts/qa-fixes-2026-09-06/fix3-${width}-blocked-detail.png` });
    await page.getByRole('button', { name: 'Close task details' }).click();
    await blocked.getByRole('heading', taskName).scrollIntoViewIfNeeded();
    await page.screenshot({ path: `artifacts/qa-fixes-2026-09-06/fix3-${width}-blocked-board.png` });
  }
  return 'Desktop1440 and mobile390: set/clear Blocked, board badge, reopen, unrelated save and refresh passed';
}
