# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: timebox.spec.ts >> derived Actual snapshots the Planned name and stays independently editable through Chronicle and reload
- Location: ..\artifacts\qa-fixes-2026-09-06\baseline-ebf691e\frontend\e2e\timebox.spec.ts:649:1

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: locator('[data-block-id="1"]').getByText('Revised plan')
Expected: visible
Timeout: 5000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 5000ms
  - waiting for locator('[data-block-id="1"]').getByText('Revised plan')

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
      - paragraph [ref=e38]: Loading…
```

# Test source

```ts
  618 |     blockId = block?.id ?? 0
  619 |     return block ? { name: block.name, note: block.note, type: block.task_type.name } : null
  620 |   }).toEqual({ name: 'Dinner   with Alex', note: 'Bring invitation', type: 'unspecified' })
  621 | 
  622 |   await expect(page.locator(`[data-block-id="${blockId}"]`).getByText('Dinner   with Alex')).toBeVisible()
  623 |   await page.locator(`[data-block-id="${blockId}"]`).click()
  624 |   await expect(inspector.getByLabel('Name')).toHaveValue('Dinner   with Alex')
  625 |   await inspector.getByLabel('Name').fill('Dinner with Sam')
  626 |   await inspector.getByLabel('Name').blur()
  627 |   await expect.poll(async () => {
  628 |     const response = await request.get(`${base}/days/${date}`)
  629 |     const body = (await response.json()) as { time_blocks: Array<{ id: number; name: string | null }> }
  630 |     return body.time_blocks.find((block) => block.id === blockId)?.name
  631 |   }).toBe('Dinner with Sam')
  632 | 
  633 |   await page.reload()
  634 |   await expect(page.locator(`[data-block-id="${blockId}"]`).getByText('Dinner with Sam')).toBeVisible()
  635 |   await page.locator(`[data-block-id="${blockId}"]`).click()
  636 |   await inspector.getByLabel('Name').fill('   ')
  637 |   await inspector.getByLabel('Name').blur()
  638 |   await expect.poll(async () => {
  639 |     const response = await request.get(`${base}/days/${date}`)
  640 |     const body = (await response.json()) as { time_blocks: Array<{ id: number; name: string | null }> }
  641 |     return body.time_blocks.find((block) => block.id === blockId)?.name ?? 'missing'
  642 |   }).toBe('missing')
  643 | 
  644 |   await page.reload()
  645 |   await expect(page.locator(`[data-block-id="${blockId}"]`).getByText('Untitled')).toBeVisible()
  646 |   await expect(page.getByText('unspecified')).toHaveCount(0)
  647 | })
  648 | 
  649 | test('derived Actual snapshots the Planned name and stays independently editable through Chronicle and reload', async ({ page, request }) => {
  650 |   const date = '2199-12-30'
  651 |   const base = apiBase
  652 |   await request.patch(`${base}/settings`, {
  653 |     data: { start_hour: 8, end_hour: 20, show_full_day: false },
  654 |   })
  655 |   await page.goto(`/day/${date}`)
  656 |   await expect(page.getByTestId('day-date')).toHaveText(date, { timeout: 30_000 })
  657 |   await clearDayBlocks(request, base, date)
  658 |   await page.reload()
  659 | 
  660 |   const plannedLane = page.getByTestId('day-timeline').locator('[role="presentation"]').first()
  661 |   await plannedLane.scrollIntoViewIfNeeded()
  662 |   const laneBox = await plannedLane.boundingBox()
  663 |   expect(laneBox).toBeTruthy()
  664 |   const laneRelY = TIMELINE_SLOT_HEIGHT_PX * 6 + TIMELINE_SLOT_HEIGHT_PX * 0.5
  665 |   await page.mouse.click(laneBox!.x + laneBox!.width / 2, laneBox!.y + laneRelY)
  666 | 
  667 |   const inspector = page.getByRole('complementary', { name: 'Block details' })
  668 |   await inspector.getByLabel('Name').fill('Planned dinner')
  669 |   await inspector.getByLabel('Note').fill('Original plan detail')
  670 |   await inspector.getByRole('button', { name: 'Create block' }).click()
  671 | 
  672 |   let plannedId = 0
  673 |   await expect.poll(async () => {
  674 |     const body = (await (await request.get(`${base}/days/${date}`)).json()) as {
  675 |       time_blocks: Array<{ id: number; name: string | null }>
  676 |     }
  677 |     plannedId = body.time_blocks[0]?.id ?? 0
  678 |     return body.time_blocks[0]?.name
  679 |   }).toBe('Planned dinner')
  680 | 
  681 |   await page.locator(`[data-block-id="${plannedId}"]`).click()
  682 |   await inspector.getByRole('button', { name: 'Record Actual as planned' }).click()
  683 | 
  684 |   let actualId = 0
  685 |   await expect.poll(async () => {
  686 |     const body = (await (await request.get(`${base}/days/${date}`)).json()) as {
  687 |       actual_blocks: Array<{ actual_block: { id: number; name: string | null; planned_block_id: number | null } }>
  688 |     }
  689 |     const actual = body.actual_blocks[0]?.actual_block
  690 |     actualId = actual?.id ?? 0
  691 |     return actual ? { name: actual.name, plannedBlockId: actual.planned_block_id } : null
  692 |   }).toEqual({ name: 'Planned dinner', plannedBlockId: plannedId })
  693 | 
  694 |   await page.locator(`[data-block-id="${plannedId}"]`).click()
  695 |   await inspector.getByLabel('Name').fill('Revised plan')
  696 |   await inspector.getByLabel('Name').blur()
  697 |   await expect.poll(async () => {
  698 |     const body = (await (await request.get(`${base}/days/${date}`)).json()) as {
  699 |       time_blocks: Array<{ id: number; name: string | null }>
  700 |     }
  701 |     return body.time_blocks.find((block) => block.id === plannedId)?.name
  702 |   }).toBe('Revised plan')
  703 |   expect(((await (await request.get(`${base}/actual-blocks/${actualId}`)).json()) as { name: string | null }).name)
  704 |     .toBe('Planned dinner')
  705 | 
  706 |   await page.locator(`[data-block-id="${actualId}"]`).click()
  707 |   await inspector.getByLabel('Name').fill('Dinner that happened')
  708 |   await inspector.getByLabel('Name').blur()
  709 |   await expect.poll(async () =>
  710 |     ((await (await request.get(`${base}/actual-blocks/${actualId}`)).json()) as { name: string | null }).name,
  711 |   ).toBe('Dinner that happened')
  712 |   const persistedPlan = (await (await request.get(`${base}/days/${date}`)).json()) as {
  713 |     time_blocks: Array<{ id: number; name: string | null }>
  714 |   }
  715 |   expect(persistedPlan.time_blocks.find((block) => block.id === plannedId)?.name).toBe('Revised plan')
  716 | 
  717 |   await page.reload()
> 718 |   await expect(page.locator(`[data-block-id="${plannedId}"]`).getByText('Revised plan')).toBeVisible()
      |                                                                                          ^ Error: expect(locator).toBeVisible() failed
  719 |   await expect(page.locator(`[data-block-id="${actualId}"]`).getByText('Dinner that happened')).toBeVisible()
  720 | 
  721 |   await page.getByRole('link', { name: 'Chronicle' }).click()
  722 |   await expect(page.getByTestId(`chronicle-day-${date}`).getByText('Dinner that happened')).toBeVisible()
  723 |   await expect(page.getByTestId(`chronicle-day-${date}`).getByText('Planned dinner')).toHaveCount(0)
  724 | })
  725 | 
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
```