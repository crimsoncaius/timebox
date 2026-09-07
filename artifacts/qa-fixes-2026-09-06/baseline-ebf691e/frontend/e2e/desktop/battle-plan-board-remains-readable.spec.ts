// spec: specs/web-frontend-audit.plan.md
// seed: e2e/seed.spec.ts
import type { APIRequestContext } from '@playwright/test'
import { expect, test } from '@playwright/test'

const apiBase = 'http://127.0.0.1:18001'
const titlePrefix = 'Desktop planning review '

async function purgeAuditTasks(request: APIRequestContext) {
  for (const state of ['active', 'trash']) {
    const response = await request.get(`${apiBase}/tasks?state=${state}`)
    if (!response.ok()) continue
    const tasks = (await response.json()) as { items: Array<{ id: number; title: string }> }
    for (const task of tasks.items.filter((item) => item.title.startsWith(titlePrefix))) {
      if (state === 'active') await request.delete(`${apiBase}/tasks/${task.id}`)
      await request.delete(`${apiBase}/tasks/${task.id}/permanent`)
    }
  }
}

test.describe('Desktop layout', () => {
  test.beforeEach(async ({ request }) => purgeAuditTasks(request))
  test.afterEach(async ({ request }) => purgeAuditTasks(request))

  test('battle-plan-board-remains-readable', async ({ page, request }) => {
    const title = `${titlePrefix}${Date.now()}`
    const created = await request.post(`${apiBase}/tasks`, { data: { title } })
    expect(created.ok()).toBeTruthy()

    // 1. Open Battle Plan at a 1280 by 900 desktop viewport with a normal task card.
    await page.setViewportSize({ width: 1280, height: 900 })
    await page.goto('/battle-plan')

    const board = page.getByTestId('battle-plan-board')
    const openLane = page.getByRole('region', { name: 'Open tasks' })
    const laneBox = await openLane.boundingBox()
    expect(laneBox).toBeTruthy()
    expect(laneBox!.width).toBeGreaterThanOrEqual(240)
    await expect(board).toHaveCSS('overflow-x', 'auto')
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(1280)

    // 2. Use the task card controls in the first lane.
    const subtaskButton = page.getByRole('button', { name: `Add a subtask to ${title}` })
    await expect(subtaskButton).toBeVisible()
    await subtaskButton.click()
    await expect(page.getByLabel(`New subtask for ${title}`)).toBeVisible()
  })
})
