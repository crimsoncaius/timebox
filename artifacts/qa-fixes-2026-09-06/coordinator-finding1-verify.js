async page => {
  const dialog=page.getByRole('dialog', {name:'Task details'});
  await dialog.getByRole('checkbox',{name:'Uncheck subtask Coordinator checkpoint',exact:true}).waitFor();
  if(!await dialog.getByRole('checkbox',{name:'Uncheck subtask Coordinator checkpoint',exact:true}).isChecked()) throw Error('Not checked');
  await page.reload();
  await page.getByRole('checkbox',{name:'Uncheck subtask Coordinator checkpoint',exact:true}).waitFor();
  if(!await page.getByRole('checkbox',{name:'Uncheck subtask Coordinator checkpoint',exact:true}).isChecked()) throw Error('Reload lost check');
  await page.keyboard.press('Escape');
  await page.getByRole('button',{name:'1 of 1 subtasks completed for QA coordinator finding1'}).waitFor();
  await page.screenshot({path:'C:/Users/Caius/Desktop/timebox/artifacts/qa-fixes-2026-09-06/coordinator-f1-web.png'});
  return 'PASS fresh task12/subtask13 UI create, check200, reopen persistence and board1/1; parent still open';
}
