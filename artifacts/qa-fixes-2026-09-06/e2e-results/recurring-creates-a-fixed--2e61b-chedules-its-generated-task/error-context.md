# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: recurring.spec.ts >> creates a fixed recurrence and schedules its generated task
- Location: e2e\recurring.spec.ts:81:1

# Error details

```
Error: expect(received).toHaveLength(expected)

Expected length: 8
Received length: 1
Received array:  [{"archived_at": null, "blocking_reason": null, "completed_at": null, "created_at": "2026-09-06T12:32:07", "deadline_at": null, "deadline_date": "2026-09-06", "deleted_at": null, "description": "", "expected_sessions": null, "id": 3, "importance": null, "is_blocked": false, "occurrence": {"id": 1, "occurrence_key": "scheduled:2026-09-06", "recurring_task_series_id": 1}, "occurrence_key": "scheduled:2026-09-06", "outstanding_occurrence_count": 1, "overdue": false, "parent_id": null, "parent_title": null, "planned_dates": [], "position": 3, "project": null, "project_id": null, "quota_completed": null, "quota_period_end": null, "quota_period_start": null, "ready_to_plan": false, "recurrence_kind": "scheduled", "recurring_template_id": 1, "recurring_template_title": "Morning review 1788697926420-2727352", "reminder_at": null, "reminder_delivered_at": null, "session_index": null, "session_tasks": [], "status": "open", "subtasks": [], "task_type": {"created_at": "2026-09-06T12:32:06", "id": 1, "name": "recurring-fixed-1788697926420-2727352", "updated_at": "2026-09-06T12:32:06"}, "task_type_id": 1, "title": "Morning review 1788697926420-2727352", "updated_at": "2026-09-06T12:32:07", "urgency": null, "version": 1}]
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
        - complementary "Battle Plan lists and projects" [ref=e39]:
          - navigation [ref=e40]:
            - button "All Tasks" [ref=e41]
            - button "Admin" [ref=e42]
            - link "Recurring" [ref=e43] [cursor=pointer]:
              - /url: /battle-plan?view=recurring
          - generic [ref=e44]:
            - generic [ref=e45]: Projects
            - button "New project" [ref=e46]: +
          - list "Projects"
          - status
          - navigation [ref=e47]:
            - button "Archive" [ref=e48]
            - button "Trash" [ref=e49]
        - generic [ref=e50]:
          - generic [ref=e51]:
            - generic [ref=e52]:
              - paragraph [ref=e53]: Battle Plan
              - heading "Recurring" [level=1] [ref=e54]
              - paragraph [ref=e55]: Templates create independent Battle Plan tasks seven days ahead.
            - button "New recurring task" [ref=e56]
          - generic [ref=e57]:
            - button "Active" [ref=e58]
            - button "Paused" [ref=e59]
            - button "Ended" [ref=e60]
          - article [ref=e62]:
            - button "Morning review 1788697926420-2727352" [ref=e63]:
              - generic [ref=e64]: Morning review 1788697926420-2727352
              - generic [ref=e65]: Admin · recurring-fixed-1788697926420-2727352
            - button "Daily" [ref=e66]
            - button "Next Sep 6, 2026" [ref=e67]:
              - generic [ref=e68]: Next
              - generic [ref=e69]: Sep 6, 2026
            - generic [ref=e70]:
              - generic [ref=e71]: active
              - button "Edit" [ref=e72]
              - button "Pause" [ref=e73]
              - button "End" [ref=e74]
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
  60  |   await page.getByRole('button', { name: taskLabel }).first().click()
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
> 96  |   expect(generated).toHaveLength(8)
      |                     ^ Error: expect(received).toHaveLength(expected)
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
  161 |     page.getByRole('button', { name: 'Paused' }).click(),
  162 |   ])
  163 |   await Promise.all([
  164 |     page.waitForResponse((response) => response.url().endsWith('/resume') && response.ok()),
  165 |     page.locator('article').filter({ hasText: title }).getByRole('button', { name: 'Resume' }).click(),
  166 |   ])
  167 |   await Promise.all([
  168 |     page.waitForResponse((response) => response.url().includes('status=active') && response.ok()),
  169 |     page.getByRole('button', { name: 'Active' }).click(),
  170 |   ])
  171 |   await expect(page.locator('article').filter({ hasText: title })).toHaveCount(1)
  172 | 
  173 |   const occurrences = await request.get(`${apiBase}/tasks?state=active`)
  174 |   const matching = ((await occurrences.json()) as { items: Array<{ title: string; occurrence_key: string | null }> }).items.filter((task) => task.title === title)
  175 |   expect(new Set(matching.map((task) => task.occurrence_key)).size).toBe(matching.length)
  176 | })
  177 | 
```