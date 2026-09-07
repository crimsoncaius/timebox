async page => {
  const path = '**/api/days/2026-09-06/blocks/766';
  await page.context().request.patch('http://127.0.0.1:8001/days/2026-09-06/blocks/766', {data:{name:'QA fix2 note race',note:'Previously saved note'}});
  await page.goto('http://127.0.0.1:5176/day/2026-09-06');
  await page.getByRole('button', {name:'Edit planned block',exact:true}).click();
  let release;
  const gate = new Promise(resolve => {release = resolve;});
  let reached;
  const saved = new Promise(resolve => {reached = resolve;});
  const handler = async route => {
    const response = await route.fetch();
    reached();
    await gate;
    await route.fulfill({response});
  };
  await page.route(path, handler);
  const name = page.getByRole('textbox',{name:/^Name(?: Name)?$/});
  const note = page.getByRole('textbox',{name:/^Note(?: Note)?$/});
  try {
    await name.fill('QA fix2 renamed block');
    await note.fill('New note that should not disappear');
    await saved;
    if (await note.inputValue() !== 'New note that should not disappear') throw new Error('Unexpected note before response');
    await page.screenshot({path:'artifacts/qa-fixes-2026-09-06/fix2-note-before-response.png'});
    const arrived = page.waitForResponse(r=>r.url().endsWith('/api/days/2026-09-06/blocks/766') && r.request().method()==='PATCH');
    release();
    await arrived;
    await page.waitForTimeout(200);
    const actual = await note.inputValue();
    await page.screenshot({path:'artifacts/qa-fixes-2026-09-06/fix2-note-after-response.png'});
    if(actual !== 'New note that should not disappear') throw new Error('Note overwritten: '+JSON.stringify(actual));
    return 'PASS note draft survives name save';
  } finally {release(); await page.unroute(path,handler);}
}

