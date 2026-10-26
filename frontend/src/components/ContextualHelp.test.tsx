import { it, expect } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { ContextualHelp } from './ContextualHelp'

it('starts hidden, opens by keyboard and closes without changing an editor draft', async () => {
  const user = userEvent.setup()
  function Editor() {
    const [draft, setDraft] = useState('Original')
    return <form onSubmit={() => { throw new Error('Help must not submit') }}>
      <input aria-label="Draft" value={draft} onChange={event => setDraft(event.target.value)} />
      <ContextualHelp title="Editing">Optional instructions</ContextualHelp>
    </form>
  }
  render(<Editor />)
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  await user.clear(screen.getByLabelText('Draft'))
  await user.type(screen.getByLabelText('Draft'), 'Unsaved')
  const trigger = screen.getByRole('button', { name: 'Help: Editing' })
  trigger.focus()
  await user.keyboard('{Enter}')
  expect(screen.getByRole('dialog', { name: 'Editing' })).toBeVisible()
  expect(screen.getByRole('button', { name: 'Close help: Editing' })).toHaveFocus()
  fireEvent(screen.getByRole('dialog'), new Event('cancel', { cancelable: true }))
  expect(trigger).toHaveFocus()
  expect(screen.getByLabelText('Draft')).toHaveValue('Unsaved')
})

it('replaces an existing help panel and supports explicit dismissal', async () => {
  const user = userEvent.setup()
  render(<><ContextualHelp title="First">First rules</ContextualHelp><ContextualHelp title="Second">Second rules</ContextualHelp></>)
  await user.click(screen.getByRole('button', { name: 'Help: First' }))
  await user.click(screen.getByRole('button', { name: 'Help: Second' }))
  expect(screen.getAllByRole('dialog')).toHaveLength(1)
  expect(screen.getByRole('dialog', { name: 'Second' })).toBeVisible()
  await user.click(screen.getByRole('button', { name: 'Close help: Second' }))
  expect(screen.getByRole('button', { name: 'Help: Second' })).toHaveFocus()
})
