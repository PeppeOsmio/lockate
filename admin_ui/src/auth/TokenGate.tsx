import { useEffect, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Paper from '@mui/material/Paper'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useQueryClient } from '@tanstack/react-query'
import { useAdminApiClient } from '../api/adminApiClient'
import { Logo } from '../components/Logo'
import { usePageTitle } from '../hooks/usePageTitle'
import { useToken } from './token'

export function TokenGate({ children }: { children: ReactNode }) {
  const { token } = useToken()
  const queryClient = useQueryClient()

  // Nothing seen with a key outlives it
  useEffect(() => {
    if (!token) queryClient.clear()
  }, [token, queryClient])

  return token ? <>{children}</> : <SignIn />
}

function SignIn() {
  usePageTitle('Sign in')
  const { setToken } = useToken()
  const client = useAdminApiClient()
  const [input, setInput] = useState('')
  const [checking, setChecking] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setChecking(true)
    setError(null)
    try {
      const ok = await client.checkApiKey(input.trim())
      if (!ok) {
        setError('That key was rejected by the server. Double-check it and try again.')
        return
      }
      setToken(input.trim())
    } catch {
      setError('Could not reach the Lockate server. Check VITE_API_BASE_URL and try again.')
    } finally {
      setChecking(false)
    }
  }

  return (
    <Box
      sx={{
        minHeight: '100svh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        p: 2,
      }}
    >
      <Paper variant="outlined" sx={{ p: 4, width: 380, maxWidth: '100%' }}>
        <Box
          component="form"
          onSubmit={handleSubmit}
          sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
        >
          <Logo />
          <Typography variant="body2" color="text.secondary">
            Paste the server's admin key (ADMIN_API_KEY) to continue.
          </Typography>
          <TextField
            type="password"
            autoFocus
            value={input}
            onChange={(e) => setInput(e.target.value)}
            label="Admin key"
            size="small"
            fullWidth
          />
          <Button
            type="submit"
            variant="contained"
            disabled={checking || input.trim().length === 0}
            fullWidth
          >
            {checking ? 'Checking…' : 'Continue'}
          </Button>
          {error && <Alert severity="error">{error}</Alert>}
        </Box>
      </Paper>
    </Box>
  )
}
