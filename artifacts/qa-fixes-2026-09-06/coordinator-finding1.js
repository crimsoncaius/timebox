async page => {
  await page.goto('http://127.0.0.1:5176/battle-plan');
  await page.getByRole('button', {name:'Add Open task', exact:true}).click();
  await page.getByRole('textbox', {name:'Task title', exact:true}).fill('QA coordinator finding1');
  await page.getByRole('button', {name:'Add task', exact:true}).click();
  await page.getByRole('heading', {name:'QA coordinator finding1', exact:true}).click();
  return await page.locator('body').innerText();
}
