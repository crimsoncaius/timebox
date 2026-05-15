import type { APIRequestContext } from '@playwright/test'
import { expect, test } from '@playwright/test'
import { TIMELINE_SLOT_HEIGHT_PX } from '../src/lib/time'

/** E2E uses a persistent SQLite file; clear blocks so seed POSTs stay idempotent. */
async function clearDayBlocks(request: APIRequestContext, base: string, date: string) {
  const r = await request.get(`${base}/days/${date}`)
  if (!r.ok()) return
  const data = (await r.json()) as { time_blocks: Array<{ id: number }> }
  for (const b of data.time_blocks) {
    const del = await request.delete(`${base}/days/${date}/blocks/${b.id}`)
    expect(del.ok()).toBeTruthy()
  }
}

function canonicalTaskTypeName(name: string): string {
  return name
    .trim()
    .split('/')
    .map((s) => s.trim().toLowerCase())
    .join('/')
}

async function ensureTaskType(request: APIRequestContext, base: string, name: string): Promise<number> {
  const canonical = canonicalTaskTypeName(name)
  const list = await request.get(`${base}/task-types`)
  expect(list.ok()).toBeTruthy()
  const rows = (await list.json()) as Array<{ id: number; name: string }>
  const found = rows.find((r) => r.name === canonical)
  if (found) return found.id
  const r = await request.post(`${base}/task-types`, {
    data: { name: canonical },
    headers: { 'Content-Type': 'application/json' },
  })
  expect(r.ok()).toBeTruthy()
  return (await r.json()).id as number
}

/**
 * Seeds planned + actual blocks via API, then verifies Day and Chronicle in the browser.
 */
test('plan blocks and history', async ({ page, request }) => {
  const date = '2026-06-01'
  const base = 'http://127.0.0.1:8000'
  await page.goto(`/day/${date}`)
  await expect(page.getByTestId('day-date')).toHaveText(date, { timeout: 30_000 })
  await clearDayBlocks(request, base, date)

  const tidPlanned = await ensureTaskType(request, base, 'E2E planned')
  const tidActual = await ensureTaskType(request, base, 'E2E actual')

  const r1 = await request.post(`${base}/days/${date}/blocks`, {
    data: {
      lane: 'planned',
      task_type_id: tidPlanned,
      start_minute: 480,
      end_minute: 510,
    },
    headers: { 'Content-Type': 'application/json' },
  })
  expect(r1.ok()).toBeTruthy()

  const r2 = await request.post(`${base}/days/${date}/blocks`, {
    data: {
      lane: 'actual',
      task_type_id: tidActual,
      start_minute: 540,
      end_minute: 570,
    },
    headers: { 'Content-Type': 'application/json' },
  })
  expect(r2.ok()).toBeTruthy()

  await page.reload()
  const inspector = page.getByRole('complementary', { name: 'Block details' })
  const timeline = page.getByTestId('day-timeline')
  const timelineWidthBefore = (await timeline.boundingBox())?.width

  const firstPlannedBlock = page.locator('[data-block-id]').nth(0)
  await firstPlannedBlock.click()
  await expect(firstPlannedBlock).toHaveAttribute('data-selected', 'true')
  await expect(inspector).toBeVisible()
  const timelineWidthAfter = (await timeline.boundingBox())?.width
  expect(timelineWidthBefore).toBeTruthy()
  expect(timelineWidthAfter).toBeTruthy()
  expect(Math.round(timelineWidthBefore!)).toBe(Math.round(timelineWidthAfter!))

  await expect(inspector.getByLabel('Task type', { exact: true })).toHaveValue('e2e planned', {
    timeout: 15_000,
  })
  await expect(inspector.getByText('08:00', { exact: true })).toBeVisible()
  await expect(inspector.getByText('08:30', { exact: true })).toBeVisible()
  await expect(inspector.locator('#block-start')).toHaveCount(0)
  await page.keyboard.press('Escape')
  await expect(inspector.getByLabel('Task type', { exact: true })).toHaveCount(0)

  await page.locator('[data-block-id]').nth(1).click()
  await expect(inspector.getByLabel('Task type', { exact: true })).toHaveValue('e2e actual')
  await page.keyboard.press('Escape')
  await expect(inspector.getByLabel('Task type', { exact: true })).toHaveCount(0)

  await page.getByTestId('day-nav').getByRole('button', { name: 'Next day' }).click()
  await expect(page.getByTestId('day-date')).toHaveText('2026-06-02')
  await page.getByTestId('day-nav').getByRole('button', { name: 'Previous day' }).click()
  await expect(page.getByTestId('day-date')).toHaveText('2026-06-01')

  await page.getByTestId('day-calendar-trigger').click()
  await page.getByRole('button', { name: '2026-06-10' }).click()
  await expect(page).toHaveURL(/\/day\/2026-06-10$/)
  await expect(page.getByTestId('day-date')).toHaveText('2026-06-10')

  await page.getByRole('link', { name: 'Chronicle' }).click()
  await expect(page.getByRole('heading', { name: /Chronicle of focus/i })).toBeVisible()
  await expect(page.getByTestId('chronicle-calendar')).toBeVisible()
  await expect(page.getByTestId('chronicle-month-heading')).toContainText(/June 2026/i)
  await page.getByTestId('chronicle-day-2026-06-01').click()
  await expect(page).toHaveURL(/\/day\/2026-06-01$/)

  await page.getByRole('link', { name: 'Chronicle' }).click()
  await page.getByRole('link', { name: 'Day' }).click()
  await expect(page.getByTestId('day-timeline')).toBeVisible()

  await page.getByRole('link', { name: 'Settings' }).click()
  await expect(page).toHaveURL(/\/settings$/)
  await expect(page.getByText('Day window', { exact: true })).toBeVisible()
  await page.getByLabel(/Start hour/i).fill('9')
  await page.getByLabel(/Start hour/i).blur()
  await expect(page.getByText('Saved', { exact: true })).toBeVisible({ timeout: 15_000 })

  await page.getByRole('link', { name: 'Day' }).click()
  await expect(page.getByTestId('day-timeline')).toBeVisible()
  await expect(page.locator('text=Day window')).toHaveCount(0)

  await request.patch(`${base}/settings`, {
    headers: { 'Content-Type': 'application/json' },
    data: JSON.stringify({ start_hour: 8, end_hour: 20, show_full_day: false }),
  })
})

