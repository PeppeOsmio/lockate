import { useMemo, useState, type ReactNode } from 'react'
import CssBaseline from '@mui/material/CssBaseline'
import { ThemeProvider, useMediaQuery } from '@mui/material'
import { buildTheme, type ColorMode } from './buildTheme'
import { ColorModeContext } from './colorMode'

const STORAGE_KEY = 'lockate-color-mode'

function savedMode(): ColorMode | null {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    return saved === 'light' || saved === 'dark' ? saved : null
  } catch {
    return null
  }
}

export function ColorModeProvider({ children }: { children: ReactNode }) {
  const prefersDark = useMediaQuery('(prefers-color-scheme: dark)')
  const [chosen, setChosen] = useState<ColorMode | null>(savedMode)
  const mode: ColorMode = chosen ?? (prefersDark ? 'dark' : 'light')
  const theme = useMemo(() => buildTheme(mode), [mode])

  const value = useMemo(
    () => ({
      mode,
      toggle: () => {
        const next = mode === 'light' ? 'dark' : 'light'
        setChosen(next)
        try {
          localStorage.setItem(STORAGE_KEY, next)
        } catch {
          // the choice then lasts only for this page
        }
      },
    }),
    [mode],
  )

  return (
    <ColorModeContext.Provider value={value}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ColorModeContext.Provider>
  )
}
