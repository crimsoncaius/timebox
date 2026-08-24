export type BlockLane = 'planned' | 'actual'

/** Client-only placement before POST /days/.../blocks (draft-first creation). */
export type BlockDraftPlacement = {
  lane: BlockLane
  start_minute: number
  end_minute: number
  task_id?: number | null
  task_type_id?: number | null
}

export interface TaskType {
  id: number
  name: string
  created_at: string
  updated_at: string
  usage_count?: number
  task_usage_count?: number
}

export type TaskStatus = 'open' | 'in_progress' | 'blocked' | 'completed'
export type PriorityLevel = 'low' | 'medium' | 'high'
export type TaskCollection = 'active' | 'archived' | 'trash'
export type RecurrenceMode = 'scheduled' | 'quota'
export type RecurrenceStatus = 'active' | 'paused' | 'ended'
export type RecurrenceFrequency = 'daily' | 'weekly' | 'monthly'

export interface Project {
  id: number
  name: string
  description: string
  deadline_date: string | null
  deadline_at: string | null
  created_at: string
  updated_at: string
}

export interface BattleTask {
  id: number
  parent_id: number | null
  parent_title?: string | null
  project_id: number | null
  project: Project | null
  task_type_id: number | null
  task_type: TaskType | null
  recurring_template_id?: number | null
  recurring_template_title?: string | null
  occurrence_key?: string | null
  recurrence_kind?: 'scheduled' | 'checklist' | 'quota_parent' | 'quota_session' | null
  quota_period_start?: string | null
  quota_period_end?: string | null
  expected_sessions?: number | null
  session_index?: number | null
  quota_completed?: number | null
  title: string
  description: string
  ready_to_plan?: boolean
  status: TaskStatus
  urgency: PriorityLevel | null
  importance: PriorityLevel | null
  deadline_date: string | null
  deadline_at: string | null
  reminder_at: string | null
  reminder_delivered_at: string | null
  position: number
  archived_at: string | null
  deleted_at: string | null
  created_at: string
  updated_at: string
  overdue: boolean
  planned_dates?: string[]
  allocation_total?: number
  allocation_completed?: number
  allocations?: Array<{
    block_id: number
    date: string
    start_minute: number
    end_minute: number
    time_completed: boolean
  }>
  subtasks: BattleTask[]
}

export interface TaskCompletionResult {
  task: BattleTask
  undo_token: string
  removed_planned_block_ids: number[]
}

export interface BattleTaskList {
  items: BattleTask[]
  timezone: string
  server_now_iso: string
}

export interface DueReminder {
  id: number
  title: string
  deadline_date: string | null
  deadline_at: string | null
  reminder_at: string
}

export type ProjectWrite = {
  name: string
  description?: string
  deadline_date?: string | null
  deadline_at?: string | null
}

export type BattleTaskWrite = {
  title: string
  description?: string
  ready_to_plan?: boolean
  status?: TaskStatus
  project_id?: number | null
  parent_id?: number | null
  task_type_id?: number | null
  urgency?: PriorityLevel | null
  importance?: PriorityLevel | null
  deadline_date?: string | null
  deadline_at?: string | null
  reminder_at?: string | null
}

export interface TimeBlock {
  id: number
  lane: BlockLane
  task_type_id: number
  task_type: TaskType
  task_id?: number | null
  task?: (Pick<BattleTask, 'id' | 'title' | 'status' | 'task_type_id'> & {
    archived_at?: string | null
    deleted_at?: string | null
  }) | null
  note: string | null
  /** Present on Actual blocks created via "complete as planned" from a Planned block. */
  planned_block_id?: number | null
  start_minute: number
  end_minute: number
  created_at: string
  updated_at: string
}

export interface DayMeta {
  timezone: string
  today: string
  server_now_iso: string
}

export interface DayRead {
  id: number
  date: string
  start_hour: number
  end_hour: number
  show_full_day: boolean
  created_at: string
  updated_at: string
  time_blocks: TimeBlock[]
  meta: DayMeta
}

export interface DayListItem {
  id: number
  date: string
  start_hour: number
  end_hour: number
  show_full_day: boolean
  updated_at: string
}

export interface HealthResponse {
  status: string
  today: string
  timezone: string
}

export interface SettingsRead {
  id: number
  start_hour: number
  end_hour: number
  show_full_day: boolean
  week_start?: 'monday' | 'sunday'
  created_at: string
  updated_at: string
}

export type RecurrenceWindow = { key: string; start: string; end: string }

export interface RecurringTemplate {
  id: number
  title: string
  description: string
  project_id: number | null
  project: Project | null
  task_type_id: number | null
  task_type: TaskType | null
  mode: RecurrenceMode
  status: RecurrenceStatus
  frequency: RecurrenceFrequency
  interval: number
  weekdays: number[]
  month_day: number | null
  quota_count: number | null
  start_date: string
  end_date: string | null
  cycle_limit: number | null
  urgency: PriorityLevel | null
  importance: PriorityLevel | null
  paused_at: string | null
  ended_at: string | null
  created_at: string
  updated_at: string
  checklist_items: Array<{ id: number; title: string; position: number }>
  upcoming: RecurrenceWindow[]
  current_tasks: Array<{ id: number; title: string; deadline_date: string | null; overdue: boolean }>
  cadence: string
  next_occurrence: string | null
}

export type RecurrenceRuleWrite = {
  mode: RecurrenceMode
  frequency: RecurrenceFrequency
  interval: number
  weekdays?: number[]
  month_day?: number | null
  quota_count?: number | null
  start_date: string
  end_date?: string | null
  cycle_limit?: number | null
}

export type RecurringTemplateWrite = RecurrenceRuleWrite & {
  title: string
  description?: string
  project_id?: number | null
  task_type_id?: number | null
  urgency?: PriorityLevel | null
  importance?: PriorityLevel | null
  checklist_titles?: string[]
  confirm_backfill?: boolean
}

export type RecurrencePreview = {
  upcoming: RecurrenceWindow[]
  past_cycles: number
  past_tasks: number
}

/** Matches backend 409 detail when DELETE /task-types/:id has blocks (no cascade/migrate). */
export const TASK_TYPE_STILL_IN_USE_DETAIL = 'Task type is still used by existing blocks'