test('resize planned block stops at next block in same lane', async ({ page, request }) => {
  const date = '2026-06-02'
  const base = 'http://127.0.0.1:8000'
  await page.goto(`/day/${date}`)
  await expect(page.getByTestId('day-date')).toHaveText(date, { timeout: 30_000 })
  await clearDayBlocks(request, base, date)

  const tidA = await ensureTaskType(request, base, 'Resize A')
  const tidB = await ensureTaskType(request, base, 'Resize B')

  const r1 = await request.post(`${base}/days/${date}/blocks`, {
    data: {
      lane: 'planned',
      task_type_id: tidA,
      start_minute: 480,
      end_minute: 510,
    },
    headers: { 'Content-Type': 'application/json' },
  })
  expect(r1.ok()).toBeTruthy()
  const idA = (await r1.json()).time_blocks[0].id as number

  const r2 = await request.post(`${base}/days/${date}/blocks`, {
    data: {
      lane: 'planned',
      task_type_id: tidB,
      start_minute: 540,
      end_minute: 600,
    },
    headers: { 'Content-Type': 'application/json' },
  })
  expect(r2.ok()).toBeTruthy()

  await page.reload()
  const inspectorResize = page.getByRole('complementary', { name: 'Block details' })
  await page.locator(`[data-block-id="${idA}"]`).click()
  await expect(inspectorResize).toBeVisible()
  await expect(inspectorResize.getByLabel('Task type', { exact: true })).toHaveValue('resize a', {
    timeout: 15_000,
  })
  await page.keyboard.press('Escape')
  await expect(inspectorResize.getByLabel('Task type', { exact: true })).toHaveCount(0)

  const handle = page.locator(`[data-block-id="${idA}"]`).getByRole('button', { name: 'Resize block end' })
  await handle.scrollIntoViewIfNeeded()
  const box = await handle.boundingBox()
  expect(box).toBeTruthy()

  await page.mouse.move(box!.x + box!.width / 2, box!.y + box!.height / 2)
  await page.mouse.down()
  await expect(page.locator(`[data-block-id="${idA}"]`)).toHaveAttribute('data-dragging', 'true')
  await expect(page.locator(`[data-block-id="${idA}"]`)).toHaveAttribute('data-drag-kind', 'resize')
  // Try to drag far past the next block; UI should clamp to its start (540).
  await page.mouse.move(box!.x + box!.width / 2, box!.y + box!.height / 2 + TIMELINE_SLOT_HEIGHT_PX * 12, {
    steps: 12,
  })
  await page.mouse.up()

  await expect(page.getByText('Saved', { exact: true })).toBeVisible({ timeout: 15_000 })

  await page.locator(`[data-block-id="${idA}"]`).click()
  await expect(inspectorResize).toBeVisible()
  await expect(inspectorResize.getByText('09:00', { exact: true })).toBeVisible()

  const rDay = await request.get(`${base}/days/${date}`)
  expect(rDay.ok()).toBeTruthy()
  const blocks = (await rDay.json()).time_blocks as Array<{ id: number; end_minute: number }>
  const a = blocks.find((b) => b.id === idA)
  expect(a?.end_minute).toBe(540)
})

