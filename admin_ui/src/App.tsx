import { useMemo, useState } from 'react'
import { BrowserRouter, Link as RouterLink, Navigate, Route, Routes } from 'react-router-dom'
import DarkModeIcon from '@mui/icons-material/DarkMode'
import LightModeIcon from '@mui/icons-material/LightMode'
import LogoutIcon from '@mui/icons-material/Logout'
import AppBar from '@mui/material/AppBar'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Container from '@mui/material/Container'
import CssBaseline from '@mui/material/CssBaseline'
import IconButton from '@mui/material/IconButton'
import Toolbar from '@mui/material/Toolbar'
import Typography from '@mui/material/Typography'
import { createTheme, ThemeProvider, useMediaQuery } from '@mui/material'
import { AdminAPIClientProvider } from './api/AdminAPIClientContext'
import { TokenGate } from './auth/TokenGate'
import { TokenProvider, useToken } from './auth/TokenContext'
import { ApiKeysPage } from './routes/ApiKeysPage'
import { AnonymousGroupsPage } from './routes/AnonymousGroupsPage'
import { GroupDetailPage } from './routes/GroupDetailPage'

const STORAGE_KEY = 'lockate-color-mode'

function AdminLayout({ mode, toggleMode }: { mode: 'light' | 'dark'; toggleMode: () => void }) {
  const { clearToken } = useToken()

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100svh' }}>
      <AppBar position="static">
        <Toolbar sx={{ gap: 1 }}>
          <Typography variant="h6" sx={{ flexGrow: 1, fontWeight: 600 }}>
            Lockate Admin
          </Typography>
          <Button component={RouterLink} to="/api-keys" color="inherit">
            API Keys
          </Button>
          <Button component={RouterLink} to="/anonymous-groups" color="inherit">
            Anonymous Groups
          </Button>
          <IconButton color="inherit" onClick={toggleMode}>
            {mode === 'dark' ? <LightModeIcon /> : <DarkModeIcon />}
          </IconButton>
          <IconButton color="inherit" onClick={clearToken}>
            <LogoutIcon />
          </IconButton>
        </Toolbar>
      </AppBar>
      <Container maxWidth="md" sx={{ py: 3, flex: 1 }}>
        <Routes>
          <Route path="/" element={<Navigate to="/api-keys" replace />} />
          <Route path="/api-keys" element={<ApiKeysPage />} />
          <Route path="/anonymous-groups" element={<AnonymousGroupsPage />} />
          <Route path="/anonymous-groups/:anonymousGroupId" element={<GroupDetailPage />} />
        </Routes>
      </Container>
    </Box>
  )
}

function App() {
  const prefersDark = useMediaQuery('(prefers-color-scheme: dark)')
  const [mode, setMode] = useState<'light' | 'dark'>(() => {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'light' || saved === 'dark') return saved
    return prefersDark ? 'dark' : 'light'
  })

  const toggleMode = () => {
    setMode((prev) => {
      const next = prev === 'light' ? 'dark' : 'light'
      localStorage.setItem(STORAGE_KEY, next)
      return next
    })
  }

  const theme = useMemo(() => createTheme({ palette: { mode } }), [mode])

  return (
    <ThemeProvider theme={theme}>
      <AdminAPIClientProvider>
      <TokenProvider>
        <BrowserRouter>
          <CssBaseline />
          <TokenGate>
            <AdminLayout mode={mode} toggleMode={toggleMode} />
          </TokenGate>
        </BrowserRouter>
      </TokenProvider>
      </AdminAPIClientProvider>
    </ThemeProvider>
  )
}

export default App
