import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ProjectEditor } from './ProjectEditor'

describe('ProjectEditor', () => {
  const originalConfirm = window.confirm

  it('creates a project using only its name', async () => {
    const user = userEvent.setup()
    const onSave = vi.fn().mockResolvedValue(undefined)
    render(<ProjectEditor project={null} taskCount={0} onSave={onSave} onDelete={null} onClose={vi.fn()} />)
    expect(screen.queryByLabelText('Description')).not.toBeInTheDocument()
    expect(screen.queryByText('Deadline')).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('Name'), '  Launch  ')
    await user.click(screen.getByRole('button', { name: 'Save project' }))
    expect(onSave).toHaveBeenCalledWith({ name: 'Launch' })
  })

  afterEach(() => {
    window.confirm = originalConfirm
    vi.restoreAllMocks()
  })

  it('keeps a changed project open when close dismissal is rejected', async () => {
    window.confirm = vi.fn(() => false)
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(
      <ProjectEditor
        project={null}
        taskCount={0}
        onSave={vi.fn()}
        onDelete={null}
        onClose={onClose}
      />,
    )

    const editor = screen.getByRole('heading', { name: 'New project' }).closest('section')
    expect(editor).not.toBeNull()
    await user.type(within(editor!).getByLabelText('Name'), 'Unsaved project')
    await user.click(within(editor!).getByRole('button', { name: 'Close project editor' }))

    expect(screen.getByRole('heading', { name: 'New project' })).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
    expect(window.confirm).toHaveBeenCalledWith('Discard your unsaved changes?')
    expect(window.confirm).toHaveBeenCalledTimes(1)
  })

  it('closes a pristine project from the backdrop without prompting', async () => {
    window.confirm = vi.fn(() => false)
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(
      <ProjectEditor
        project={null}
        taskCount={0}
        onSave={vi.fn()}
        onDelete={null}
        onClose={onClose}
      />,
    )

    const editor = screen.getByRole('heading', { name: 'New project' }).closest('section')
    expect(editor?.parentElement).not.toBeNull()
    await user.click(editor!.parentElement!)

    expect(onClose).toHaveBeenCalledTimes(1)
    expect(window.confirm).not.toHaveBeenCalled()
  })

  it('allows Save to close a changed project without a discard prompt', async () => {
    window.confirm = vi.fn(() => false)
    const user = userEvent.setup()

    function SaveClosingProjectEditor() {
      const [open, setOpen] = useState(true)
      return open ? (
        <ProjectEditor
          project={null}
          taskCount={0}
          onSave={async () => setOpen(false)}
          onDelete={null}
          onClose={() => setOpen(false)}
        />
      ) : <p>Project editor closed</p>
    }

    render(<SaveClosingProjectEditor />)
    const editor = screen.getByRole('heading', { name: 'New project' }).closest('section')
    expect(editor).not.toBeNull()
    await user.type(within(editor!).getByLabelText('Name'), 'Saved project')
    await user.click(within(editor!).getByRole('button', { name: 'Save project' }))

    expect(await screen.findByText('Project editor closed')).toBeInTheDocument()
    expect(window.confirm).not.toHaveBeenCalled()
  })
})
