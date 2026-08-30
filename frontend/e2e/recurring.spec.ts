import type { APIRequestContext, Page } from '@playwright/test'
import { expect, test } from '@playwright/test'
import { TIMELINE_SLOT_HEIGHT_PX } from '../src/lib/time'

const apiBase = 'http://127.0.0.1:18001'

async function today(request: APIRequestContext) {
  const response = await request.get(`${apiBase}/health`)
  expect(response.ok()).toBeTruthy()
  return ((await response.json()) as { today: string }).today
}

async function ensureTaskType(request: APIRequestContext, name: string) {
  const list = await request.get(`${apiBase}/task-types`)
  const rows = (await list.json()) as Array<{ id: number; name: string }>
  const found = rows.find((row) => row.name === name.toLowerCase())
  if (found) return found.id
  const created = await request.post(`${apiBase}/task-types`, { data: { name } })
  expect(created.ok()).toBeTruthy()
  return ((await created.json()) as { id: number }).id
}

async function clearDay(request: APIRequestContext, date: string) {
  const response = await request.get(`${apiBase}/days/${date}`)
  if (!response.ok()) return
  const data = (await response.json()) as { time_blocks: Array<{ id: number }> }
  for (const block of data.time_blocks) {
    await request.delete(`${apiBase}/days/${date}/blocks/${block.id}`)
  }
}

async function createTemplateThroughUi(page: Page, values: {
  mode: 'scheduled' | 'quota'
  title: string
  taskTypeName: string
  frequency?: 'daily' | 'weekly' | 'monthly'
  quotaCount?: number
}) {
  await page.goto('/battle-plan?view=recurring')
  await page.getByRole('button', { name: 'New recurring task' }).click()
  const dialog = page.getByRole('dialog', { name: 'New recurring task' })
  if (values.mode === 'quota') await dialog.getByRole('radio', { name: 'Times per period' }).click()
  await dialog.getByLabel('Title', { exact: true }).fill(values.title)
  await dialog.getByLabel('Task type').selectOption({ label: values.taskTypeName.toLowerCase() })
  if (values.frequency) {
    const preset = values.mode === 'scheduled'
      ? { daily: 'Daily', weekly: 'Weekly', monthly: 'Monthly' }[values.frequency]
      : { daily: 'Per day', weekly: 'Per week', monthly: 'Per month' }[values.frequency]
    await dialog.getByRole('button', { name: preset, exact: true }).click()
  }
  if (values.mode === 'quota' && values.quotaCount) {
    await dialog.getByLabel('Times per period').fill(String(values.quotaCount))
  }
  await dialog.getByRole('button', { name: 'Create recurrence' }).click()
  await expect(page.getByRole('dialog', { name: `Recurring template ${values.title}` })).toBeVisible()
  await page.getByRole('button', { name: 'Close recurring details' }).click()
}

async function scheduleReadyTask(page: Page, taskLabel: RegExp, slot = 4) {
  await page.getByRole('button', { name: taskLabel }).first().click()
  const blocks = page.locator('[data-block-id]')
  const before = await blocks.count()
  const plannedLane = page.getByTestId('day-timeline').locator('[role="presentation"]').first()
  await plannedLane.scrollIntoViewIfNeeded()
  const box = await plannedLane.boundingBox()
  expect(box).toBeTruthy()
  await page.mouse.click(
    box!.x + box!.width / 2,
    box!.y + TIMELINE_SLOT_HEIGHT_PX * slot + TIMELINE_SLOT_HEIGHT_PX / 2,
  )
  await expect(blocks).toHaveCount(before + 1, { timeout: 15_000 })
  await page.reload()
  await expect(page.getByTestId('day-timeline')).toBeVisible()
}

