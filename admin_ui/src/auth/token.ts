import { createContext, useContext } from 'react'

export interface TokenContextValue {
  token: string | null
  setToken: (value: string) => void
  clearToken: () => void
}

export const TokenContext = createContext<TokenContextValue | null>(null)

export function useToken(): TokenContextValue {
  const context = useContext(TokenContext)
  if (!context) throw new Error('useToken must be used within a TokenProvider')
  return context
}
