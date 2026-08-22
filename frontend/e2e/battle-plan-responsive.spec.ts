import { expect, test } from '@playwright/test'

const project = {
  id: 7,
  name: 'Atlas',
  description: 'Launch project',
  deadline_date: null,
  deadline_at: null,
  created_at: '2026-08-15T00:00:00Z',
  updated_at: '2026-08-15T00:00:00Z',
}

const task = {
  id: 11,
  parent_id: null,
  project_id: project.id,
  project,
  task_type_id: null,
  task_type: null,
  title: 'Draft launch brief',
  description: '',
  ready_to_plan: false,
  status: 'open',
  urgency: null,
  importance: null,
  deadline_date: '2026-08-15',
  deadline_at: null,
  reminder_at: null,
  reminder_delivered_at: null,
  position: 0,
  archived_at: null,
  deleted_at: null,
  created_at: '2026-08-15T00:00:00Z',
  updated_at: '2026-08-15T00:00:00Z',
  overdue: false,
  subtasks: [],
}

for (const width of [1280, 1366]) {
  test(`Battle Plan card controls remain clickable at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await page.route('**/api/**', async (route) => {
      const url = new URL(route.request().url())
      if (url.pathname === '/api/tasks' && url.searchParams.get('state') === 'active') {
        await route.fulfill({
          json: {
            items: [task],
            timezone: 'UTC',
            server_now_iso: '2026-08-15T12:00:00Z',
          },
        })
        return
      }
      if (url.pathname === '/api/projects') {
        await route.fulfill({ json: [project] })
        return
      }
      if (url.pathname === '/api/task-types' || url.pathname === '/api/reminders/due') {
        await route.fulfill({ json: [] })
        return
      }
      await route.fulfill({ status: 404, json: { detail: 'Not found in test' } })
    })

    await page.goto('/battle-plan')
    const subtaskButton = page.getByRole('button', { name: 'Add a subtask to Draft launch brief' })
    await expect(subtaskButton).toBeVisible()
    await subtaskButton.click({ timeout: 2_000 })

    await expect(page.getByLabel('New subtask for Draft launch brief')).toBeVisible()
  })
}
