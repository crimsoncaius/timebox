async page => {
  await page.setViewportSize({width:390,height:844});
  await page.goto('http://127.0.0.1:5176/day/2026-09-06');
  await page.getByRole('button',{name:'Edit planned block',exact:true}).click();
  await page.getByRole('dialog',{name:'Planned'}).waitFor();
  await page.keyboard.press('Escape');
  await page.getByRole('dialog',{name:'Planned'}).waitFor({state:'hidden'});
  await page.setViewportSize({width:1440,height:1000});
  const rail=page.getByRole('complementary',{name:'Block details'});
  await rail.getByRole('region',{name:'Ready to Plan tasks'}).waitFor();
  await page.getByRole('button',{name:'Edit planned block',exact:true}).click();
  await rail.getByRole('textbox',{name:'Note',exact:true}).waitFor();
  await page.keyboard.press('Escape');
  await rail.getByRole('region',{name:'Ready to Plan tasks'}).waitFor();
  await page.getByRole('button',{name:'Edit planned block',exact:true}).click();
  return 'PASS mobile Escape, desktop empty Ready to Plan rail, desktop Escape, reopen persisted block';
}
