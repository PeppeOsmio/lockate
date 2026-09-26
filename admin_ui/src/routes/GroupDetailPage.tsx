import { useEffect, useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Typography from '@mui/material/Typography'
import { useAdminApiClient } from '../api/AdminAPIClientContext'
import type { AGMember } from '../api/types'
import { Table } from '../components/Table'
import { formatDate } from '../utils/format'

export function GroupDetailPage() {
  const { anonymousGroupId } = useParams<{ anonymousGroupId: string }>()
  const [members, setMembers] = useState<AGMember[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const client = useAdminApiClient()

  useEffect(() => {
    if (!anonymousGroupId) return
    client.listGroupMembers(anonymousGroupId)
      .then(setMembers)
      .catch(() => setError('Failed to load members for this group.'))
      .finally(() => setLoading(false))
  }, [client, anonymousGroupId])

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <Typography variant="h5">
          Group <code>{anonymousGroupId}</code>
        </Typography>
        <Button component={RouterLink} to="/anonymous-groups" variant="outlined">
          Back to groups
        </Button>
      </Box>

      {loading && <CircularProgress />}
      {error && <Alert severity="error">{error}</Alert>}

      {!loading && !error && (
        <Table
          columns={[
            { header: 'Member ID', render: (m) => <code>{m.id}</code> },
            { header: 'Joined', render: (m) => formatDate(m.createdAt) },
            { header: 'Admin', render: (m) => (m.isAGAdmin ? 'Yes' : 'No') },
            { header: 'Last location received', render: (m) => formatDate(m.lastLocationAt) },
          ]}
          rows={members}
          keyFor={(m) => m.id}
          emptyMessage="No members in this group."
        />
      )}
    </Box>
  )
}
