# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: recurring.spec.ts >> creates a weekly quota, schedules sessions, derives progress, and pauses without duplication
- Location: e2e\recurring.spec.ts:113:1

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: locator.click: Test timeout of 30000ms exceeded.
Call log:
  - waiting for getByRole('button', { name: /Gym 1788697929021-3263915 · Session 1/ }).first()

```

# Page snapshot

```yaml
- generic [ref=e3]:
  - complementary [ref=e4]:
    - generic [ref=e5]:
      - heading "Timebox" [level=1] [ref=e6]
      - paragraph [ref=e7]: Monastic productivity
    - navigation [ref=e8]:
      - link "Day" [ref=e9] [cursor=pointer]:
        - /url: /day/2026-09-06
        - generic [ref=e10]: calendar_today
        - generic [ref=e11]: Day
      - link "Chronicle" [ref=e12] [cursor=pointer]:
        - /url: /history
        - generic [ref=e13]: history
        - generic [ref=e14]: Chronicle
      - link "Battle Plan" [ref=e15] [cursor=pointer]:
        - /url: /battle-plan
        - generic [ref=e16]: view_kanban
        - generic [ref=e17]: Battle Plan
      - link "Task types" [ref=e18] [cursor=pointer]:
        - /url: /task-types
        - generic [ref=e19]: category
        - generic [ref=e20]: Task types
    - generic [ref=e22]:
      - generic [ref=e23]: TB
      - generic [ref=e24]:
        - generic [ref=e25]: You
        - generic [ref=e26]: Cloud
  - generic [ref=e27]:
    - banner [ref=e28]:
      - heading "Timebox" [level=2] [ref=e30]
      - generic [ref=e31]:
        - link "Start Work Mode" [ref=e32] [cursor=pointer]:
          - /url: /day/2026-09-06?workMode=start
        - link "Settings" [ref=e33] [cursor=pointer]:
          - /url: /settings
          - generic [ref=e34]: settings
          - generic [ref=e35]: Settings
        - button "Switch to dark mode" [ref=e36]: dark_mode
    - main [ref=e37]:
      - generic [ref=e38]:
        - generic [ref=e39]:
          - generic [ref=e40]: 2026-09-06
          - generic [ref=e42]:
            - generic [ref=e43]:
              - heading "Sunday, September 6, 2026" [level=1] [ref=e44]
              - paragraph [ref=e45]: Timezone UTC.
            - generic [ref=e46]:
              - button "Previous day" [ref=e47]: ← Prev
              - button "Jump to date" [ref=e49]:
                - generic [ref=e50]: 06/09/2026
                - generic [ref=e51]: calendar_month
              - button "Next day" [ref=e52]: Next →
          - generic [ref=e54]:
            - heading "Planned" [level=3] [ref=e56]
            - heading "Actual" [level=3] [ref=e57]
            - generic [ref=e59]:
              - generic [ref=e60]: 8 AM
              - generic [ref=e62]: 9 AM
              - generic [ref=e64]: 10 AM
              - generic [ref=e66]: 11 AM
              - generic [ref=e68]: 12 PM
              - generic [ref=e70]: 1 PM
              - generic [ref=e72]: 2 PM
              - generic [ref=e74]: 3 PM
              - generic [ref=e76]: 4 PM
              - generic [ref=e78]: 5 PM
              - generic [ref=e80]: 6 PM
              - generic [ref=e82]: 7 PM
        - complementary "Block details" [ref=e133]:
          - region "Ready to Plan tasks" [ref=e134]:
            - generic [ref=e135]:
              - generic [ref=e136]:
                - paragraph [ref=e137]: Battle Plan
                - heading "Ready to Plan" [level=2] [ref=e138]
                - paragraph [ref=e139]: Drag a task to Planned, or select it and choose a time slot.
              - generic [ref=e140]: "0"
            - paragraph [ref=e142]: No tasks are waiting to be planned.
