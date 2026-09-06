// spec: specs/web-frontend-audit.plan.md
// seed: e2e/seed.spec.ts
import type { APIRequestContext } from '@playwright/test'
import { expect, test } from '@playwright/test'

const apiBase = 'http://127.0.0.1:18001'
const titlePrefix = 'Ready desktop item '

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

  test('day-timeline-remains-readable', async ({ page, request }) => {
    for (let index = 0; index < 12; index += 1) {
      const created = await request.post(`${apiBase}/tasks`, {
        data: { title: `${titlePrefix}${Date.now()}-${index}`, ready_to_plan: true },
      })
      expect(created.ok()).toBeTruthy()
    }

    // 1. Open Day at a 1024 by 900 desktop viewport.
    await page.setViewportSize({ width: 1024, height: 900 })
    await page.goto('/day/2026-05-15')
    await expect(page.getByTestId('day-timeline')).toBeVisible()

    const plannedLane = page.getByTestId('day-timeline').locator('[data-day-lane="planned"]')
    const laneBox = await plannedLane.boundingBox()
    expect(laneBox).toBeTruthy()
    expect(laneBox!.width).toBeGreaterThanOrEqual(240)
    await expect(page.getByTestId('day-inspector-rail')).toBeHidden()
    const readyPanel = page.getByRole('region', { name: 'Ready to Plan tasks' })
    await expect(readyPanel).toBeVisible()
    const readyPanelBox = await readyPanel.boundingBox()
    expect(readyPanelBox).toBeTruthy()
    expect(readyPanelBox!.height).toBeLessThanOrEqual(520)
    await expect(readyPanel.getByTestId('ready-to-plan-list')).toHaveCSS('overflow-y', 'auto')
  })
})
