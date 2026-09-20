/**
 * The message to show for a thrown value.
 *
 * `catch` binds `unknown`, so every call site would otherwise repeat the same
 * `instanceof Error` narrowing before it can reach `.message`.
 */
export function errorMessage(cause: unknown, fallback: string): string {
  return cause instanceof Error ? cause.message : fallback
}
