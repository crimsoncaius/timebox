export type IdentifiableBlock = { name?: string | null; task?: { title: string } | null; task_type?: { name: string } | null }

export const UNTITLED_BLOCK = 'Unnamed activity'

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
  return hasName || taskTitle ? meaningfulTaskTypeName(block) : null
}

/** Compact prose and accessibility labels retain both parts of the identity. */
export function blockIdentityText(block: IdentifiableBlock): string {
  return [blockPrimaryIdentity(block), blockSecondaryIdentity(block)].filter(Boolean).join(' · ')
}
