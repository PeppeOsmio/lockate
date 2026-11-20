import { Link as RouterLink } from 'react-router-dom'
import Button from '@mui/material/Button'
import Typography from '@mui/material/Typography'
import { PageHeader } from '../components/PageHeader'
import { usePageTitle } from '../hooks/usePageTitle'

export function NotFoundPage() {
  usePageTitle('Not found')
  return (
    <>
      <PageHeader title="Page not found" />
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        There is nothing at this address.
      </Typography>
      <Button component={RouterLink} to="/groups" variant="outlined">
        Go to groups
      </Button>
    </>
  )
}
