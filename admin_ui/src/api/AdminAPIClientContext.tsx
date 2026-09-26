import { createContext, useContext, useState, type ReactNode } from 'react'
import { AdminAPIClient } from './client'

const AdminAPIClientContext = createContext<AdminAPIClient | null>(null)

export function AdminAPIClientProvider({ children }: { children: ReactNode }) {
  const [client] = useState(() => new AdminAPIClient())
  return <AdminAPIClientContext.Provider value={client}>{children}</AdminAPIClientContext.Provider>
}

export function useAdminApiClient(): AdminAPIClient {
  const client = useContext(AdminAPIClientContext)
  if (!client) throw new Error('useAdminApiClient must be used within AdminAPIClientProvider')
  return client
}
