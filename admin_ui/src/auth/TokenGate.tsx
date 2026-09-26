import { useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Paper from '@mui/material/Paper'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useAdminApiClient } from '../api/AdminAPIClientContext'
import { useToken } from './TokenContext'

export function TokenGate({ children }: { children: ReactNode }) {
  const { token, setToken } = useToken()
  const client = useAdminApiClient()
  const [input, setInput] = useState('')
  const [checking, setChecking] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (token) {
    return <>{children}</>
  }

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
      setError('Could not reach the Lockate backend. Check VITE_API_BASE_URL and try again.')
    } finally {
      setChecking(false)
    }
  }

  return (
    <Box
      sx={{ minHeight: '100svh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
    >
      <Paper elevation={3} sx={{ p: 4, width: 360 }}>
        <Box
          component="form"
          onSubmit={handleSubmit}
          sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
        >
          <Typography variant="h5" sx={{ fontWeight: 600 }}>
            Lockate Admin
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Paste an admin key to continue.
          </Typography>
          <TextField
            type="password"
            autoFocus
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Admin key"
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
