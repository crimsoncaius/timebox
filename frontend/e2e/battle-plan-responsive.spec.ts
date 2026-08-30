import { expect, test } from '@playwright/test'

const apiBase = 'http://127.0.0.1:18001'

for (const width of [1280, 1366]) {
  test(`Battle Plan card controls remain clickable at ${width}px`, async ({ page, request }) => {
    const title = `Responsive task ${width} ${Date.now()}-${Math.floor(Math.random() * 1e9)}`
    const created = await request.post(`${apiBase}/tasks`, { data: { title } })
    expect(created.ok()).toBeTruthy()

    await page.setViewportSize({ width, height: 900 })
    await page.goto('/battle-plan')
    const subtaskButton = page.getByRole('button', { name: `Add a subtask to ${title}` })
    await expect(subtaskButton).toBeVisible()
    await subtaskButton.click({ timeout: 2_000 })

    await expect(page.getByLabel(`New subtask for ${title}`)).toBeVisible()
  })
}
