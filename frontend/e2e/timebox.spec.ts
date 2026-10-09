import { expect, test } from '@playwright/test'

const apiBase = 'http://127.0.0.1:18001'

test('Battle Plan creates a dated project task, persists subtask progress, trashes, and restores it', async ({ page, request }) => {
  const base = apiBase
  const uniq = `${Date.now()}-${Math.floor(Math.random() * 1e9)}`
  const projectName = `Atlas ${uniq}`
  const taskTitle = `Launch brief ${uniq}`

  const projectResponse = await request.post(`${base}/projects`, {
    data: { name: projectName },
  })
  expect(projectResponse.ok()).toBeTruthy()

  await page.goto('/battle-plan')
  await page.getByRole('button', { name: projectName, exact: true }).click()
  const open = page.getByRole('region', { name: 'Open tasks' })
  await open.getByRole('button', { name: 'Add Open task' }).click()
  const composer = open.getByRole('form', { name: 'New task' })
  await composer.getByLabel('Task title').fill(taskTitle)
  await composer.getByLabel('Task description').fill('Prepare the launch review')
  await composer.getByRole('button', { name: 'Urgency' }).click()
  await composer.getByRole('menuitemradio', { name: 'High' }).click()
  await composer.getByRole('button', { name: 'Impact' }).click()
  await composer.getByRole('menuitemradio', { name: 'Medium' }).click()
  await composer.getByRole('button', { name: 'Due' }).click()
  await composer.getByRole('menuitemradio', { name: 'Today' }).click()
  await composer.getByRole('button', { name: 'Add task', exact: true }).click()

  await expect(page.getByText(taskTitle, { exact: true })).toBeVisible()
  await expect(open.getByText('Today', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: `Add a subtask to ${taskTitle}` }).click()
  await page.getByLabel(`New subtask for ${taskTitle}`).fill('Review sources')
  await page.getByLabel(`Add subtask to ${taskTitle}`).click()
  await expect(page.getByText('Review sources', { exact: true })).toBeVisible()
  await page.getByLabel('Check subtask Review sources').click()
  await expect(page.getByRole('button', { name: `1 of 1 subtasks completed for ${taskTitle}` })).toBeVisible()

  await page.reload()
  await expect(page.getByRole('button', { name: `1 of 1 subtasks completed for ${taskTitle}` })).toBeVisible()

  const taskCard = page.locator('article[data-task-id]').filter({ hasText: taskTitle }).first()
  await taskCard.click()
  page.once('dialog', (dialog) => void dialog.accept())
  await page.getByRole('button', { name: 'Move to Trash' }).click()
  await expect(page.getByText('Moved to Trash')).toBeVisible()
  await page.getByRole('button', { name: 'Undo', exact: true }).click()
  await expect(page.getByText(taskTitle, { exact: true })).toBeVisible()
})
