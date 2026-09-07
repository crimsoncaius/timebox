import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { BattlePlanSidebar } from './BattlePlanSidebar'
import type { Project } from '../../lib/api'

const projects: Project[] = ['Alpha', 'Beta'].map((name, index) => ({ id: index + 1, name, created_at: '', updated_at: '' }))
function setup(onReorderProjects = vi.fn().mockResolvedValue(undefined)) {
  const onEditProject = vi.fn()
  render(<MemoryRouter><BattlePlanSidebar open collection="active" scope="all" projects={projects} onClose={vi.fn()} onScope={vi.fn()} onCollection={vi.fn()} onNewProject={vi.fn()} onEditProject={onEditProject} onReorderProjects={onReorderProjects} /></MemoryRouter>)
  return { user: userEvent.setup(), onEditProject, onReorderProjects }
}

describe('Project ordering', () => {
  it('supports menu moves, boundaries, and editing without moving fixed navigation', async () => {
    const { user, onReorderProjects, onEditProject } = setup()
    await user.click(screen.getByRole('button', { name: 'More actions for Alpha' }))
    expect(screen.getByRole('button', { name: 'Move up' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Move down' }))
    await waitFor(() => expect(onReorderProjects).toHaveBeenCalledWith([2, 1]))
    expect(within(screen.getByRole('list', { name: 'Projects' })).queryByText('All Tasks')).toBeNull()
    await user.click(screen.getByRole('button', { name: 'Edit Alpha' }))
    expect(onEditProject).toHaveBeenCalledWith(projects[0])
  })

  it('reports persistence failure and leaves the saved order visible', async () => {
    const { user } = setup(vi.fn().mockRejectedValue(new Error('Offline')))
    await user.click(screen.getByRole('button', { name: 'More actions for Alpha' }))
    await user.click(screen.getByRole('button', { name: 'Move down' }))
    expect(await screen.findByText('Could not save project order. Refresh and try again.')).toBeVisible()
    const rows = within(screen.getByRole('list', { name: 'Projects' })).getAllByRole('listitem')
    expect(rows[0]).toHaveTextContent('Alpha')
    expect(rows[1]).toHaveTextContent('Beta')
  })
})
