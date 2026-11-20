import { createContext, useContext } from 'react'

export type Notify = (message: string, severity?: 'success' | 'error') => void

export const NotifyContext = createContext<Notify | null>(null)

export function useNotify(): Notify {
  const notify = useContext(NotifyContext)
  if (!notify) throw new Error('useNotify must be used within NotifyProvider')
  return notify
}
