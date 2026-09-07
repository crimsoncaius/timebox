async page => {
  await page.setViewportSize({width:1440,height:1000});
  await page.goto('http://127.0.0.1:5176/battle-plan?task=10');
  const checkbox=page.getByRole('checkbox',{name:'Uncheck subtask QA fix1 recurring checkpoint',exact:true});
  await checkbox.waitFor();
  if(!await checkbox.isChecked()) throw Error('Detached subtask not checked');
  const data=await (await page.context().request.get('http://127.0.0.1:8001/tasks')).json();
  const task=data.items.find(t=>t.id===10);
  if(task.recurring_template_id!==null||task.occurrence!==null||task.subtasks[0].id!==11) throw Error('Detached task lost identity');
  await page.screenshot({path:'C:/Users/Caius/Desktop/timebox/artifacts/qa-fixes-2026-09-06/coordinator-f4-preserved.png'});
  return 'PASS web task10/subtask11 remains checked after native series2 deletion; IDs retained, series detached';
}
