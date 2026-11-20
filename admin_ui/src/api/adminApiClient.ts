import { createContext, useContext } from 'react'
import type { AdminAPIClient } from './client'

export const AdminAPIClientContext = createContext<AdminAPIClient | null>(null)

export function useAdminApiClient(): AdminAPIClient {
  const client = useContext(AdminAPIClientContext)
  if (!client) throw new Error('useAdminApiClient must be used within AdminAPIClientProvider')
  return client
}
