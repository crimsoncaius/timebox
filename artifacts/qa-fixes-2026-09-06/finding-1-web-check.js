async page => {
  await page.goto('http://127.0.0.1:5176/battle-plan?task=8');
  const checked = page.getByRole('checkbox', { name: 'Uncheck subtask QA fix1 checkpoint', exact: true });
  await checked.waitFor();
  if (!await checked.isChecked()) throw new Error('Checked state was not persisted');
  await page.route('**/api/subtasks/9/uncheck', route => route.fulfill({status:500, contentType:'application/json', body: JSON.stringify({detail:'Could not update subtask. Please retry.'})}));
  await checked.click();
  await page.getByRole('dialog').getByRole('alert').waitFor();
  await page.screenshot({path:'artifacts/qa-fixes-2026-09-06/web-fix1-visible-error.png'});
  if (!await checked.isChecked()) throw new Error('Failed update changed checkbox');
  await page.unroute('**/api/subtasks/9/uncheck');
  await checked.click();
  await page.getByRole('checkbox', { name: 'Check subtask QA fix1 checkpoint', exact: true }).waitFor();
  if (await page.getByRole('dialog').getByRole('alert').count()) throw new Error('Retry did not clear error');
  await page.setViewportSize({width:390,height:844});
  await page.getByRole('checkbox', { name: 'Check subtask QA fix1 checkpoint', exact: true }).click();
  await checked.waitFor();
  await page.reload();
  await checked.waitFor();
  if (!await checked.isChecked()) throw new Error('Mobile checked state did not persist');
  await page.screenshot({path:'artifacts/qa-fixes-2026-09-06/web-fix1-mobile-checked.png'});
  return 'Desktop persistence, visible failure, retry, mobile check and reload passed';
}
