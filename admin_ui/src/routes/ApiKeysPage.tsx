import { useEffect, useState } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Typography from '@mui/material/Typography'
import { createApiKey, listApiKeys, revokeApiKey } from '../api/apiKeys'
import type { ApiKey, ApiKeyCreated } from '../api/types'
import { ConfirmButton } from '../components/ConfirmButton'
import { Table } from '../components/Table'
import { formatDate } from '../utils/format'

export function ApiKeysPage() {
  const [keys, setKeys] = useState<ApiKey[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [revealedKey, setRevealedKey] = useState<ApiKeyCreated | null>(null)

  function refresh() {
    setLoading(true)
    listApiKeys()
      .then(setKeys)
      .catch(() => setError('Failed to load API keys.'))
      .finally(() => setLoading(false))
  }

  useEffect(refresh, [])

  async function handleCreate() {
    const created = await createApiKey()
    setRevealedKey(created)
    refresh()
  }

  async function handleRevoke(id: string) {
    await revokeApiKey(id)
    refresh()
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
            <strong>New key created — this won't be shown again:</strong>
            <code>{revealedKey.key}</code>
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

      {!loading && !error && (
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
          rows={keys}
          keyFor={(k) => k.id}
          emptyMessage="No API keys yet."
        />
      )}
    </Box>
  )
}
