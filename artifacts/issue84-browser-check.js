async page => {
  const projects = await (await page.request.get('http://127.0.0.1:5176/api/projects')).json();
  const response = await page.request.post('http://127.0.0.1:5176/api/tasks', { data: { title: 'Issue 84 move verification', description: 'Temporary automated verification' } });
  if (!response.ok()) throw new Error(await response.text());
  const task = await response.json();
  const read = async () => (await (await page.request.get('http://127.0.0.1:5176/api/tasks')).json()).items.find(row => row.id === task.id);
  try {
    await page.goto('http://127.0.0.1:5176/battle-plan');
    await page.getByRole('button', { name: 'Admin', exact: true }).click();
    const open = async () => {
      await page.getByLabel('Actions for ' + task.title, { exact: true }).click();
      await page.getByRole('button', { name: 'Move to project', exact: true }).click();
    };
    await open();
    await page.getByLabel('Destination').selectOption(String(projects[0].id));
    await page.getByRole('button', { name: 'Cancel', exact: true }).click();
    if ((await read()).project_id !== null) throw new Error('Cancel changed assignment');
    await open();
    await page.getByLabel('Destination').selectOption(String(projects[0].id));
    await page.route('**/api/tasks/' + task.id, route => route.request().method() === 'PATCH' ? route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ detail: 'Verification save failed' }) }) : route.continue());
    await page.getByRole('button', { name: 'Move', exact: true }).click();
    await page.getByRole('alert').filter({ hasText: 'Verification save failed' }).waitFor();
    if ((await read()).project_id !== null) throw new Error('Failed move changed assignment');
    await page.unroute('**/api/tasks/' + task.id);
    for (const destination of [projects[0], projects[1], null]) {
      await page.getByLabel('Destination').selectOption(destination ? String(destination.id) : '');
      await page.getByRole('button', { name: 'Move', exact: true }).click();
      await page.getByRole('dialog').waitFor({ state: 'detached' });
      await page.locator('[data-task-id="' + task.id + '"]').waitFor({ state: 'detached' });
      if ((await read()).project_id !== (destination?.id ?? null)) throw new Error('Assignment did not persist');
      await page.getByRole('button', { name: destination?.name ?? 'Admin', exact: true }).click();
      await page.getByLabel('Actions for ' + task.title, { exact: true }).waitFor();
      await page.reload();
      await page.getByLabel('Actions for ' + task.title, { exact: true }).waitFor();
      if (destination) await open();
    }
    return 'PASS: cancel, failed save and retry, Admin → Project → Project → Admin, source removal and persistence after reload';
  } finally {
    await page.request.delete('http://127.0.0.1:5176/api/tasks/' + task.id);
    await page.request.delete('http://127.0.0.1:5176/api/tasks/' + task.id + '/permanent');
    await page.goto('http://127.0.0.1:5176/battle-plan');
  }
}
