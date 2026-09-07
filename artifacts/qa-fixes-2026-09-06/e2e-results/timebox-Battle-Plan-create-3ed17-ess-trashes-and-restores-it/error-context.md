# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: timebox.spec.ts >> Battle Plan creates a dated project task, persists subtask progress, trashes, and restores it
- Location: e2e\timebox.spec.ts:799:1

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: getByRole('region', { name: 'Open tasks' }).getByText('Today', { exact: true })
Expected: visible
Error: strict mode violation: getByRole('region', { name: 'Open tasks' }).getByText('Today', { exact: true }) resolved to 2 elements:
    1) <span class="max-w-32 truncate">Today</span> aka getByRole('button', { name: 'Due' })
    2) <span class="truncate">Today</span> aka getByRole('button', { name: 'Move Launch brief' })

Call log:
  - Expect "toBeVisible" with timeout 5000ms
  - waiting for getByRole('region', { name: 'Open tasks' }).getByText('Today', { exact: true })

```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
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
            - list "Projects" [ref=e47]:
              - listitem [ref=e48]:
                - generic [ref=e49]:
                  - button "Reorder Atlas 1788698084377-529494144" [ref=e50]:
                    - generic [ref=e51]: drag_indicator
                  - button "Atlas 1788698084377-529494144" [ref=e52]:
                    - generic [ref=e53]: folder
                    - generic [ref=e54]: Atlas 1788698084377-529494144
                  - button "More actions for Atlas 1788698084377-529494144" [ref=e55]:
                    - generic [ref=e56]: more_horiz
            - status
            - navigation [ref=e57]:
              - button "Archive" [ref=e58]
              - button "Trash" [ref=e59]
          - generic [ref=e60]:
            - generic [ref=e61]:
              - generic [ref=e62]:
                - paragraph [ref=e63]: Battle Plan
                - heading "Atlas 1788698084377-529494144" [level=1] [ref=e64]
              - generic [ref=e65]:
                - combobox "Sort tasks" [ref=e66]:
                  - option "Manual order" [selected]
                  - option "Deadline"
                  - option "Urgency"
                  - option "Importance"
                - generic [ref=e67]:
                  - checkbox "Hide completed" [ref=e68]
                  - text: Hide completed
            - generic [ref=e69]:
              - generic [ref=e70]:
                - generic [ref=e71]: Urgency
                - button "low" [ref=e72]
                - button "medium" [ref=e73]
                - button "high" [ref=e74]
                - button "unset" [ref=e75]
              - generic [ref=e76]:
                - generic [ref=e77]: Importance
                - button "low" [ref=e78]
                - button "medium" [ref=e79]
                - button "high" [ref=e80]
                - button "unset" [ref=e81]
              - group [ref=e82]:
                - generic "Task types" [ref=e83] [cursor=pointer]
            - generic [ref=e85]:
              - region "Open tasks" [ref=e86]:
                - generic [ref=e87]:
                  - heading "Open" [level=2] [ref=e88]
                  - generic [ref=e89]: "1"
                - form "New task" [ref=e90]:
                  - textbox "Task title" [ref=e91]:
                    - /placeholder: "Task name  ·  #project  !urgency  ~impact"
                    - text: Launch brief 1788698084377-529494144
                  - textbox "Task description" [ref=e92]:
                    - /placeholder: Description
                    - text: Prepare the launch review
                  - generic [ref=e93]:
                    - 'generic "Location: Atlas 1788698084377-529494144" [ref=e94]':
                      - generic [ref=e95]: folder_open
                      - generic [ref=e96]: Atlas 1788698084377-529494144
                    - generic [ref=e98]:
                      - button "Due" [ref=e99]:
                        - generic [ref=e100]: calendar_today
                        - generic [ref=e101]: Today
                      - button "Clear" [ref=e102]: ×
                    - generic [ref=e104]:
                      - button "Urgency" [ref=e105]:
                        - generic [ref=e106]: bolt
                        - generic [ref=e107]: High
                      - button "Clear" [ref=e108]: ×
                    - generic [ref=e110]:
                      - button "Impact" [ref=e111]:
                        - generic [ref=e112]: flag
                        - generic [ref=e113]: Medium
                      - button "Clear" [ref=e114]: ×
                    - button "Type" [ref=e117]:
                      - generic [ref=e118]: sell
                      - generic [ref=e119]: Type
                  - textbox "New task deadline date" [ref=e120]: 2026-09-06
                  - generic [ref=e121]:
                    - button "Cancel" [disabled] [ref=e122]:
                      - generic [ref=e123]: close
                    - button "Add task" [disabled] [ref=e124]:
                      - generic [ref=e125]: arrow_upward
                - button "Move Launch brief 1788698084377-529494144" [ref=e127]:
                  - generic [ref=e128]:
                    - heading "Launch brief 1788698084377-529494144" [level=3] [ref=e130]
                    - generic [ref=e132]: drag_indicator
                  - generic [ref=e134]:
                    - generic [ref=e135]: Atlas 1788698084377-529494144
                    - generic [ref=e136]: U · high
                    - generic [ref=e137]: I · medium
                  - generic [ref=e138]:
                    - generic [ref=e139]:
                      - generic [ref=e140]: calendar_today
                      - generic [ref=e141]: Today
                    - generic [ref=e142]:
                      - button "Add Launch brief 1788698084377-529494144 to Ready to Plan" [ref=e143]:
                        - generic [ref=e144]: event_upcoming
                        - text: Plan
                      - button "Add a subtask to Launch brief 1788698084377-529494144" [ref=e145]:
                        - generic [ref=e146]: account_tree
                        - text: 0/0
                  - button "Complete Task" [ref=e147]
              - region "In progress tasks" [ref=e148]:
                - generic [ref=e149]:
                  - heading "In progress" [level=2] [ref=e150]
                  - generic [ref=e151]: "0"
                - button "Add In progress task" [ref=e152]:
                  - generic [ref=e153]: add
                  - text: Add task
              - region "Blocked tasks" [ref=e154]:
                - generic [ref=e155]:
                  - heading "Blocked" [level=2] [ref=e156]
                  - generic [ref=e157]: "0"
                - button "Add Blocked task" [ref=e158]:
                  - generic [ref=e159]: add
                  - text: Add task
              - region "Completed tasks" [ref=e160]:
                - generic [ref=e161]:
                  - heading "Completed" [level=2] [ref=e162]
                  - generic [ref=e163]: "0"
                - button "Add Completed task" [ref=e164]:
                  - generic [ref=e165]: add
                  - text: Add task
  - status [ref=e166]
  - status [ref=e167]
```

