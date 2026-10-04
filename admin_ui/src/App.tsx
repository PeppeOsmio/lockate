import { useMemo, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import DarkModeIcon from '@mui/icons-material/DarkMode'
import KeyIcon from '@mui/icons-material/Key'
import LightModeIcon from '@mui/icons-material/LightMode'
import LogoutIcon from '@mui/icons-material/Logout'
import MenuIcon from '@mui/icons-material/Menu'
import PeopleIcon from '@mui/icons-material/People'
import AppBar from '@mui/material/AppBar'
import Box from '@mui/material/Box'
import CssBaseline from '@mui/material/CssBaseline'
import Divider from '@mui/material/Divider'
import Drawer from '@mui/material/Drawer'
import IconButton from '@mui/material/IconButton'
import List from '@mui/material/List'
import ListItemButton from '@mui/material/ListItemButton'
import ListItemIcon from '@mui/material/ListItemIcon'
import ListItemText from '@mui/material/ListItemText'
import Toolbar from '@mui/material/Toolbar'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import { ThemeProvider, useMediaQuery } from '@mui/material'
import { AdminAPIClientProvider } from './api/AdminAPIClientContext'
import { TokenGate } from './auth/TokenGate'
import { TokenProvider, useToken } from './auth/TokenContext'
import { ApiKeysPage } from './routes/ApiKeysPage'
import { AnonymousGroupsPage } from './routes/AnonymousGroupsPage'
import { GroupDetailPage } from './routes/GroupDetailPage'
import { buildTheme } from './theme'

const STORAGE_KEY = 'lockate-color-mode'
const DRAWER_WIDTH = 220

const NAV_ITEMS = [
  { label: 'API Keys', path: '/api-keys', icon: <KeyIcon /> },
  { label: 'Anonymous Groups', path: '/anonymous-groups', icon: <PeopleIcon /> },
]

function NavDrawerContent({
  mode,
  onToggleMode,
  onNavigate,
}: {
  mode: 'light' | 'dark'
  onToggleMode: () => void
  onNavigate?: () => void
}) {
  const location = useLocation()
  const navigate = useNavigate()
  const { clearToken } = useToken()

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Toolbar>
        <Typography variant="h6" sx={{ fontWeight: 700, fontSize: 16 }}>
          Lockate Admin
        </Typography>
      </Toolbar>
      <Divider />
      <List sx={{ flex: 1, pt: 1 }}>
        {NAV_ITEMS.map((item) => {
          const active = location.pathname.startsWith(item.path)
          return (
            <ListItemButton
              key={item.path}
              selected={active}
              onClick={() => {
                navigate(item.path)
                onNavigate?.()
              }}
              sx={{ borderRadius: 2, mx: 1, mb: 0.5 }}
            >
              <ListItemIcon sx={{ minWidth: 36 }}>{item.icon}</ListItemIcon>
              <ListItemText primary={item.label} />
            </ListItemButton>
          )
        })}
      </List>
      <Divider />
      <Box sx={{ display: 'flex', justifyContent: 'space-around', p: 1 }}>
        <Tooltip title="Toggle theme">
          <IconButton onClick={onToggleMode} size="small">
            {mode === 'dark' ? <LightModeIcon /> : <DarkModeIcon />}
          </IconButton>
        </Tooltip>
        <Tooltip title="Logout">
          <IconButton onClick={clearToken} size="small">
            <LogoutIcon />
          </IconButton>
        </Tooltip>
      </Box>
    </Box>
  )
}

function AdminLayout() {
  const prefersDark = useMediaQuery('(prefers-color-scheme: dark)')
  const [mode, setMode] = useState<'light' | 'dark'>(() => {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'light' || saved === 'dark') return saved
    return prefersDark ? 'dark' : 'light'
  })
  const theme = useMemo(() => buildTheme(mode), [mode])

  function toggleMode() {
    setMode((prev) => {
      const next = prev === 'light' ? 'dark' : 'light'
      localStorage.setItem(STORAGE_KEY, next)
      return next
    })
  }
  const isDesktop = useMediaQuery(theme.breakpoints.up('md'))
  const [mobileOpen, setMobileOpen] = useState(false)

  return (
    <ThemeProvider theme={theme}>
      <Box sx={{ display: 'flex', minHeight: '100svh' }}>
        <CssBaseline />

        {/* Mobile top bar */}
        {!isDesktop && (
          <AppBar position="fixed" sx={{ zIndex: (t) => t.zIndex.drawer + 1 }}>
            <Toolbar>
              <IconButton
                color="inherit"
                edge="start"
                sx={{ mr: 1 }}
                onClick={() => setMobileOpen(true)}
              >
                <MenuIcon />
              </IconButton>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>
                Lockate Admin
              </Typography>
            </Toolbar>
          </AppBar>
        )}

        {/* Left Drawer — permanent on desktop, temporary on mobile */}
        <Drawer
          anchor="left"
          variant={isDesktop ? 'permanent' : 'temporary'}
          open={isDesktop || mobileOpen}
          onClose={() => setMobileOpen(false)}
          sx={{
            width: DRAWER_WIDTH,
            flexShrink: 0,
            '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' },
          }}
        >
          <NavDrawerContent
            mode={mode}
            onToggleMode={toggleMode}
            onNavigate={isDesktop ? undefined : () => setMobileOpen(false)}
          />
        </Drawer>

        {/* Main content */}
        <Box
          component="main"
          sx={{
            flex: 1,
            p: 3,
            ml: isDesktop ? `${DRAWER_WIDTH}px` : 0,
            mt: isDesktop ? 0 : '64px',
            maxWidth: isDesktop ? `calc(100% - ${DRAWER_WIDTH}px)` : '100%',
          }}
        >
          <Routes>
            <Route path="/" element={<Navigate to="/api-keys" replace />} />
            <Route path="/api-keys" element={<ApiKeysPage />} />
            <Route path="/anonymous-groups" element={<AnonymousGroupsPage />} />
            <Route path="/anonymous-groups/:anonymousGroupId" element={<GroupDetailPage />} />
          </Routes>
        </Box>
      </Box>
    </ThemeProvider>
  )
}

function App() {
  return (
    <AdminAPIClientProvider>
      <TokenProvider>
        <BrowserRouter>
          <TokenGate>
            <AdminLayout />
          </TokenGate>
        </BrowserRouter>
      </TokenProvider>
    </AdminAPIClientProvider>
  )
}

export default App