```

# Test source

```ts
  1   | import type { APIRequestContext, Page } from '@playwright/test'
  2   | import { expect, test } from '@playwright/test'
  3   | import { TIMELINE_SLOT_HEIGHT_PX } from '../src/lib/time'
  4   | 
  5   | const apiBase = 'http://127.0.0.1:18001'
  6   | 
  7   | async function today(request: APIRequestContext) {
  8   |   const response = await request.get(`${apiBase}/health`)
  9   |   expect(response.ok()).toBeTruthy()
  10  |   return ((await response.json()) as { today: string }).today
  11  | }
  12  | 
  13  | async function ensureTaskType(request: APIRequestContext, name: string) {
  14  |   const list = await request.get(`${apiBase}/task-types`)
  15  |   const rows = (await list.json()) as Array<{ id: number; name: string }>
  16  |   const found = rows.find((row) => row.name === name.toLowerCase())
  17  |   if (found) return found.id
  18  |   const created = await request.post(`${apiBase}/task-types`, { data: { name } })
  19  |   expect(created.ok()).toBeTruthy()
  20  |   return ((await created.json()) as { id: number }).id
  21  | }
  22  | 
  23  | async function clearDay(request: APIRequestContext, date: string) {
  24  |   const response = await request.get(`${apiBase}/days/${date}`)
  25  |   if (!response.ok()) return
  26  |   const data = (await response.json()) as { time_blocks: Array<{ id: number }> }
  27  |   for (const block of data.time_blocks) {
  28  |     await request.delete(`${apiBase}/days/${date}/blocks/${block.id}`)
  29  |   }
  30  | }
  31  | 
  32  | async function createTemplateThroughUi(page: Page, values: {
  33  |   mode: 'scheduled' | 'quota'
  34  |   title: string
  35  |   taskTypeName: string
  36  |   frequency?: 'daily' | 'weekly' | 'monthly'
  37  |   quotaCount?: number
  38  | }) {
  39  |   await page.goto('/battle-plan?view=recurring')
  40  |   await page.getByRole('button', { name: 'New recurring task' }).click()
  41  |   const dialog = page.getByRole('dialog', { name: 'New recurring task' })
  42  |   if (values.mode === 'quota') await dialog.getByRole('radio', { name: 'Times per period' }).click()
  43  |   await dialog.getByLabel('Title', { exact: true }).fill(values.title)
  44  |   await dialog.getByLabel('Task type').selectOption({ label: values.taskTypeName.toLowerCase() })
  45  |   if (values.frequency) {
  46  |     const preset = values.mode === 'scheduled'
  47  |       ? { daily: 'Daily', weekly: 'Weekly', monthly: 'Monthly' }[values.frequency]
  48  |       : { daily: 'Per day', weekly: 'Per week', monthly: 'Per month' }[values.frequency]
  49  |     await dialog.getByRole('button', { name: preset, exact: true }).click()
  50  |   }
  51  |   if (values.mode === 'quota' && values.quotaCount) {
  52  |     await dialog.getByLabel('Times per period').fill(String(values.quotaCount))
  53  |   }
  54  |   await dialog.getByRole('button', { name: 'Create recurrence' }).click()
  55  |   await expect(page.getByRole('dialog', { name: `Recurring template ${values.title}` })).toBeVisible()
  56  |   await page.getByRole('button', { name: 'Close recurring details' }).click()
  57  | }
  58  | 
  59  | async function scheduleReadyTask(page: Page, taskLabel: RegExp, slot = 4) {
> 60  |   await page.getByRole('button', { name: taskLabel }).first().click()
      |                                                               ^ Error: locator.click: Test timeout of 30000ms exceeded.
  61  |   const blocks = page.locator('[data-block-id]')
  62  |   const before = await blocks.count()
  63  |   const plannedLane = page.getByTestId('day-timeline').locator('[role="presentation"]').first()
  64  |   const slotOffset = TIMELINE_SLOT_HEIGHT_PX * slot + TIMELINE_SLOT_HEIGHT_PX / 2
  65  |   const initialBox = await plannedLane.boundingBox()
  66  |   expect(initialBox).toBeTruthy()
  67  |   // Today's page positions the current-time line in view. Bring the requested
  68  |   // slot below the sticky header before using viewport-relative mouse input.
  69  |   await page.evaluate((delta) => window.scrollBy({ top: delta, behavior: 'auto' }), initialBox!.y + slotOffset - 180)
  70  |   const box = await plannedLane.boundingBox()
  71  |   expect(box).toBeTruthy()
  72  |   await page.mouse.click(
  73  |     box!.x + box!.width / 2,
  74  |     box!.y + slotOffset,
  75  |   )
  76  |   await expect(blocks).toHaveCount(before + 1, { timeout: 15_000 })
  77  |   await page.reload()
  78  |   await expect(page.getByTestId('day-timeline')).toBeVisible()
  79  | }
  80  | 
  81  | test('creates a fixed recurrence and schedules its generated task', async ({ page, request }) => {
  82  |   const date = await today(request)
  83  |   await clearDay(request, date)
  84  |   const unique = `${Date.now()}-${Math.floor(Math.random() * 1e7)}`
  85  |   const typeName = `recurring-fixed-${unique}`
  86  |   const title = `Morning review ${unique}`
  87  |   const taskTypeId = await ensureTaskType(request, typeName)
  88  | 
  89  |   await createTemplateThroughUi(page, {
  90  |     mode: 'scheduled', title, taskTypeName: typeName, frequency: 'daily',
  91  |   })
  92  | 
  93  |   const tasksResponse = await request.get(`${apiBase}/tasks?state=active`)
  94  |   const generated = ((await tasksResponse.json()) as { items: Array<{ id: number; title: string; recurring_template_id: number }> }).items
  95  |     .filter((task) => task.title === title)
  96  |   expect(generated).toHaveLength(8)
  97  | 
  98  |   await page.getByRole('link', { name: 'Day', exact: true }).click()
  99  |   await expect(page.getByTestId('day-date')).toHaveText(date)
  100 |   await scheduleReadyTask(page, new RegExp(`^${title.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}`))
  101 | 
  102 |   await expect.poll(async () => {
  103 |     const response = await request.get(`${apiBase}/days/${date}`)
  104 |     const blocks = ((await response.json()) as { time_blocks: Array<{ task_id: number; task_type_id: number }> }).time_blocks
  105 |     return blocks.some((block) => block.task_id === generated[0].id && block.task_type_id === taskTypeId)
  106 |   }).toBe(true)
  107 | 
  108 |   await page.goto('/battle-plan')
  109 |   const card = page.locator('article[data-task-id]').filter({ hasText: title }).first()
  110 |   await expect(card.getByRole('button', { name: new RegExp(`↻.*${title}`) })).toBeVisible()
  111 | })
  112 | 
  113 | test('creates a weekly quota, schedules sessions, derives progress, and pauses without duplication', async ({ page, request }) => {
  114 |   const date = await today(request)
  115 |   await clearDay(request, date)
  116 |   const unique = `${Date.now()}-${Math.floor(Math.random() * 1e7)}`
  117 |   const typeName = `recurring-quota-${unique}`
  118 |   const title = `Gym ${unique}`
  119 |   await ensureTaskType(request, typeName)
  120 | 
  121 |   await createTemplateThroughUi(page, {
  122 |     mode: 'quota', title, taskTypeName: typeName, frequency: 'weekly', quotaCount: 3,
  123 |   })
  124 | 
  125 |   const taskResult = await request.get(`${apiBase}/tasks?state=active`)
  126 |   const parent = ((await taskResult.json()) as { items: Array<{ id: number; title: string; session_tasks: Array<{ id: number }> }> }).items.find((task) => task.title === title)
  127 |   expect(parent).toBeTruthy()
  128 |   expect(parent!.session_tasks).toHaveLength(3)
  129 | 
  130 |   await page.getByRole('link', { name: 'Day', exact: true }).click()
  131 |   await scheduleReadyTask(page, new RegExp(`${title} · Session 1`), 4)
  132 |   await scheduleReadyTask(page, new RegExp(`${title} · Session 2`), 6)
  133 | 
  134 |   await request.post(`${apiBase}/tasks/${parent!.session_tasks[0].id}/complete`)
  135 |   await expect.poll(async () => {
  136 |     const response = await request.get(`${apiBase}/tasks?state=active`)
  137 |     const item = ((await response.json()) as { items: Array<{ id: number; status: string; quota_completed: number }> }).items.find((task) => task.id === parent!.id)
  138 |     return `${item?.status}:${item?.quota_completed}`
  139 |   }).toBe('in_progress:1')
  140 |   await page.goto('/battle-plan')
  141 |   await expect(page.getByRole('region', { name: 'In progress tasks' }).getByText(title, { exact: true }).first()).toBeVisible()
  142 | 
  143 |   await request.post(`${apiBase}/tasks/${parent!.session_tasks[1].id}/complete`)
  144 |   await request.post(`${apiBase}/tasks/${parent!.session_tasks[2].id}/complete`)
  145 |   await expect.poll(async () => {
  146 |     const response = await request.get(`${apiBase}/tasks?state=active`)
  147 |     const item = ((await response.json()) as { items: Array<{ id: number; status: string }> }).items.find((task) => task.id === parent!.id)
  148 |     return item?.status
  149 |   }).toBe('completed')
  150 |   await page.reload()
  151 |   await expect(page.getByRole('region', { name: 'Completed tasks' }).getByText(title, { exact: true }).first()).toBeVisible()
  152 | 
  153 |   await page.goto('/battle-plan?view=recurring')
  154 |   const row = page.locator('article').filter({ hasText: title }).first()
  155 |   await Promise.all([
  156 |     page.waitForResponse((response) => response.url().endsWith('/pause') && response.ok()),
  157 |     row.getByRole('button', { name: 'Pause' }).click(),
  158 |   ])
  159 |   await Promise.all([
  160 |     page.waitForResponse((response) => response.url().includes('status=paused') && response.ok()),
```