test('move planned block preserves duration and jumps past blocker', async ({ page, request }) => {
  const date = '2026-06-03'
  const base = 'http://127.0.0.1:8000'
  await page.goto(`/day/${date}`)
  await expect(page.getByTestId('day-date')).toHaveText(date, { timeout: 30_000 })
  await clearDayBlocks(request, base, date)

  const tidA = await ensureTaskType(request, base, 'Move A')
  const tidB = await ensureTaskType(request, base, 'Move B')

  const r1 = await request.post(`${base}/days/${date}/blocks`, {
    data: {
      lane: 'planned',
      task_type_id: tidA,
      start_minute: 480,
      end_minute: 510,
    },
    headers: { 'Content-Type': 'application/json' },
  })
  expect(r1.ok()).toBeTruthy()
  const idA = (await r1.json()).time_blocks[0].id as number

  const r2 = await request.post(`${base}/days/${date}/blocks`, {
    data: {
      lane: 'planned',
      task_type_id: tidB,
      start_minute: 540,
      end_minute: 600,
    },
    headers: { 'Content-Type': 'application/json' },
  })
  expect(r2.ok()).toBeTruthy()

  await page.reload()

  const body = page.locator(`[data-block-id="${idA}"]`).getByRole('button', { name: 'Edit planned block' })
  await body.scrollIntoViewIfNeeded()
  const box = await body.boundingBox()
  expect(box).toBeTruthy()
  const cx = box!.x + box!.width / 2
  const cy = box!.y + box!.height / 2
  await page.mouse.move(cx, cy)
  await page.mouse.down()
  // Any movement starts move mode (no long-press).
  await page.mouse.move(cx, cy + 1)
  await expect(page.locator(`[data-block-id="${idA}"]`)).toHaveAttribute('data-dragging', 'true')
  await expect(page.locator(`[data-block-id="${idA}"]`)).toHaveAttribute('data-drag-kind', 'move')
  // Drag down past blocker B (9:00–10:00) so A can land at 10:00–10:30 (duration preserved).
  // Four slots ≈ +120 minutes from 8:00 anchor → 10:00 start.
  await page.mouse.move(cx, cy + TIMELINE_SLOT_HEIGHT_PX * 4, { steps: 12 })
  await page.mouse.up()

  await expect(page.getByText('Saved', { exact: true })).toBeVisible({ timeout: 15_000 })
  await expect(
    page.getByRole('complementary', { name: 'Block details' }).getByLabel('Task type', { exact: true }),
  ).toHaveCount(0)

  const rDay = await request.get(`${base}/days/${date}`)
  expect(rDay.ok()).toBeTruthy()
  const blocks = (await rDay.json()).time_blocks as Array<{
    id: number
    start_minute: number
    end_minute: number
  }>
  const a = blocks.find((b) => b.id === idA)
  expect(a?.start_minute).toBe(600)
  expect(a?.end_minute).toBe(630)
})

