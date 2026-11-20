import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import { formatDateTime, formatRelative, parseServerDate } from '../utils/format'

export function Time({ value }: { value: string | null }) {
  if (!value) {
    return (
      <Typography component="span" variant="inherit" color="text.secondary">
        Never
      </Typography>
    )
  }
  const date = parseServerDate(value)
  return (
    <Tooltip title={formatDateTime(date)}>
      <time dateTime={date.toISOString()}>{formatRelative(date)}</time>
    </Tooltip>
  )
}
