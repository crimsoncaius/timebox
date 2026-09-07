export type BattlePlanScope = 'all' | 'admin' | `project:${number}`

export const BATTLE_PLAN_STORAGE_KEY = 'timebox:battle-plan:v1'

export function persistBattlePlanScope(scope: BattlePlanScope) {
  try {
    const stored = JSON.parse(localStorage.getItem(BATTLE_PLAN_STORAGE_KEY) ?? '{}') as Record<string, unknown>
    localStorage.setItem(BATTLE_PLAN_STORAGE_KEY, JSON.stringify({ ...stored, version: 1, scope }))
  } catch {
    localStorage.setItem(BATTLE_PLAN_STORAGE_KEY, JSON.stringify({ version: 1, scope }))
  }
}
