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
  onConfirm: () => void
}) {
  const [confirming, setConfirming] = useState(false)

  if (confirming) {
    return (
      <Stack direction="row" spacing={1}>
        <Button size="small" color="error" variant="outlined" onClick={onConfirm}>
          {confirmLabel}
        </Button>
        <Button size="small" variant="outlined" onClick={() => setConfirming(false)}>
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
