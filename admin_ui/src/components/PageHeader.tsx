import type { ReactNode } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import Box from '@mui/material/Box'
import Breadcrumbs from '@mui/material/Breadcrumbs'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'

export function PageHeader({
  title,
  crumb,
  parent,
  description,
  actions,
}: {
  title: ReactNode
  // The current page in the breadcrumbs, when the title isn't plain text
  crumb?: string
  parent?: { label: string; to: string }
  description?: ReactNode
  actions?: ReactNode
}) {
  return (
    <Box sx={{ mb: 3 }}>
      {parent && (
        <Breadcrumbs sx={{ mb: 1 }}>
          <Link component={RouterLink} to={parent.to} underline="hover" color="inherit">
            {parent.label}
          </Link>
          <Typography color="text.primary">{crumb ?? title}</Typography>
        </Breadcrumbs>
      )}
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        sx={{ alignItems: { xs: 'flex-start', sm: 'center' }, justifyContent: 'space-between' }}
      >
        <Typography variant="h5" component="h1">
          {title}
        </Typography>
        {actions && (
          <Stack direction="row" spacing={1} sx={{ flexShrink: 0 }}>
            {actions}
          </Stack>
        )}
      </Stack>
      {description && (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1, maxWidth: 720 }}>
          {description}
        </Typography>
      )}
    </Box>
  )
}
