import type { TimeBlock } from './api'

type IdentifiableBlock = Pick<TimeBlock, 'name' | 'task' | 'task_type'>

export const UNTITLED_BLOCK = 'Untitled'

export function meaningfulTaskTypeName(block: IdentifiableBlock): string | null {
  const name = block.task_type?.name?.trim()
  return name && name.toLowerCase() !== 'unspecified' ? name : null
}

export function blockPrimaryIdentity(block: IdentifiableBlock): string {
  return block.name?.trim() || block.task?.title.trim() || meaningfulTaskTypeName(block) || UNTITLED_BLOCK
}

export function blockSecondaryIdentity(block: IdentifiableBlock): string | null {
  const hasName = !!block.name?.trim()
  const taskTitle = block.task?.title.trim() || null
  if (hasName && taskTitle) return taskTitle
  return hasName || taskTitle ? meaningfulTaskTypeName(block) : null
}
