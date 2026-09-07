async page => {
  const api = 'http://127.0.0.1:8001/days/2026-09-06/blocks/766';
  const path = '**/api/days/2026-09-06/blocks/766';
  const results = [];
  for (const [width,height] of [[1440,1000],[390,844]]) {
    await page.unroute(path);
    await page.setViewportSize({width,height});
    await page.context().request.patch(api,{data:{name:'QA fix2 note race',note:'Previously saved note'}});
    await page.goto('http://127.0.0.1:5176/day/2026-09-06');
    await page.getByRole('button',{name:'Edit planned block',exact:true}).click();
    const editor = width >= 1280 ? page.getByRole('complementary',{name:'Block details'}) : page.getByRole('dialog',{name:'Planned'});
    const name=editor.locator('input[id=block-name]');
    const note=editor.locator('textarea[id=block-note]');
    let releaseName;
    const gate=new Promise(resolve=>{releaseName=resolve});
    let startedName;
    const nameStarted=new Promise(resolve=>{startedName=resolve});
    const patches=[];
    await page.route(path,async route=>{
      const patch=route.request().postDataJSON();
      patches.push(patch);
      const response=await route.fetch();
      if('name' in patch) {startedName(); await gate;}
      await route.fulfill({response});
    });
    try {
      await name.fill('QA fix2 renamed block');
      await note.fill('New note that should not disappear');
      await nameStarted;
      // Let Note's debounce/save finish first; the held Name snapshot still has the old Note.
      await page.waitForResponse(r=>r.url().endsWith('/api/days/2026-09-06/blocks/766') && 'note' in r.request().postDataJSON());
      await page.waitForTimeout(1200);
      if(await note.inputValue()!=='New note that should not disappear') throw new Error('Note vanished before Name response');
      const nameArrived=page.waitForResponse(r=>r.url().endsWith('/api/days/2026-09-06/blocks/766') && 'name' in r.request().postDataJSON());
      releaseName();
      await nameArrived;
      await page.waitForTimeout(800);
      if(await note.inputValue()!=='New note that should not disappear') throw new Error('Note overwritten by stale Name response');
      if(!await note.evaluate(el=>el===document.activeElement)) throw new Error('Note lost focus');
      if(patches.some(p=>('note' in p) && p.note!=='New note that should not disappear')) throw new Error('Responsive peer saved stale Note');
      await page.screenshot({path:`artifacts/qa-fixes-2026-09-06/fix2-${width}-name-note-race.png`});
      await page.unroute(path);
      await page.reload();
      await page.getByRole('button',{name:'Edit planned block',exact:true}).click();
      if(await note.inputValue()!=='New note that should not disappear') throw new Error('Note did not persist on reload');
      if(await name.inputValue()!=='QA fix2 renamed block') throw new Error('Name did not persist on reload');
      results.push({width,height,patches,result:'focused Note preserved after delayed Name response, both fields persisted on reload'});
    } finally {releaseName(); await page.unroute(path);}
  }
  return results;
}

