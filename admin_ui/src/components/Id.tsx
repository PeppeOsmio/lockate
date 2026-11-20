import { useState } from 'react'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import CheckIcon from '@mui/icons-material/Check'
import Box from '@mui/material/Box'
import IconButton from '@mui/material/IconButton'
import Tooltip from '@mui/material/Tooltip'
import { shortId } from '../utils/id'

export function Id({ value }: { value: string }) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    await navigator.clipboard.writeText(value)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <Box
      component="span"
      sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, whiteSpace: 'nowrap' }}
      onClick={(e) => e.stopPropagation()}
    >
      <Tooltip title={value}>
        <Box component="code" sx={{ fontSize: '0.875em' }}>
          {shortId(value)}
        </Box>
      </Tooltip>
      <Tooltip title={copied ? 'Copied' : 'Copy full ID'}>
        <IconButton size="small" aria-label={`Copy ${value}`} onClick={copy}>
          {copied ? <CheckIcon fontSize="inherit" /> : <ContentCopyIcon fontSize="inherit" />}
        </IconButton>
      </Tooltip>
    </Box>
  )
}
