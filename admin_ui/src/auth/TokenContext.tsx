import { createContext, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import * as tokenStorage from './tokenStorage'

interface TokenContextValue {
  token: string | null
  setToken: (value: string) => void
  clearToken: () => void
}

const TokenContext = createContext<TokenContextValue | null>(null)

export function TokenProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(() => tokenStorage.getToken())

  useEffect(() => tokenStorage.onTokenChange(() => setTokenState(tokenStorage.getToken())), [])

  const value: TokenContextValue = {
    token,
    setToken: tokenStorage.setToken,
    clearToken: tokenStorage.clearToken,
  }

  return <TokenContext.Provider value={value}>{children}</TokenContext.Provider>
}

export function useToken(): TokenContextValue {
  const context = useContext(TokenContext)
  if (!context) {
    throw new Error('useToken must be used within a TokenProvider')
  }
  return context
}
