async page => {
  const base='http://127.0.0.1:8001/days/2026-09-06/blocks/766';
  const results=[];
  for(const width of [1440,390]) {
    await page.context().request.patch(base,{data:{name:'QA fix2 coordinator baseline',note:'Previously saved note'}});
    await page.setViewportSize({width,height:width===1440?1000:844});
    await page.goto('http://127.0.0.1:5176/day/2026-09-06');
    await page.getByRole('button',{name:'Edit planned block',exact:true}).click();
    const editor=page.locator(width===1440?'[data-inspector=rail]':'[data-inspector=sheet]');
    const name=editor.locator('input[id=block-name]');
    const note=editor.locator('textarea[id=block-note]');
    let release, reached;
    const held=new Promise(resolve=>reached=resolve);
    const gate=new Promise(resolve=>release=resolve);
    const routePattern='**/api/days/2026-09-06/blocks/766';
    const handler=async route=>{
      if(route.request().method()==='PATCH' && Object.hasOwn(route.request().postDataJSON(),'name')) {
        const result=await route.fetch();
        reached();
        await gate;
        await route.fulfill({response:result});
      } else await route.continue();
    };
    await page.route(routePattern,handler);
    try {
      await name.fill('QA fix2 coordinator renamed '+width);
      await note.focus();
      await Promise.race([held,page.waitForTimeout(5000).then(()=>{throw Error('No Name PATCH')})]);
      await note.fill('New note that should not disappear '+width);
      await page.waitForTimeout(1600);
      release();
      await page.waitForTimeout(700);
      if(await note.inputValue()!=='New note that should not disappear '+width) throw Error('Note overwritten '+width);
      if(!await note.evaluate(el=>el===document.activeElement)) throw Error('Note lost focus '+width);
      await page.screenshot({path:'C:/Users/Caius/Desktop/timebox/artifacts/qa-fixes-2026-09-06/coordinator-f2-'+width+'.png'});
      await page.reload();
      await page.getByRole('button',{name:'Edit planned block',exact:true}).click();
      if(await note.inputValue()!=='New note that should not disappear '+width) throw Error('Note not persisted '+width);
      results.push({width,result:'PASS held stale Name response preserves focused Note and reload persistence'});
    } finally {release();await page.unroute(routePattern,handler);}
  }
  return results;
}
