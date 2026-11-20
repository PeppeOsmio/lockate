import { useEffect, useState } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Typography from '@mui/material/Typography'
import { useAdminApiClient } from '../api/AdminAPIClientContext'
import { isNotFound } from '../api/client'
import type { ApiKey, ApiKeyCreated, Page } from '../api/types'
import { ConfirmButton } from '../components/ConfirmButton'
import { Table } from '../components/Table'
import { TablePager } from '../components/TablePager'
import { formatDate } from '../utils/format'

export function ApiKeysPage() {
  const [result, setResult] = useState<Page<ApiKey> | null>(null)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [revealedKey, setRevealedKey] = useState<ApiKeyCreated | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [reloads, setReloads] = useState(0)
  const client = useAdminApiClient()

  useEffect(() => {
    setLoading(true)
    setError(null)
    client
      .listApiKeys(page)
      .then(setResult)
      .catch(() => setError('Failed to load API keys.'))
      .finally(() => setLoading(false))
  }, [client, page, reloads])

  async function handleCreate() {
    setActionError(null)
    try {
      setRevealedKey(await client.createApiKey())
    } catch {
      setActionError('Failed to create the API key.')
      return
    }
    setReloads((n) => n + 1)
  }

  async function handleRevoke(id: string) {
    setActionError(null)
    try {
      await client.revokeApiKey(id)
    } catch (e) {
      // a key already gone is what was asked
      if (!isNotFound(e)) {
        setActionError('Failed to revoke the API key.')
        return
      }
    }
    const newTotal = (result?.totalElements ?? 1) - 1
    const maxPage = Math.max(0, Math.ceil(newTotal / (result?.size ?? 20)) - 1)
    const targetPage = Math.min(page, maxPage)
    if (targetPage !== page) {
      setPage(targetPage)
    } else {
      setReloads((n) => n + 1)
    }
  }

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <Typography variant="h5">API Keys</Typography>
        <Button variant="contained" onClick={handleCreate}>
          Create key
        </Button>
      </Box>

      {revealedKey && (
        <Alert severity="success" onClose={() => setRevealedKey(null)} sx={{ mb: 2 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
            <strong>New key — shown once:</strong>
            <code style={{ wordBreak: 'break-all' }}>{revealedKey.key}</code>
            <Button
              size="small"
              variant="outlined"
              color="success"
              onClick={() => navigator.clipboard.writeText(revealedKey.key)}
            >
              Copy
            </Button>
          </Box>
        </Alert>
      )}

      {loading && <CircularProgress />}
      {error && <Alert severity="error">{error}</Alert>}
      {actionError && (
        <Alert severity="error" onClose={() => setActionError(null)} sx={{ mb: 2 }}>
          {actionError}
        </Alert>
      )}

      {!loading && !error && result && (
        <>
          <Table
            columns={[
              { header: 'ID', render: (k) => <code>{k.id}</code> },
              { header: 'Created', render: (k) => formatDate(k.createdAt) },
              { header: 'Last validated', render: (k) => formatDate(k.lastValidated) },
              {
                header: '',
                render: (k) => (
                  <ConfirmButton
                    label="Revoke"
                    confirmLabel="Confirm revoke"
                    onConfirm={() => handleRevoke(k.id)}
                  />
                ),
              },
            ]}
            rows={result.items}
            keyFor={(k) => k.id}
            emptyMessage="No API keys yet."
          />
          <TablePager page={page} totalPages={result.totalPages} onChange={setPage} />
        </>
      )}
    </Box>
  )
}