test('move planned block: preview stays stable while pointer wiggles in the invalid gap', async ({
  page,
  request,
}) => {
  const date = '2026-06-25'
  const base = 'http://127.0.0.1:8000'
  await page.goto(`/day/${date}`)
  await expect(page.getByTestId('day-date')).toHaveText(date, { timeout: 30_000 })
  await clearDayBlocks(request, base, date)

  const tidA = await ensureTaskType(request, base, 'Hysteresis A')
  const tidB = await ensureTaskType(request, base, 'Hysteresis B')

  const r1 = await request.post(`${base}/days/${date}/blocks`, {
    data: {
      lane: 'planned',
      task_type_id: tidA,
      start_minute: 480,
      end_minute: 510,
    },
    headers: { 'Content-Type': 'application/json' },
  })
  expect(r1.ok()).toBeTruthy()
  const idA = (await r1.json()).time_blocks[0].id as number

  const r2 = await request.post(`${base}/days/${date}/blocks`, {
    data: {
      lane: 'planned',
      task_type_id: tidB,
      start_minute: 540,
      end_minute: 600,
    },
    headers: { 'Content-Type': 'application/json' },
  })
  expect(r2.ok()).toBeTruthy()

  await page.reload()

  const body = page.locator(`[data-block-id="${idA}"]`).getByRole('button', { name: 'Edit planned block' })
  await body.scrollIntoViewIfNeeded()
  const box = await body.boundingBox()
  expect(box).toBeTruthy()
  const cx = box!.x + box!.width / 2
  const cy = box!.y + box!.height / 2
  await page.mouse.move(cx, cy)
  await page.mouse.down()
  await page.mouse.move(cx, cy + 1)

  /** Hover in the invalid strip above blocker B without crossing the 600-preview threshold. */
  await page.mouse.move(cx, cy + TIMELINE_SLOT_HEIGHT_PX * 2.2, { steps: 8 })
  const y1 = (await page.locator(`[data-block-id="${idA}"]`).boundingBox())?.y
  await page.mouse.move(cx, cy + TIMELINE_SLOT_HEIGHT_PX * 2.35, { steps: 8 })
  const y2 = (await page.locator(`[data-block-id="${idA}"]`).boundingBox())?.y
  expect(y1).toBeTruthy()
  expect(y2).toBeTruthy()
  expect(Math.round(y1!)).toBe(Math.round(y2!))

  await page.mouse.move(cx, cy + TIMELINE_SLOT_HEIGHT_PX * 4, { steps: 12 })
  await page.mouse.up()

  await expect(page.getByText('Saved', { exact: true })).toBeVisible({ timeout: 15_000 })

  const rDay = await request.get(`${base}/days/${date}`)
  expect(rDay.ok()).toBeTruthy()
  const blocks = (await rDay.json()).time_blocks as Array<{
    id: number
    start_minute: number
    end_minute: number
  }>
  const a = blocks.find((b) => b.id === idA)
  expect(a?.start_minute).toBe(600)
  expect(a?.end_minute).toBe(630)
})

test('creates a hierarchical task type from the block editor and renames parent on Task types', async ({
  page,
  request,
}) => {
  const date = '2026-06-04'
  const base = 'http://127.0.0.1:8000'
  const uniq = `${Date.now()}-${Math.floor(Math.random() * 1e9)}`
  const rootPath = `e2ehp${uniq}`
  const childPath = `${rootPath}/x`
  const renamedRoot = `e2ehpr${uniq}`
  const renamedChild = `${renamedRoot}/x`

  await page.goto(`/day/${date}`)
  await expect(page.getByTestId('day-date')).toHaveText(date, { timeout: 30_000 })
  await clearDayBlocks(request, base, date)

  const tidRoot = await ensureTaskType(request, base, rootPath)
  const r = await request.post(`${base}/days/${date}/blocks`, {
    data: { lane: 'planned', task_type_id: tidRoot, start_minute: 480, end_minute: 510 },
    headers: { 'Content-Type': 'application/json' },
  })
  expect(r.ok()).toBeTruthy()
  const blockId = (await r.json()).time_blocks[0].id as number

  await page.reload()
  await page.locator(`[data-block-id="${blockId}"]`).click()
  await expect(page.getByRole('complementary', { name: 'Block details' })).toBeVisible()

  await page.getByLabel('Task type', { exact: true }).fill(childPath)
  await page.getByRole('option', { name: `Create "${childPath}"` }).click()
  await expect(page.getByText('Saved', { exact: true })).toBeVisible({ timeout: 15_000 })

  const rows = (await (await request.get(`${base}/task-types`)).json()) as Array<{ id: number; name: string }>
  expect(rows.map((x) => x.name)).toContain(rootPath)
  expect(rows.map((x) => x.name)).toContain(childPath)

  const rootRow = rows.find((row) => row.name === rootPath)
  expect(rootRow).toBeTruthy()
  await page.getByRole('link', { name: /Task types/i }).click()
  await page.getByRole('button', { name: `Edit ${rootPath}`, exact: true }).click()
  const taskTypeNameField = page.getByRole('textbox', {
    name: new RegExp(`Task type name ${rootRow!.id}`, 'i'),
  })
  await taskTypeNameField.fill(renamedRoot)
  await taskTypeNameField.blur()
  await expect
    .poll(async () => {
      const r = await request.get(`${base}/task-types`)
      if (!r.ok()) return false
      const names = ((await r.json()) as Array<{ name: string }>).map((x) => x.name)
      return names.includes(renamedChild) && names.includes(renamedRoot)
    })
    .toBe(true)

  const afterRows = (await (await request.get(`${base}/task-types`)).json()) as Array<{ name: string }>
  expect(afterRows.map((x) => x.name)).toContain(renamedChild)

  await page.goto(`/day/${date}`)
  await expect(page.getByTestId('day-date')).toHaveText(date, { timeout: 30_000 })
  await page.locator(`[data-block-id="${blockId}"]`).click()
  await expect(page.getByLabel('Task type', { exact: true })).toHaveValue(renamedChild)
})

