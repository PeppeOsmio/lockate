import { createContext, useContext } from 'react'
import type { ColorMode } from './buildTheme'

export interface ColorModeContextValue {
  mode: ColorMode
  toggle: () => void
}

export const ColorModeContext = createContext<ColorModeContextValue | null>(null)

export function useColorMode(): ColorModeContextValue {
  const context = useContext(ColorModeContext)
  if (!context) throw new Error('useColorMode must be used within ColorModeProvider')
  return context
}