test('creates a fixed recurrence and schedules its generated task', async ({ page, request }) => {
  const date = await today(request)
  await clearDay(request, date)
  const unique = `${Date.now()}-${Math.floor(Math.random() * 1e7)}`
  const typeName = `recurring-fixed-${unique}`
  const title = `Morning review ${unique}`
  const taskTypeId = await ensureTaskType(request, typeName)

  await createTemplateThroughUi(page, {
    mode: 'scheduled', title, taskTypeName: typeName, frequency: 'daily',
  })

  const tasksResponse = await request.get(`${apiBase}/tasks?state=active`)
  const generated = ((await tasksResponse.json()) as { items: Array<{ id: number; title: string; recurring_template_id: number }> }).items
    .filter((task) => task.title === title)
  expect(generated).toHaveLength(8)

  await page.getByRole('link', { name: 'Day', exact: true }).click()
  await expect(page.getByTestId('day-date')).toHaveText(date)
  await scheduleReadyTask(page, new RegExp(`^${title.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}`))

  await expect.poll(async () => {
    const response = await request.get(`${apiBase}/days/${date}`)
    const blocks = ((await response.json()) as { time_blocks: Array<{ task_id: number; task_type_id: number }> }).time_blocks
    return blocks.some((block) => block.task_id === generated[0].id && block.task_type_id === taskTypeId)
  }).toBe(true)

  await page.goto('/battle-plan')
  const card = page.locator('article[data-task-id]').filter({ hasText: title }).first()
  await expect(card.getByRole('button', { name: new RegExp(`↻.*${title}`) })).toBeVisible()
})

test('creates a weekly quota, schedules sessions, derives progress, and pauses without duplication', async ({ page, request }) => {
  const date = await today(request)
  await clearDay(request, date)
  const unique = `${Date.now()}-${Math.floor(Math.random() * 1e7)}`
  const typeName = `recurring-quota-${unique}`
  const title = `Gym ${unique}`
  await ensureTaskType(request, typeName)

  await createTemplateThroughUi(page, {
    mode: 'quota', title, taskTypeName: typeName, frequency: 'weekly', quotaCount: 3,
  })

  const taskResult = await request.get(`${apiBase}/tasks?state=active`)
  const parent = ((await taskResult.json()) as { items: Array<{ id: number; title: string; session_tasks: Array<{ id: number }> }> }).items.find((task) => task.title === title)
  expect(parent).toBeTruthy()
  expect(parent!.session_tasks).toHaveLength(3)

  await page.getByRole('link', { name: 'Day', exact: true }).click()
  await scheduleReadyTask(page, new RegExp(`${title} · Session 1`), 4)
  await scheduleReadyTask(page, new RegExp(`${title} · Session 2`), 6)

  await request.post(`${apiBase}/tasks/${parent!.session_tasks[0].id}/complete`)
  await expect.poll(async () => {
    const response = await request.get(`${apiBase}/tasks?state=active`)
    const item = ((await response.json()) as { items: Array<{ id: number; status: string; quota_completed: number }> }).items.find((task) => task.id === parent!.id)
    return `${item?.status}:${item?.quota_completed}`
  }).toBe('in_progress:1')
  await page.goto('/battle-plan')
  await expect(page.getByRole('region', { name: 'In progress tasks' }).getByText(title, { exact: true }).first()).toBeVisible()

  await request.post(`${apiBase}/tasks/${parent!.session_tasks[1].id}/complete`)
  await request.post(`${apiBase}/tasks/${parent!.session_tasks[2].id}/complete`)
  await expect.poll(async () => {
    const response = await request.get(`${apiBase}/tasks?state=active`)
    const item = ((await response.json()) as { items: Array<{ id: number; status: string }> }).items.find((task) => task.id === parent!.id)
    return item?.status
  }).toBe('completed')
  await page.reload()
  await expect(page.getByRole('region', { name: 'Completed tasks' }).getByText(title, { exact: true }).first()).toBeVisible()

  await page.goto('/battle-plan?view=recurring')
  const row = page.locator('article').filter({ hasText: title }).first()
  await Promise.all([
    page.waitForResponse((response) => response.url().endsWith('/pause') && response.ok()),
    row.getByRole('button', { name: 'Pause' }).click(),
  ])
  await Promise.all([
    page.waitForResponse((response) => response.url().includes('status=paused') && response.ok()),
    page.getByRole('button', { name: 'Paused' }).click(),
  ])
  await Promise.all([
    page.waitForResponse((response) => response.url().endsWith('/resume') && response.ok()),
    page.locator('article').filter({ hasText: title }).getByRole('button', { name: 'Resume' }).click(),
  ])
  await Promise.all([
    page.waitForResponse((response) => response.url().includes('status=active') && response.ok()),
    page.getByRole('button', { name: 'Active' }).click(),
  ])
  await expect(page.locator('article').filter({ hasText: title })).toHaveCount(1)

  const occurrences = await request.get(`${apiBase}/tasks?state=active`)
  const matching = ((await occurrences.json()) as { items: Array<{ title: string; occurrence_key: string | null }> }).items.filter((task) => task.title === title)
  expect(new Set(matching.map((task) => task.occurrence_key)).size).toBe(matching.length)
})
