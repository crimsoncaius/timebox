import { type ReactNode, useState } from 'react'
import { ReadinessContext, ReadinessCoordinator } from './readinessCoordinator'

export function ReadinessProvider({ children }: { children: ReactNode }) {
  const [coordinator] = useState(() => new ReadinessCoordinator())
  return <ReadinessContext.Provider value={coordinator}>{children}</ReadinessContext.Provider>
}
