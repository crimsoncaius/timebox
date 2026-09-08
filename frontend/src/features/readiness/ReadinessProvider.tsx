import { type ReactNode, useState } from 'react'
import { ReadinessContext, ReadinessCoordinator } from './readinessCoordinator'

export function ReadinessProvider({ children, coordinator: providedCoordinator }: {
  children: ReactNode
  coordinator?: ReadinessCoordinator
}) {
  const [localCoordinator] = useState(() => new ReadinessCoordinator())
  const coordinator = providedCoordinator ?? localCoordinator
  return <ReadinessContext.Provider value={coordinator}>{children}</ReadinessContext.Provider>
}
