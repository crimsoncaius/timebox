import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { SubtaskTitle } from './SubtaskTitle'

function setup(onRename = vi.fn<(id: number, title: string) => Promise<void>>().mockResolvedValue(undefined)) {
  const view = render(<SubtaskTitle id={21} title="Check figures" disabled={false} className="" onRename={onRename} />)
  return { ...view, onRename, user: userEvent.setup() }
}

describe('SubtaskTitle', () => {
  it('validates trimmed titles and keeps the draft when focus leaves', async () => {
    const { user, onRename } = setup()
    await user.click(screen.getByRole('button', { name: 'Rename subtask Check figures' }))
    const input = screen.getByRole('textbox')
    expect(input).toHaveFocus()
    const save = screen.getByRole('button', { name: 'Save subtask' })
    for (const value of ['   ', ' Check figures ', 'x'.repeat(501)]) {
      fireEvent.change(input, { target: { value } })
      expect(save).toBeDisabled()
      fireEvent.submit(input.closest('form')!)
    }
    expect(screen.getByRole('alert')).toHaveTextContent('500 characters')
    fireEvent.change(input, { target: { value: 'x'.repeat(500) } })
    expect(save).toBeEnabled()
    await user.tab()
    expect(input).toHaveValue('x'.repeat(500))
    expect(onRename).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  })

  it('cancels with Escape without bubbling to the detail panel', async () => {
    const { user, onRename } = setup()
    const listener = vi.fn()
    document.addEventListener('keydown', listener)
    await user.click(screen.getByRole('button'))
    await user.keyboard('{Escape}')
    document.removeEventListener('keydown', listener)
    expect(listener).not.toHaveBeenCalled()
    expect(onRename).not.toHaveBeenCalled()
    await waitFor(() => expect(screen.getByRole('button')).toHaveFocus())
  })

  it('waits for the server, retains a failed draft, and retries', async () => {
    let reject!: (cause: Error) => void
    const onRename = vi.fn<(id: number, title: string) => Promise<void>>()
      .mockImplementationOnce(() => new Promise((_, fail) => { reject = fail }))
      .mockResolvedValue(undefined)
    const { user } = setup(onRename)
    await user.click(screen.getByRole('button'))
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '  Verify totals  ' } })
    await user.keyboard('{Enter}')
    expect(screen.getByRole('textbox')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Saving…' })).toBeDisabled()
    fireEvent.submit(screen.getByRole('form'))
    expect(onRename).toHaveBeenCalledTimes(1)
    await act(async () => reject(new Error('Server unavailable')))
    expect(screen.getByRole('alert')).toHaveTextContent('Server unavailable')
    expect(screen.getByRole('textbox')).toHaveValue('  Verify totals  ')
    await user.click(screen.getByRole('button', { name: 'Save subtask' }))
    await waitFor(() => expect(screen.queryByRole('textbox')).not.toBeInTheDocument())
    expect(onRename).toHaveBeenLastCalledWith(21, 'Verify totals')
  })

  it('prevents renaming when the parent becomes completed', async () => {
    const { user, rerender, onRename } = setup()
    await user.click(screen.getByRole('button'))
    rerender(<SubtaskTitle id={21} title="Check figures" disabled className="" onRename={onRename} />)
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.getByRole('button')).toBeDisabled()
    expect(onRename).not.toHaveBeenCalled()
  })
})
