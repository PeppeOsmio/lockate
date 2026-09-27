import { useEffect, useState } from 'react'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useAdminApiClient } from '../api/AdminAPIClientContext'
import type { AGMember } from '../api/types'
import { ConfirmButton } from '../components/ConfirmButton'
import { Table } from '../components/Table'
import { formatDate } from '../utils/format'

export function GroupDetailPage() {
  const { anonymousGroupId } = useParams<{ anonymousGroupId: string }>()
  const [members, setMembers] = useState<AGMember[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const client = useAdminApiClient()
  const navigate = useNavigate()

  useEffect(() => {
    if (!anonymousGroupId) return
    client.listGroupMembers(anonymousGroupId)
      .then(setMembers)
      .catch(() => setError('Failed to load members for this group.'))
      .finally(() => setLoading(false))
  }, [client, anonymousGroupId])

  async function handleDeleteGroup() {
    if (!anonymousGroupId) return
    await client.deleteAnonymousGroup(anonymousGroupId)
    navigate('/anonymous-groups')
  }

  async function handleDeleteMember(memberId: string) {
    if (!anonymousGroupId) return
    await client.deleteGroupMember(anonymousGroupId, memberId)
    setMembers((prev) => prev.filter((m) => m.id !== memberId))
  }

  return (
    <Box>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1}
        sx={{ mb: 2, alignItems: { xs: 'flex-start', sm: 'center' }, justifyContent: 'space-between' }}
      >
        <Typography variant="h5" sx={{ wordBreak: 'break-all' }}>
          Group <code>{anonymousGroupId}</code>
        </Typography>
        <Stack direction="row" spacing={1} sx={{ flexShrink: 0 }}>
          <ConfirmButton
            label="Delete group"
            confirmLabel="Confirm delete"
            onConfirm={handleDeleteGroup}
          />
          <Button component={RouterLink} to="/anonymous-groups" variant="outlined">
            Back
          </Button>
        </Stack>
      </Stack>

      {loading && <CircularProgress />}
      {error && <Alert severity="error">{error}</Alert>}

      {!loading && !error && (
        <Table
          columns={[
            { header: 'Member ID', render: (m) => <code>{m.id}</code> },
            { header: 'Joined', render: (m) => formatDate(m.createdAt) },
            { header: 'Admin', render: (m) => (m.isAGAdmin ? 'Yes' : 'No') },
            { header: 'Last location received', render: (m) => formatDate(m.lastLocationAt) },
            {
              header: '',
              render: (m) => (
                <ConfirmButton
                  label="Remove"
                  confirmLabel="Confirm remove"
                  onConfirm={() => handleDeleteMember(m.id)}
                />
              ),
            },
          ]}
          rows={members}
          keyFor={(m) => m.id}
          emptyMessage="No members in this group."
        />
      )}
    </Box>
  )
}
