import { expect, test } from '@playwright/test'

for (const direction of ['in', 'out'] as const) {
  test(`Reset Zoom preserves the visible time after zooming ${direction}`, async ({ page }) => {
    await page.goto('/day/2026-05-15')
    const lane = page.locator('[data-day-lane="planned"]')
    await expect(lane).toBeVisible()
    await page.getByRole('button', { name: 'View', exact: true }).click()
    const slider = page.getByRole('slider', { name: 'Zoom', exact: true })
    if (direction === 'out') await slider.press('Home')
    else for (let i = 0; i < 6; i++) await slider.press('ArrowRight')
    await lane.evaluate(element => {
      const rect = element.getBoundingClientRect()
      window.scrollBy(0, rect.top + rect.height * 0.6 - window.innerHeight / 2)
    })
    const anchor = await lane.evaluate(element => {
      const rect = element.getBoundingClientRect()
      const centre = (Math.max(0, rect.top) + Math.min(window.innerHeight, rect.bottom)) / 2
      return { fraction: (centre - rect.top) / rect.height, centre }
    })
    await page.getByRole('button', { name: /Reset zoom/ }).click()
    await expect(slider).toHaveValue('1')
    await expect.poll(() => lane.evaluate((element, before) => {
      const rect = element.getBoundingClientRect()
      return Math.abs(rect.top + before.fraction * rect.height - before.centre)
    }, anchor)).toBeLessThan(2)
  })
}

test('Reset Zoom at the end clamps to the document scroll bounds', async ({ page }) => {
  await page.goto('/day/2026-05-15')
  await expect(page.locator('[data-day-lane="planned"]')).toBeVisible()
  await page.getByRole('button', { name: 'View', exact: true }).click()
  const slider = page.getByRole('slider', { name: 'Zoom', exact: true })
  for (let i = 0; i < 6; i++) await slider.press('ArrowRight')
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight))
  await page.getByRole('button', { name: /Reset zoom/ }).click()
  await expect(slider).toHaveValue('1')
  await expect.poll(() => page.evaluate(() =>
    Math.abs(window.scrollY - (document.documentElement.scrollHeight - window.innerHeight)),
  )).toBeLessThan(2)
})
