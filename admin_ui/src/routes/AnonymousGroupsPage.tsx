import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import Typography from '@mui/material/Typography'
import { listAnonymousGroups } from '../api/anonymousGroups'
import type { AnonymousGroupSummary } from '../api/types'
import { Table } from '../components/Table'
import { formatDate } from '../utils/format'

export function AnonymousGroupsPage() {
  const [groups, setGroups] = useState<AnonymousGroupSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const navigate = useNavigate()

  useEffect(() => {
    listAnonymousGroups()
      .then(setGroups)
      .catch(() => setError('Failed to load anonymous groups.'))
      .finally(() => setLoading(false))
  }, [])

  return (
    <Box>
      <Typography variant="h5" sx={{ mb: 2 }}>
        Anonymous Groups
      </Typography>

      {loading && <CircularProgress />}
      {error && <Alert severity="error">{error}</Alert>}

      {!loading && !error && (
        <Table
          columns={[
            { header: 'Group ID', render: (g) => <code>{g.id}</code> },
            { header: 'Created', render: (g) => formatDate(g.createdAt) },
            { header: 'Members', render: (g) => g.memberCount },
            { header: 'Last location received', render: (g) => formatDate(g.lastLocationAt) },
          ]}
          rows={groups}
          keyFor={(g) => g.id}
          onRowClick={(g) => navigate(`/anonymous-groups/${g.id}`)}
          emptyMessage="No anonymous groups yet."
        />
      )}
    </Box>
  )
}
