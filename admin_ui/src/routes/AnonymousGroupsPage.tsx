import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import Typography from '@mui/material/Typography'
import { useAdminApiClient } from '../api/AdminAPIClientContext'
import type { AnonymousGroupSummary } from '../api/types'
import { ConfirmButton } from '../components/ConfirmButton'
import { Table } from '../components/Table'
import { formatDate } from '../utils/format'

export function AnonymousGroupsPage() {
  const [groups, setGroups] = useState<AnonymousGroupSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const navigate = useNavigate()
  const client = useAdminApiClient()

  useEffect(() => {
    client.listAnonymousGroups()
      .then(setGroups)
      .catch(() => setError('Failed to load anonymous groups.'))
      .finally(() => setLoading(false))
  }, [client])

  async function handleDelete(groupId: string) {
    await client.deleteAnonymousGroup(groupId)
    setGroups((prev) => prev.filter((g) => g.id !== groupId))
  }

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
            {
              header: '',
              render: (g) => (
                <Box onClick={(e) => e.stopPropagation()} sx={{ display: 'inline-block' }}>
                  <ConfirmButton
                    label="Delete"
                    confirmLabel="Confirm delete"
                    onConfirm={() => handleDelete(g.id)}
                  />
                </Box>
              ),
            },
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
