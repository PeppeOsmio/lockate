import { useState, type FormEvent } from 'react'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import MenuItem from '@mui/material/MenuItem'
import TextField from '@mui/material/TextField'
import type { ApiKey, ApiKeyWrite } from '../api/types'
import { parseServerDate, toServerDate } from '../utils/format'

export const LABEL_MAX = 60

const EXPIRY_DAYS = [30, 90, 365]

type Expiry = 'never' | 'date' | `${number}`

// A date input's value (local day) for a time
function dayInput(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

// The key stops working at the start of the chosen day, in the admin's time zone
function expiresAt(expiry: Expiry, day: string, now: Date): string | null {
  if (expiry === 'never') return null
  if (expiry === 'date') {
    const [y, m, d] = day.split('-').map(Number)
    return toServerDate(new Date(y, m - 1, d))
  }
  return toServerDate(new Date(now.getTime() + Number(expiry) * 24 * 3600 * 1000))
}

// Creates a key (no `apiKey`) or edits one; mounted only while open
export function ApiKeyDialog({
  apiKey,
  onSave,
  onClose,
}: {
  apiKey?: ApiKey
  onSave: (body: ApiKeyWrite) => Promise<void>
  onClose: () => void
}) {
  const current = apiKey?.expiresAt ? parseServerDate(apiKey.expiresAt) : null
  const [label, setLabel] = useState(apiKey?.label ?? '')
  const [expiry, setExpiry] = useState<Expiry>(current ? 'date' : 'never')
  const [day, setDay] = useState(current ? dayInput(current) : '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [today] = useState(() => dayInput(new Date()))
  const dayMissing = expiry === 'date' && !day

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (dayMissing) return
    setBusy(true)
    setError(null)
    try {
      await onSave({ label: label.trim() || null, expiresAt: expiresAt(expiry, day, new Date()) })
      onClose()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      open
      onClose={busy ? undefined : onClose}
      maxWidth="xs"
      fullWidth
      slotProps={{ paper: { component: 'form', onSubmit: handleSubmit } }}
    >
      <DialogTitle>{apiKey ? 'Edit API key' : 'New API key'}</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <TextField
          label="Label"
          value={label}
          onChange={(e) => setLabel(e.target.value)}
          helperText="Who or what it is for, to tell keys apart. Optional."
          slotProps={{ htmlInput: { maxLength: LABEL_MAX } }}
          autoFocus
          size="small"
          sx={{ mt: 1 }}
        />
        <TextField
          select
          label="Expires"
          value={expiry}
          onChange={(e) => setExpiry(e.target.value as Expiry)}
          size="small"
        >
          <MenuItem value="never">Never</MenuItem>
          {EXPIRY_DAYS.map((days) => (
            <MenuItem key={days} value={String(days)}>
              In {days === 365 ? 'a year' : `${days} days`}
            </MenuItem>
          ))}
          <MenuItem value="date">On a date…</MenuItem>
        </TextField>
        {expiry === 'date' && (
          <TextField
            type="date"
            label="Stops working on"
            value={day}
            onChange={(e) => setDay(e.target.value)}
            helperText="From the start of that day, your time."
            slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: today } }}
            size="small"
            required
          />
        )}
        {apiKey && expiry !== 'never' && (
          <Alert severity="info">
            Apps using the key are refused once it expires, until they get another one.
          </Alert>
        )}
        {error && <Alert severity="error">{error}</Alert>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={busy}>
          Cancel
        </Button>
        <Button type="submit" variant="contained" disabled={busy || dayMissing}>
          {apiKey ? 'Save' : 'Create key'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
