async page => {
  await page.setViewportSize({width:390,height:844});
  const note=page.locator('[data-inspector=sheet] textarea[id=block-note]');
  await note.scrollIntoViewIfNeeded();
  await page.screenshot({path:'artifacts/qa-fixes-2026-09-06/fix2-responsive-single-editor.png'});
  await page.setViewportSize({width:1440,height:1000});
  await page.locator('[data-inspector=rail] textarea[id=block-note]').scrollIntoViewIfNeeded();
  return 'Review screenshots refreshed; desktop editor left open';
}
