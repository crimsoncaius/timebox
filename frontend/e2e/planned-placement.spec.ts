import { expect, test, type APIRequestContext, type Page } from '@playwright/test'
import { TIMELINE_SLOT_HEIGHT_PX } from '../src/lib/time'

const apiBase = process.env.PLACEMENT_TEST_API ?? 'http://127.0.0.1:18001'
const headers = { 'X-Timebox-Protocol': 'activity-online-v1' }

async function seed(request: APIRequestContext, date: string, ranges: number[][]) {
  await request.patch(`${apiBase}/settings`, { headers, data: { start_hour: 8, end_hour: 20, show_full_day: false } })
  const day = await (await request.get(`${apiBase}/days/${date}`, { headers })).json()
  for (const block of day.time_blocks) {
    await request.delete(`${apiBase}/days/${date}/blocks/${block.id}`, { headers })
  }
  const ids: number[] = []
  for (const [start_minute, end_minute] of ranges) {
    const response = await request.post(`${apiBase}/days/${date}/blocks`, {
      headers, data: { lane: 'planned', start_minute, end_minute, name: 'Placement test obstacle' },
    })
    expect(response.ok(), await response.text()).toBeTruthy()
    const next = await response.json()
    ids.push(next.time_blocks.find((b: { start_minute: number }) => b.start_minute === start_minute).id)
  }
  return ids
}

async function queueTask(request: APIRequestContext, title: string) {
  const response = await request.post(`${apiBase}/tasks`, { headers, data: { title, ready_to_plan: true } })
  expect(response.ok()).toBeTruthy()
  return (await response.json()).id as number
}

async function atMinute(page: Page, minute: number) {
  const box = await page.locator('[data-day-lane="planned"]').boundingBox()
  if (!box) throw new Error('Planned lane is unavailable')
  return { x: box.x + box.width / 2, y: box.y + (minute - 480) / 30 * TIMELINE_SLOT_HEIGHT_PX + 2 }
}

