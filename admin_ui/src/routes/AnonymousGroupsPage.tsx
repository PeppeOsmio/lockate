import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import Typography from '@mui/material/Typography'
import { useAdminApiClient } from '../api/AdminAPIClientContext'
import type { AnonymousGroupSummary, Page } from '../api/types'
import { ConfirmButton } from '../components/ConfirmButton'
import { Table } from '../components/Table'
import { TablePager } from '../components/TablePager'
import { formatDate } from '../utils/format'

export function AnonymousGroupsPage() {
  const [result, setResult] = useState<Page<AnonymousGroupSummary> | null>(null)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const navigate = useNavigate()
  const client = useAdminApiClient()

  useEffect(() => {
    setLoading(true)
    client.listAnonymousGroups(page)
      .then(setResult)
      .catch(() => setError('Failed to load anonymous groups.'))
      .finally(() => setLoading(false))
  }, [client, page])

  async function handleDelete(groupId: string) {
    await client.deleteAnonymousGroup(groupId)
    const newTotal = (result?.totalElements ?? 1) - 1
    const maxPage = Math.max(0, Math.ceil(newTotal / (result?.size ?? 20)) - 1)
    const targetPage = Math.min(page, maxPage)
    if (targetPage !== page) {
      setPage(targetPage)
    } else {
      client.listAnonymousGroups(page).then(setResult)
    }
  }

  return (
    <Box>
      <Typography variant="h5" sx={{ mb: 2 }}>
        Anonymous Groups
      </Typography>

      {loading && <CircularProgress />}
      {error && <Alert severity="error">{error}</Alert>}

      {!loading && !error && result && (
        <>
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
            rows={result.items}
            keyFor={(g) => g.id}
            onRowClick={(g) => navigate(`/anonymous-groups/${g.id}`)}
            emptyMessage="No anonymous groups yet."
          />
          <TablePager page={page} totalPages={result.totalPages} onChange={setPage} />
        </>
      )}
    </Box>
  )
}
