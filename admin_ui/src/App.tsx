import { useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import DarkModeIcon from '@mui/icons-material/DarkMode'
import GroupsIcon from '@mui/icons-material/Groups'
import KeyIcon from '@mui/icons-material/Key'
import LightModeIcon from '@mui/icons-material/LightMode'
import LogoutIcon from '@mui/icons-material/Logout'
import MenuIcon from '@mui/icons-material/Menu'
import AppBar from '@mui/material/AppBar'
import Box from '@mui/material/Box'
import Divider from '@mui/material/Divider'
import Drawer from '@mui/material/Drawer'
import IconButton from '@mui/material/IconButton'
import List from '@mui/material/List'
import ListItemButton from '@mui/material/ListItemButton'
import ListItemIcon from '@mui/material/ListItemIcon'
import ListItemText from '@mui/material/ListItemText'
import Toolbar from '@mui/material/Toolbar'
import Tooltip from '@mui/material/Tooltip'
import { useMediaQuery, useTheme } from '@mui/material'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AdminAPIClientProvider } from './api/AdminAPIClientProvider'
import { ApiError } from './api/client'
import { TokenGate } from './auth/TokenGate'
import { useToken } from './auth/token'
import { TokenProvider } from './auth/TokenProvider'
import { Logo } from './components/Logo'
import { NotifyProvider } from './feedback/NotifyProvider'
import { ApiKeysPage } from './routes/ApiKeysPage'
import { GroupDetailPage } from './routes/GroupDetailPage'
import { GroupsPage } from './routes/GroupsPage'
import { NotFoundPage } from './routes/NotFoundPage'
import { ColorModeProvider } from './theme/ColorModeProvider'
import { useColorMode } from './theme/colorMode'

const DRAWER_WIDTH = 220

const NAV_ITEMS = [
  { label: 'Groups', path: '/groups', icon: <GroupsIcon /> },
  { label: 'API keys', path: '/api-keys', icon: <KeyIcon /> },
]

function NavDrawerContent({ onNavigate }: { onNavigate?: () => void }) {
  const location = useLocation()
  const navigate = useNavigate()
  const { clearToken } = useToken()
  const { mode, toggle } = useColorMode()

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Toolbar>
        <Logo />
      </Toolbar>
      <Divider />
      <List sx={{ flex: 1, pt: 1 }}>
        {NAV_ITEMS.map((item) => (
          <ListItemButton
            key={item.path}
            selected={location.pathname.startsWith(item.path)}
            onClick={() => {
              navigate(item.path)
              onNavigate?.()
            }}
            sx={{ borderRadius: 2, mx: 1, mb: 0.5 }}
          >
            <ListItemIcon sx={{ minWidth: 36 }}>{item.icon}</ListItemIcon>
            <ListItemText primary={item.label} />
          </ListItemButton>
        ))}
      </List>
      <Divider />
      <Box sx={{ display: 'flex', justifyContent: 'space-around', p: 1 }}>
        <Tooltip title={mode === 'dark' ? 'Light theme' : 'Dark theme'}>
          <IconButton onClick={toggle} size="small" aria-label="Toggle theme">
            {mode === 'dark' ? <LightModeIcon /> : <DarkModeIcon />}
          </IconButton>
        </Tooltip>
        <Tooltip title="Sign out">
          <IconButton onClick={clearToken} size="small" aria-label="Sign out">
            <LogoutIcon />
          </IconButton>
        </Tooltip>
      </Box>
    </Box>
  )
}

function AdminLayout() {
  const theme = useTheme()
  const isDesktop = useMediaQuery(theme.breakpoints.up('md'))
  const [mobileOpen, setMobileOpen] = useState(false)

  return (
    <Box sx={{ display: 'flex', minHeight: '100svh' }}>
      {!isDesktop && (
        <AppBar
          position="fixed"
          color="default"
          elevation={0}
          sx={{ borderBottom: 1, borderColor: 'divider' }}
        >
          <Toolbar>
            <IconButton
              edge="start"
              sx={{ mr: 1 }}
              aria-label="Open menu"
              onClick={() => setMobileOpen(true)}
            >
              <MenuIcon />
            </IconButton>
            <Logo />
          </Toolbar>
        </AppBar>
      )}

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
        <NavDrawerContent onNavigate={isDesktop ? undefined : () => setMobileOpen(false)} />
      </Drawer>

      <Box component="main" sx={{ flex: 1, minWidth: 0, p: { xs: 2, sm: 3 } }}>
        {!isDesktop && <Toolbar />}
        <Box sx={{ maxWidth: 1200, mx: 'auto' }}>
          <Routes>
            <Route path="/" element={<Navigate to="/groups" replace />} />
            <Route path="/groups" element={<GroupsPage />} />
            <Route path="/groups/:groupId" element={<GroupDetailPage />} />
            <Route path="/api-keys" element={<ApiKeysPage />} />
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </Box>
      </Box>
    </Box>
  )
}

// Client errors (a missing group, a refused key) won't change on a second try
function shouldRetry(failureCount: number, error: Error): boolean {
  const clientError = error instanceof ApiError && error.status >= 400 && error.status < 500
  return !clientError && failureCount < 2
}

function App() {
  const [queryClient] = useState(
    () => new QueryClient({ defaultOptions: { queries: { retry: shouldRetry } } }),
  )

  return (
    <AdminAPIClientProvider>
      <QueryClientProvider client={queryClient}>
        <ColorModeProvider>
          <NotifyProvider>
            <TokenProvider>
              <BrowserRouter>
                <TokenGate>
                  <AdminLayout />
                </TokenGate>
              </BrowserRouter>
            </TokenProvider>
          </NotifyProvider>
        </ColorModeProvider>
      </QueryClientProvider>
    </AdminAPIClientProvider>
  )
}

export default App
