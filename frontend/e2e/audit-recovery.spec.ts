import { expect, test } from '@playwright/test'

test('closed mobile sidebar is absent from keyboard navigation and restores focus after Escape', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/battle-plan')
  const trigger = page.getByRole('button', { name: 'Lists & projects', exact: true })
  await expect(trigger).toBeVisible()
  await expect(page.getByRole('complementary', { name: 'Battle Plan lists and projects' })).toBeHidden()
  await trigger.focus()
  await page.keyboard.press('Tab')
  const focus = await page.evaluate(() => {
    const element = document.activeElement as HTMLElement
    const rect = element.getBoundingClientRect()
    return { inSidebar: !!element.closest('aside'), visible: rect.right > 0 && rect.left < innerWidth }
  })
  expect(focus).toEqual({ inSidebar: false, visible: true })
  await trigger.click()
  await expect(page.getByRole('dialog', { name: 'Lists & projects' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(trigger).toBeFocused()
  await expect(page.getByRole('complementary', { name: 'Battle Plan lists and projects' })).toBeHidden()
})

test('Day keeps its controls visible and Settings starts at its own heading', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 })
  await page.goto('/day/2026-05-15')
  const toolbar = page.locator('[data-day-toolbar]')
  await expect(toolbar).toBeVisible()
  await page.evaluate(() => window.scrollTo(0, 1400))
  await expect(toolbar.getByRole('button', { name: 'View', exact: true })).toBeInViewport()
  await expect(toolbar.getByRole('button', { name: 'Previous day' })).toBeInViewport()
  await page.getByRole('link', { name: 'Settings', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Settings', exact: true })).toBeInViewport()
})

test('mobile Battle Plan exposes every status without horizontal searching', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/battle-plan')
  const statuses = page.getByRole('navigation', { name: 'Task statuses' })
  await expect(statuses).toBeVisible()
  await statuses.getByRole('link', { name: /^Blocked/ }).click()
  await expect(page.getByRole('region', { name: 'Blocked tasks' })).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})
