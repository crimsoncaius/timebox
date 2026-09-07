async page => {
  await page.goto('http://127.0.0.1:5176/battle-plan?task=14');
  await page.getByRole('combobox', { name: 'Status', exact: true }).waitFor();
  if (await page.getByRole('combobox', { name: 'Status', exact: true }).inputValue() === 'blocked') {
    await page.getByRole('combobox', { name: 'Status', exact: true }).selectOption('open');
    await page.getByRole('button', { name: 'Save', exact: true }).click();
    await page.getByRole('dialog', { name: 'Task details' }).waitFor({ state: 'hidden' });
    await page.getByRole('heading', { name: 'QA fix3 blocked task', exact: true }).click();
  }
  await page.getByRole('combobox', { name: 'Status', exact: true }).selectOption('blocked');
  const saved = page.waitForResponse(response => response.url().endsWith('/api/tasks/14') && response.request().method() === 'PATCH');
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  const response = await saved;
  const task = await response.json();
  if (!response.ok() || !task.is_blocked || task.status !== 'open') throw new Error('Blocked condition did not save');
  await page.getByRole('dialog', { name: 'Task details' }).waitFor({ state: 'hidden' });
  const blockedCard = page.getByRole('region', { name: 'Blocked tasks', exact: true }).getByRole('heading', { name: 'QA fix3 blocked task', exact: true });
  await blockedCard.waitFor({ timeout: 3000 });
  await blockedCard.click();
  if (await page.getByRole('combobox', { name: 'Status', exact: true }).inputValue() !== 'blocked') throw new Error('Reopened control lost blocked condition');
  await page.reload();
  await page.getByRole('combobox', { name: 'Status', exact: true }).waitFor();
  if (await page.getByRole('combobox', { name: 'Status', exact: true }).inputValue() !== 'blocked') throw new Error('Refreshed control lost blocked condition');
  return 'Saved Blocked condition remains in Blocked column and editor across reopen and refresh';
}
