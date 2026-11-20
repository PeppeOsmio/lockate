import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { TokenContext, type TokenContextValue } from './token'
import * as tokenStorage from './tokenStorage'

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
