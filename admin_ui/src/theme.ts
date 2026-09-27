import { createTheme } from '@mui/material'

export function buildTheme(mode: 'light' | 'dark') {
  return createTheme({
    palette: { mode },
    components: {
      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: {
          root: { textTransform: 'none', whiteSpace: 'nowrap', borderRadius: 8 },
        },
      },
    },
  })
}