# Test source

```ts
  726 | test('names a standalone Actual Block without choosing a Task Type and reloads edits', async ({ page, request }) => {
  727 |   const date = '2026-06-29'
  728 |   const base = apiBase
  729 |   await page.goto(`/day/${date}`)
  730 |   await expect(page.getByTestId('day-date')).toHaveText(date, { timeout: 30_000 })
  731 |   await clearDayBlocks(request, base, date)
  732 |   await page.reload()
  733 | 
  734 |   const actualLane = page.getByTestId('day-timeline').locator('[role="presentation"]').nth(1)
  735 |   await actualLane.scrollIntoViewIfNeeded()
  736 |   const box = await actualLane.boundingBox()
  737 |   expect(box).toBeTruthy()
  738 |   const laneRelY = TIMELINE_SLOT_HEIGHT_PX * 6 + TIMELINE_SLOT_HEIGHT_PX * 0.5
  739 |   await page.mouse.click(box!.x + box!.width / 2, box!.y + laneRelY)
  740 | 
  741 |   const inspector = page.getByRole('complementary', { name: 'Block details' })
  742 |   await inspector.getByLabel('Name').fill('  Evening   walk  ')
  743 |   await inspector.getByLabel('Note').fill('Took the river path')
  744 |   await inspector.getByRole('button', { name: 'Create block' }).click()
  745 | 
  746 |   let actualId = 0
  747 |   await expect.poll(async () => {
  748 |     const response = await request.get(`${base}/days/${date}`)
  749 |     const body = (await response.json()) as {
  750 |       actual_blocks: Array<{ actual_block: { id: number; name: string | null; note: string | null; task_type: { name: string } } }>
  751 |     }
  752 |     const actual = body.actual_blocks[0]?.actual_block
  753 |     actualId = actual?.id ?? 0
  754 |     return actual ? { name: actual.name, note: actual.note, type: actual.task_type.name } : null
  755 |   }).toEqual({ name: 'Evening   walk', note: 'Took the river path', type: 'unspecified' })
  756 | 
  757 |   await expect(page.locator(`[data-block-id="${actualId}"]`).getByText('Evening   walk')).toBeVisible()
  758 |   await page.locator(`[data-block-id="${actualId}"]`).click()
  759 |   await inspector.getByLabel('Name').fill('Walk home')
  760 |   await inspector.getByLabel('Name').blur()
  761 |   await expect.poll(async () => {
  762 |     const response = await request.get(`${base}/actual-blocks/${actualId}`)
  763 |     return ((await response.json()) as { name: string | null }).name
  764 |   }).toBe('Walk home')
  765 | 
  766 |   await page.reload()
  767 |   await expect(page.locator(`[data-block-id="${actualId}"]`).getByText('Walk home')).toBeVisible()
  768 |   await page.locator(`[data-block-id="${actualId}"]`).click()
  769 |   await inspector.getByLabel('Name').fill('   ')
  770 |   await inspector.getByLabel('Name').blur()
  771 |   await expect.poll(async () => {
  772 |     const response = await request.get(`${base}/actual-blocks/${actualId}`)
  773 |     return ((await response.json()) as { name: string | null }).name ?? 'missing'
  774 |   }).toBe('missing')
  775 | 
  776 |   await page.reload()
  777 |   await expect(page.locator(`[data-block-id="${actualId}"]`).getByText('Untitled')).toBeVisible()
  778 |   await expect(page.getByText('unspecified')).toHaveCount(0)
  779 | })
  780 | 
  781 | test('draft cleared when clicking outside the timeline', async ({ page }) => {
  782 |   const date = '2026-06-21'
  783 |   await page.goto(`/day/${date}`)
  784 |   await expect(page.getByTestId('day-date')).toHaveText(date, { timeout: 30_000 })
  785 |   await expect(page.getByTestId('day-timeline')).toBeVisible()
  786 | 
  787 |   const plannedLane = page.getByTestId('day-timeline').locator('[role="presentation"]').first()
  788 |   await plannedLane.scrollIntoViewIfNeeded()
  789 |   const box = await plannedLane.boundingBox()
  790 |   expect(box).toBeTruthy()
  791 |   const laneRelY = TIMELINE_SLOT_HEIGHT_PX * 4 + TIMELINE_SLOT_HEIGHT_PX * 0.5
  792 |   await page.mouse.click(box!.x + box!.width / 2, box!.y + laneRelY)
  793 | 
  794 |   await expect(page.getByTestId('draft-block')).toBeVisible()
  795 |   await page.locator('main h1').first().click()
  796 |   await expect(page.getByTestId('draft-block')).toHaveCount(0)
  797 | })
  798 | 
  799 | test('Battle Plan creates a dated project task, persists subtask progress, trashes, and restores it', async ({ page, request }) => {
  800 |   const base = apiBase
  801 |   const uniq = `${Date.now()}-${Math.floor(Math.random() * 1e9)}`
  802 |   const projectName = `Atlas ${uniq}`
  803 |   const taskTitle = `Launch brief ${uniq}`
  804 | 
  805 |   const projectResponse = await request.post(`${base}/projects`, {
  806 |     data: { name: projectName, description: 'E2E project' },
  807 |   })
  808 |   expect(projectResponse.ok()).toBeTruthy()
  809 | 
  810 |   await page.goto('/battle-plan')
  811 |   await page.getByRole('button', { name: projectName, exact: true }).click()
  812 |   const open = page.getByRole('region', { name: 'Open tasks' })
  813 |   await open.getByRole('button', { name: 'Add Open task' }).click()
  814 |   const composer = open.getByRole('form', { name: 'New task' })
  815 |   await composer.getByLabel('Task title').fill(taskTitle)
  816 |   await composer.getByLabel('Task description').fill('Prepare the launch review')
  817 |   await composer.getByRole('button', { name: 'Urgency' }).click()
  818 |   await composer.getByRole('menuitemradio', { name: 'High' }).click()
  819 |   await composer.getByRole('button', { name: 'Impact' }).click()
  820 |   await composer.getByRole('menuitemradio', { name: 'Medium' }).click()
  821 |   await composer.getByRole('button', { name: 'Due' }).click()
  822 |   await composer.getByRole('menuitemradio', { name: 'Today' }).click()
  823 |   await composer.getByRole('button', { name: 'Add task', exact: true }).click()
  824 | 
  825 |   await expect(page.getByText(taskTitle, { exact: true })).toBeVisible()
> 826 |   await expect(open.getByText('Today', { exact: true })).toBeVisible()
      |                                                          ^ Error: expect(locator).toBeVisible() failed
  827 | 
  828 |   await page.getByRole('button', { name: `Add a subtask to ${taskTitle}` }).click()
  829 |   await page.getByLabel(`New subtask for ${taskTitle}`).fill('Review sources')
  830 |   await page.getByLabel(`Add subtask to ${taskTitle}`).click()
  831 |   await expect(page.getByText('Review sources', { exact: true })).toBeVisible()
  832 |   await page.getByLabel('Check subtask Review sources').click()
  833 |   await expect(page.getByRole('button', { name: `1 of 1 subtasks completed for ${taskTitle}` })).toBeVisible()
  834 | 
  835 |   await page.reload()
  836 |   await expect(page.getByRole('button', { name: `1 of 1 subtasks completed for ${taskTitle}` })).toBeVisible()
  837 | 
  838 |   const taskCard = page.locator('article[data-task-id]').filter({ hasText: taskTitle }).first()
  839 |   await taskCard.click()
  840 |   page.once('dialog', (dialog) => void dialog.accept())
  841 |   await page.getByRole('button', { name: 'Move to Trash' }).click()
  842 |   await expect(page.getByText('Moved to Trash')).toBeVisible()
  843 |   await page.getByRole('button', { name: 'Undo', exact: true }).click()
  844 |   await expect(page.getByText(taskTitle, { exact: true })).toBeVisible()
  845 | })
  846 | 
```