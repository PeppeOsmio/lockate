import { createTheme } from '@mui/material'

export type ColorMode = 'light' | 'dark'

export function buildTheme(mode: ColorMode) {
  return createTheme({
    palette: { mode },
    shape: { borderRadius: 8 },
    typography: {
      h5: { fontWeight: 600 },
    },
    components: {
      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: {
          root: { textTransform: 'none', whiteSpace: 'nowrap' },
        },
      },
      MuiTableCell: {
        styleOverrides: {
          head: { fontWeight: 600 },
        },
      },
    },
  })
}