test.use({ viewport: { width: 1440, height: 1000 } })
test.beforeEach(async ({ page }) => {
  // An explicit isolated API override lets review builds be tested without changing personal plans.
  if (process.env.PLACEMENT_TEST_API) {
    await page.route('**/api/**', async route => {
      if (!new URL(route.request().url()).pathname.startsWith('/api/')) {
        await route.continue()
        return
      }
      const response = await route.fetch({ url: route.request().url().replace(/^.*\/api\//, `${apiBase}/`) })
      await route.fulfill({ response })
    })
  }
})
test.afterEach(async ({ page }) => { await page.unrouteAll({ behavior: 'ignoreErrors' }) })

test('occupied-card click fits an exact gap and persists after reload', async ({ page, request }) => {
  const date = '2030-01-14'
  await seed(request, date, [[480, 602], [632, 720]])
  const title = `Click placement ${Date.now()}`
  const taskId = await queueTask(request, title)
  try {
    await page.goto(`/day/${date}`)
    await page.getByRole('button', { name: title, exact: true }).click()
    const point = await atMinute(page, 600)
    await page.mouse.click(point.x, point.y)
    await expect.poll(async () => {
      const day = await (await request.get(`${apiBase}/days/${date}`, { headers })).json()
      return day.time_blocks.find((b: { task_id: number }) => b.task_id === taskId)?.start_minute
    }).toBe(602)
    await page.reload()
    await expect(page.locator('[data-block]').filter({ hasText: title })).toContainText('10:02')
  } finally { await request.delete(`${apiBase}/tasks/${taskId}`, { headers }) }
})

test('queue drag previews and commits the same nearby placement', async ({ page, request }) => {
  const date = '2030-01-15'
  await seed(request, date, [[480, 570]])
  const title = `Drag placement ${Date.now()}`
  const taskId = await queueTask(request, title)
  try {
    await page.goto(`/day/${date}`)
    const handle = page.getByRole('button', { name: `Drag ${title} to Planned timeline`, exact: true })
    const source = await handle.boundingBox()
    if (!source) throw new Error('Queue drag handle is unavailable')
    await page.mouse.move(source.x + source.width / 2, source.y + source.height / 2)
    await page.mouse.down()
    let point = await atMinute(page, 540)
    await page.mouse.move(point.x, point.y, { steps: 16 })
    await expect(handle.locator('..')).toHaveAttribute('data-dragging', 'true')
    point = await atMinute(page, 540)
    await page.mouse.move(point.x, point.y)
    await expect(page.getByTestId('planned-placement-preview')).toContainText('9:30')
    await page.screenshot({ path: test.info().outputPath('placement-preview.png') })
    await page.mouse.up()
    await expect.poll(async () => {
      const day = await (await request.get(`${apiBase}/days/${date}`, { headers })).json()
      return day.time_blocks.find((b: { task_id: number }) => b.task_id === taskId)?.start_minute
    }).toBe(570)
  } finally { await request.delete(`${apiBase}/tasks/${taskId}`, { headers }) }
})

test('saved move picks closest space and exact resize saves at the neighbor', async ({ page, request }) => {
  const date = '2030-01-16'
  const [id] = await seed(request, date, [[480, 510], [540, 600]])
  await page.goto(`/day/${date}`)
  const card = page.locator(`[data-block-id="${id}"]`)
  const source = await card.boundingBox()
  if (!source) throw new Error('Block is unavailable')
  await page.mouse.move(source.x + source.width / 2, source.y + source.height / 2)
  await page.mouse.down()
  await page.mouse.move(source.x + source.width / 2, source.y + source.height / 2 + 2 * TIMELINE_SLOT_HEIGHT_PX, { steps: 12 })
  await expect(card).toContainText('8:30')
  await page.mouse.up()
  await expect.poll(async () => (await (await request.get(`${apiBase}/days/${date}`, { headers })).json())
    .time_blocks.find((b: { id: number }) => b.id === id).start_minute).toBe(510)

  const [resizeId] = await seed(request, date, [[480, 510], [547, 600]])
  await page.reload()
  const edge = page.locator(`[data-block-id="${resizeId}"]`).getByRole('button', { name: 'Resize block end' })
  const edgeBox = await edge.boundingBox()
  if (!edgeBox) throw new Error('Resize edge is unavailable')
  await page.mouse.move(edgeBox.x + edgeBox.width / 2, edgeBox.y + edgeBox.height / 2)
  await page.mouse.down()
  const destination = await atMinute(page, 600)
  await page.mouse.move(destination.x, destination.y, { steps: 12 })
  await page.mouse.up()
  await expect.poll(async () => (await (await request.get(`${apiBase}/days/${date}`, { headers })).json())
    .time_blocks.find((b: { id: number }) => b.id === resizeId).end_minute).toBe(547)
  await page.reload()
  await expect(page.locator(`[data-block-id="${resizeId}"]`)).toContainText('9:07')
})

test('occupied click finds a gap beyond the former threshold', async ({ page, request }) => {
  const date = '2030-01-17'
  await seed(request, date, [[480, 631]])
  const title = `Rejected placement ${Date.now()}`
  const taskId = await queueTask(request, title)
  try {
    await page.goto(`/day/${date}`)
    const button = page.getByRole('button', { name: title, exact: true })
    await button.click()
    const point = await atMinute(page, 600)
    await page.mouse.click(point.x, point.y)
    await expect.poll(async () => (await (await request.get(`${apiBase}/days/${date}`, { headers })).json())
      .time_blocks.find((b: { task_id: number }) => b.task_id === taskId)?.start_minute).toBe(631)
    const day = await (await request.get(`${apiBase}/days/${date}`, { headers })).json()
    expect(day.time_blocks).toHaveLength(2)
  } finally { await request.delete(`${apiBase}/tasks/${taskId}`, { headers }) }
})