test('draft-first: lane click shows ghost; block is created when task type is chosen', async ({
  page,
  request,
}) => {
  const date = '2026-06-20'
  const base = 'http://127.0.0.1:8000'
  await page.goto(`/day/${date}`)
  await expect(page.getByTestId('day-date')).toHaveText(date, { timeout: 30_000 })
  await clearDayBlocks(request, base, date)

  await ensureTaskType(request, base, 'E2E draft')
  await page.reload()
  await expect(page.getByTestId('day-timeline')).toBeVisible()

  // Planned lane: first interactive lane column (role=presentation on the lane surface).
  const plannedLane = page.getByTestId('day-timeline').locator('[role="presentation"]').first()
  await plannedLane.scrollIntoViewIfNeeded()
  const box = await plannedLane.boundingBox()
  expect(box).toBeTruthy()
  // Click below the sticky app header (top slots can sit under it). Slot index 4 → 10:00–10:30.
  const laneRelY = TIMELINE_SLOT_HEIGHT_PX * 4 + TIMELINE_SLOT_HEIGHT_PX * 0.5
  await page.mouse.click(box!.x + box!.width / 2, box!.y + laneRelY)

  await expect(page.getByRole('complementary', { name: 'Block details' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'New block' })).toBeVisible()
  const draftBlock = page.getByTestId('draft-block')
  await expect(draftBlock).toBeVisible()
  const draftEndHandle = draftBlock.getByRole('button', { name: /Resize draft block end/i })
  await draftEndHandle.scrollIntoViewIfNeeded()
  const dbox = await draftEndHandle.boundingBox()
  expect(dbox).toBeTruthy()
  await page.mouse.move(dbox!.x + dbox!.width / 2, dbox!.y + dbox!.height / 2)
  await page.mouse.down()
  await expect(draftBlock).toHaveAttribute('data-dragging', 'true')
  await expect(draftBlock).toHaveAttribute('data-drag-kind', 'resize')
  await page.mouse.up()

  await page.getByLabel('Task type', { exact: true }).fill('e2e draft')
  await page.getByRole('option', { name: /e2e draft/i }).click()
  await expect(page.getByText('Saved', { exact: true })).toBeVisible({ timeout: 15_000 })

  const rDay = await request.get(`${base}/days/${date}`)
  expect(rDay.ok()).toBeTruthy()
  const data = (await rDay.json()) as { time_blocks: Array<{ start_minute: number; end_minute: number }> }
  expect(data.time_blocks.length).toBe(1)
  expect(data.time_blocks[0].start_minute).toBe(600)
  expect(data.time_blocks[0].end_minute).toBe(630)
})

test('draft cleared when clicking outside the timeline', async ({ page }) => {
  const date = '2026-06-21'
  await page.goto(`/day/${date}`)
  await expect(page.getByTestId('day-date')).toHaveText(date, { timeout: 30_000 })
  await expect(page.getByTestId('day-timeline')).toBeVisible()

  const plannedLane = page.getByTestId('day-timeline').locator('[role="presentation"]').first()
  await plannedLane.scrollIntoViewIfNeeded()
  const box = await plannedLane.boundingBox()
  expect(box).toBeTruthy()
  const laneRelY = TIMELINE_SLOT_HEIGHT_PX * 4 + TIMELINE_SLOT_HEIGHT_PX * 0.5
  await page.mouse.click(box!.x + box!.width / 2, box!.y + laneRelY)

  await expect(page.getByTestId('draft-block')).toBeVisible()
  await page.locator('main h1').first().click()
  await expect(page.getByTestId('draft-block')).toHaveCount(0)
})
