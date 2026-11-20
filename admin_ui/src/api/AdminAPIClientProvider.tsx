import { useState, type ReactNode } from 'react'
import { AdminAPIClientContext } from './adminApiClient'
import { AdminAPIClient } from './client'

export function AdminAPIClientProvider({
  client: given,
  children,
}: {
  client?: AdminAPIClient
  children: ReactNode
}) {
  const [client] = useState(() => given ?? new AdminAPIClient())
  return <AdminAPIClientContext.Provider value={client}>{children}</AdminAPIClientContext.Provider>
}
