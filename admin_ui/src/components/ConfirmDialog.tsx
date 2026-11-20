import { useState, type FormEvent, type ReactNode } from 'react'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'

// Mounted only while asking, so every question starts empty
export function ConfirmDialog({
  title,
  children,
  confirmLabel,
  typeToConfirm,
  onConfirm,
  onClose,
}: {
  title: string
  children: ReactNode
  confirmLabel: string
  typeToConfirm?: string
  onConfirm: () => Promise<void>
  onClose: () => void
}) {
  const [typed, setTyped] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const ready = !typeToConfirm || typed.trim() === typeToConfirm

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!ready) return
    setBusy(true)
    setError(null)
    try {
      await onConfirm()
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
      <DialogTitle>{title}</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <Typography component="div" variant="body2" color="text.secondary">
          {children}
        </Typography>
        {typeToConfirm && (
          <TextField
            autoFocus
            size="small"
            label={`Type ${typeToConfirm} to confirm`}
            value={typed}
            onChange={(e) => setTyped(e.target.value)}
            slotProps={{ htmlInput: { autoComplete: 'off', spellCheck: false } }}
          />
        )}
        {error && <Alert severity="error">{error}</Alert>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={busy}>
          Cancel
        </Button>
        <Button type="submit" color="error" variant="contained" disabled={!ready || busy}>
          {busy ? 'Working…' : confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
