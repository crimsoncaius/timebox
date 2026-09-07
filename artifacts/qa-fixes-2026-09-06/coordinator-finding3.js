async page => {
  await page.setViewportSize({width:1440,height:1000});
  await page.goto('http://127.0.0.1:5176/battle-plan');
  await page.getByRole('button',{name:'Add Open task',exact:true}).click();
  await page.getByRole('textbox',{name:'Task title',exact:true}).fill('QA coordinator finding3');
  await page.getByRole('button',{name:'Add task',exact:true}).click();
  await page.getByRole('heading',{name:'QA coordinator finding3',exact:true}).click();
  const detailUrl=page.url();
  const dialog=page.getByRole('dialog',{name:'Task details'});
  await dialog.getByRole('combobox',{name:'Status',exact:true}).selectOption('blocked');
  const saved=page.waitForResponse(r=>r.url().includes('/api/tasks/')&&r.request().method()==='PATCH');
  await dialog.getByRole('button',{name:'Save',exact:true}).click();
  const task=await (await saved).json();
  if(task.is_blocked!==true||task.status!=='open') throw Error('Blocked not saved separately');
  await page.goto('http://127.0.0.1:5176/battle-plan');
  await page.getByRole('region',{name:'Blocked tasks'}).getByRole('heading',{name:'QA coordinator finding3',exact:true}).waitFor();
  await page.getByRole('heading',{name:'QA coordinator finding3',exact:true}).click();
  if(await dialog.getByRole('combobox',{name:'Status',exact:true}).inputValue()!=='blocked') throw Error('Reopen lost blocked');
  for(const width of [1440,390]) {
    await page.setViewportSize({width,height:width===1440?1000:844});
    await page.reload();
    await dialog.getByRole('combobox',{name:'Status',exact:true}).waitFor();
    if(await dialog.getByRole('combobox',{name:'Status',exact:true}).inputValue()!=='blocked') throw Error('Refresh lost blocked '+width);
    await page.screenshot({path:'C:/Users/Caius/Desktop/timebox/artifacts/qa-fixes-2026-09-06/coordinator-f3-'+width+'.png'});
  }
  return {id:task.id,detailUrl,result:'PASS Blocked saved separately, visible on board, reopened and refreshed at desktop/mobile'};
}
