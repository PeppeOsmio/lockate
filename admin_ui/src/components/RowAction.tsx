import type { ReactNode } from 'react'
import IconButton from '@mui/material/IconButton'
import Tooltip from '@mui/material/Tooltip'

export function RowAction({
  label,
  icon,
  onClick,
}: {
  label: string
  icon: ReactNode
  onClick: () => void
}) {
  return (
    <Tooltip title={label}>
      <IconButton
        size="small"
        aria-label={label}
        onClick={(e) => {
          e.stopPropagation()
          onClick()
        }}
      >
        {icon}
      </IconButton>
    </Tooltip>
  )
}
