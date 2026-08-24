import { fetchJson, fetchVoid } from './client'
import type {
  BattleTask,
  BattleTaskList,
  BattleTaskWrite,
  BlockLane,
  DayListItem,
  DayRead,
  DueReminder,
  HealthResponse,
  Project,
  ProjectWrite,
  RecurrencePreview,
  RecurrenceRuleWrite,
  RecurrenceStatus,
  RecurringTemplate,
  RecurringTemplateWrite,
  SettingsRead,
  TaskCollection,
  TaskCompletionResult,
  TaskStatus,
  TaskType,
} from './types'

export const api = {
  health: () => fetchJson<HealthResponse>('/health'),

  getDay: (date: string) => fetchJson<DayRead>(`/days/${date}`),

  getSettings: () => fetchJson<SettingsRead>('/settings'),

  patchSettings: (body: Partial<{ start_hour: number; end_hour: number; show_full_day: boolean; week_start: 'monday' | 'sunday' }>) =>
    fetchJson<SettingsRead>('/settings', {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),

  listTaskTypes: () => fetchJson<TaskType[]>('/task-types'),

  createTaskType: (body: { name: string }) =>
    fetchJson<TaskType>('/task-types', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  patchTaskType: (id: number, body: Partial<{ name: string }>) =>
    fetchJson<TaskType>(`/task-types/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),

  deleteTaskType: (
    id: number,
    opts?: { cascadeBlocks?: boolean; migrateBlocksTo?: number; clearTaskReferences?: boolean },
  ) => {
    const params = new URLSearchParams()
    if (opts?.cascadeBlocks) params.set('cascade_blocks', 'true')
    if (opts?.migrateBlocksTo != null) params.set('migrate_blocks_to', String(opts.migrateBlocksTo))
    if (opts?.clearTaskReferences) params.set('clear_task_references', 'true')
    const qs = params.toString()
    return fetchVoid(`/task-types/${id}${qs ? `?${qs}` : ''}`, {
      method: 'DELETE',
    })
  },

  createBlock: (
    date: string,
    body: {
      lane: BlockLane
      task_type_id: number
      task_id?: number | null
      note?: string | null
      start_minute: number
      end_minute: number
    },
  ) =>
    fetchJson<DayRead>(`/days/${date}/blocks`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  patchBlock: (
    date: string,
    blockId: number,
    body: Partial<{ task_type_id: number; task_id: number | null; note: string | null; start_minute: number; end_minute: number }>,
  ) =>
    fetchJson<DayRead>(`/days/${date}/blocks/${blockId}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),

  deleteBlock: (date: string, blockId: number) =>
    fetchJson<DayRead>(`/days/${date}/blocks/${blockId}`, {
      method: 'DELETE',
    }),

  completeBlockAsPlanned: (date: string, blockId: number) =>
    fetchJson<DayRead>(`/days/${date}/blocks/${blockId}/complete-as-planned`, {
      method: 'POST',
    }),

  reverseBlockCompletion: (date: string, blockId: number) =>
    fetchJson<DayRead>(`/days/${date}/blocks/${blockId}/completion`, {
      method: 'DELETE',
    }),

  listDays: (limit = 60) => fetchJson<DayListItem[]>(`/days?limit=${limit}`),

  listProjects: () => fetchJson<Project[]>('/projects'),

  createProject: (body: ProjectWrite) =>
    fetchJson<Project>('/projects', { method: 'POST', body: JSON.stringify(body) }),

  patchProject: (id: number, body: Partial<ProjectWrite>) =>
    fetchJson<Project>(`/projects/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),

  deleteProject: (id: number) => fetchVoid(`/projects/${id}`, { method: 'DELETE' }),

  listBattleTasks: (state: TaskCollection = 'active') =>
    fetchJson<BattleTaskList>(`/tasks?state=${state}`),

  createBattleTask: (body: BattleTaskWrite) =>
    fetchJson<BattleTask>('/tasks', { method: 'POST', body: JSON.stringify(body) }),

  patchBattleTask: (id: number, body: Partial<BattleTaskWrite>) =>
    fetchJson<BattleTask>(`/tasks/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),

  completeBattleTask: (id: number, plannedTime: 'keep' | 'remove') =>
    fetchJson<TaskCompletionResult>(`/tasks/${id}/complete`, {
      method: 'POST',
      body: JSON.stringify({ planned_time: plannedTime }),
    }),

  reopenBattleTask: (id: number) =>
    fetchJson<BattleTask>(`/tasks/${id}/reopen`, { method: 'POST' }),

  undoBattleTaskCompletion: (id: number, undoToken: string) =>
    fetchJson<BattleTask>(`/tasks/${id}/undo-completion`, {
      method: 'POST',
      body: JSON.stringify({ undo_token: undoToken }),
    }),

  reorderBattleTasks: (
    placements: Array<{ task_id: number; status: TaskStatus; position: number }>,
  ) =>
    fetchVoid('/tasks/reorder', {
      method: 'POST',
      body: JSON.stringify({ placements }),
    }),

  archiveBattleTasks: (taskIds: number[]) =>
    fetchVoid('/tasks/archive-completed', {
      method: 'POST',
      body: JSON.stringify({ task_ids: taskIds }),
    }),

  unarchiveBattleTask: (id: number) =>
    fetchVoid(`/tasks/${id}/unarchive`, { method: 'POST' }),

  trashBattleTask: (id: number) =>
    fetchJson<BattleTask>(`/tasks/${id}`, { method: 'DELETE' }),

  restoreBattleTask: (id: number) =>
    fetchVoid(`/tasks/${id}/restore`, { method: 'POST' }),

  permanentlyDeleteBattleTask: (id: number) =>
    fetchVoid(`/tasks/${id}/permanent`, { method: 'DELETE' }),

  dueReminders: () => fetchJson<DueReminder[]>('/reminders/due'),

  acknowledgeReminder: (id: number) =>
    fetchVoid(`/reminders/${id}/delivered`, { method: 'POST' }),

  previewRecurrence: (body: RecurrenceRuleWrite) =>
    fetchJson<RecurrencePreview>('/recurring-templates/preview', {
      method: 'POST', body: JSON.stringify(body),
    }),

  listRecurringTemplates: (status: RecurrenceStatus = 'active') =>
    fetchJson<RecurringTemplate[]>(`/recurring-templates?status=${status}`),

  getRecurringTemplate: (id: number) =>
    fetchJson<RecurringTemplate>(`/recurring-templates/${id}`),

  createRecurringTemplate: (body: RecurringTemplateWrite) =>
    fetchJson<RecurringTemplate>('/recurring-templates', {
      method: 'POST', body: JSON.stringify(body),
    }),

  patchRecurringTemplate: (id: number, body: Partial<RecurringTemplateWrite>) =>
    fetchJson<RecurringTemplate>(`/recurring-templates/${id}`, {
      method: 'PATCH', body: JSON.stringify(body),
    }),

  pauseRecurringTemplate: (id: number) =>
    fetchJson<RecurringTemplate>(`/recurring-templates/${id}/pause`, { method: 'POST' }),

  resumeRecurringTemplate: (id: number) =>
    fetchJson<RecurringTemplate>(`/recurring-templates/${id}/resume`, { method: 'POST' }),

  endRecurringTemplate: (id: number) =>
    fetchJson<RecurringTemplate>(`/recurring-templates/${id}/end`, { method: 'POST' }),

  deleteRecurringTemplate: (id: number) =>
    fetchVoid(`/recurring-templates/${id}`, { method: 'DELETE' }),
}
