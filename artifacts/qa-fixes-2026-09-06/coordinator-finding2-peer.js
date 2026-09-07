async page => {
  await page.setViewportSize({width:1440,height:1000});
  await page.goto('http://127.0.0.1:5176/day/2026-09-06');
  await page.getByRole('button',{name:'Edit planned block',exact:true}).click();
  const desktop=page.locator('[data-inspector] textarea[id=block-note]');
  const mobile=desktop;
  await desktop.fill('Coordinator desktop before resize');
  await page.waitForTimeout(1200);
  await page.setViewportSize({width:390,height:844});
  await mobile.fill('Coordinator latest mobile after resize');
  await page.waitForTimeout(1800);
  const server=await (await page.context().request.get('http://127.0.0.1:8001/days/2026-09-06')).json();
  const persisted=server.time_blocks.find(b=>b.id===766).note;
  const state={mobile:await mobile.inputValue(),persisted};
  await page.unrouteAll();
  if(persisted!=='Coordinator latest mobile after resize') throw Error('Hidden editor overwrote latest Note: '+JSON.stringify(state));
  await page.setViewportSize({width:1440,height:1000});
  if(await desktop.inputValue()!=='Coordinator latest mobile after resize') throw Error('Desktop value is stale');
  await desktop.fill('Coordinator pending draft carried across resize');
  await page.setViewportSize({width:390,height:844});
  if(await mobile.inputValue()!=='Coordinator pending draft carried across resize') throw Error('Resize lost pending draft');
  await page.waitForTimeout(1000);
  await page.keyboard.press('Escape');
  if(await page.locator('[data-inspector] textarea[id=block-note]').count()) throw Error('Escape did not close editor');
  return state;
}
