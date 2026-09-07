async page => {
  await page.setViewportSize({width:1440,height:1000});
  await page.goto('http://127.0.0.1:5176/day/2026-09-06');
  await page.getByRole('button',{name:'Edit planned block',exact:true}).click();
  const note=page.locator('[data-inspector] textarea[id=block-note]');
  const patches=[];
  const observe=request=>{
    if(request.url().endsWith('/api/days/2026-09-06/blocks/766') && request.method()==='PATCH') patches.push(request.postDataJSON());
  };
  page.on('request',observe);
  const readSaved=async()=>{
    const day=await (await page.context().request.get('http://127.0.0.1:8001/days/2026-09-06')).json();
    return day.time_blocks.find(b=>b.id===766).note;
  };
  try {
    await note.fill('QA fix2 desktop before resize');
    await page.waitForTimeout(1200);
    await page.setViewportSize({width:390,height:844});
    await page.locator('[data-inspector=sheet]').waitFor();
    await note.fill('QA fix2 latest mobile after resize');
    await page.waitForTimeout(1800);
    if(await readSaved()!=='QA fix2 latest mobile after resize') throw new Error('Old responsive editor overwrote latest mobile Note');
    if(patches.some((patch,index)=>index>0 && patch.note==='QA fix2 desktop before resize')) throw new Error('Stale desktop Note saved again');
    await page.setViewportSize({width:1440,height:1000});
    await page.locator('[data-inspector=rail]').waitFor();
    if(await note.inputValue()!=='QA fix2 latest mobile after resize') throw new Error('Desktop lost mobile edit');
    await note.fill('QA fix2 unsaved draft survives resize');
    await page.setViewportSize({width:390,height:844});
    await page.locator('[data-inspector=sheet]').waitFor();
    if(await note.inputValue()!=='QA fix2 unsaved draft survives resize') throw new Error('Unsaved Note lost on resize');
    await page.waitForTimeout(1200);
    if(await readSaved()!=='QA fix2 unsaved draft survives resize') throw new Error('Resized draft did not persist');
    await page.screenshot({path:'artifacts/qa-fixes-2026-09-06/fix2-responsive-single-editor.png'});
    return {result:'PASS desktop/mobile saved edits and pending draft preserved across resize',patches};
  } finally {page.off('request',observe);}
}
