import { useState } from 'react'
import Button from '@mui/material/Button'
import Stack from '@mui/material/Stack'

export function ConfirmButton({
  label,
  confirmLabel,
  onConfirm,
}: {
  label: string
  confirmLabel: string
  onConfirm: () => Promise<void>
}) {
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)

  async function handleConfirm() {
    setBusy(true)
    try {
      await onConfirm()
    } finally {
      setBusy(false)
      setConfirming(false)
    }
  }

  if (confirming) {
    return (
      <Stack direction="row" spacing={1}>
        <Button
          size="small"
          color="error"
          variant="outlined"
          disabled={busy}
          onClick={handleConfirm}
        >
          {confirmLabel}
        </Button>
        <Button
          size="small"
          variant="outlined"
          disabled={busy}
          onClick={() => setConfirming(false)}
        >
          Cancel
        </Button>
      </Stack>
    )
  }

  return (
    <Button size="small" variant="outlined" onClick={() => setConfirming(true)}>
      {label}
    </Button>
  )
}
