import { useState } from 'react'
import { Navigate } from 'react-router-dom'
import AddIcon from '@mui/icons-material/Add'
import BlockIcon from '@mui/icons-material/Block'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import { useApiKeys, useCreateApiKey, useRevokeApiKey } from '../api/queries'
import type { ApiKey, ApiKeyCreated } from '../api/types'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { Id } from '../components/Id'
import { PageHeader } from '../components/PageHeader'
import { RowAction } from '../components/RowAction'
import { Table } from '../components/Table'
import { Time } from '../components/Time'
import { useNotify } from '../feedback/notify'
import { usePageTitle } from '../hooks/usePageTitle'
import { usePaging } from '../hooks/usePaging'
import { shortId } from '../utils/id'

export function ApiKeysPage() {
  usePageTitle('API keys')
  const { page, size, setPage, setSize, lastPageSearch } = usePaging()
  const keys = useApiKeys(page, size)
  const createKey = useCreateApiKey()
  const revokeKey = useRevokeApiKey()
  const notify = useNotify()
  const [created, setCreated] = useState<ApiKeyCreated | null>(null)
  const [revoking, setRevoking] = useState<ApiKey | null>(null)

  const fallback = keys.data ? lastPageSearch(keys.data.totalPages) : null
  if (fallback !== null) {
    return <Navigate to={{ search: fallback }} replace />
  }

  return (
    <>
      <PageHeader
        title="API keys"
        description="When the server runs with REQUIRE_API_KEY=true, apps can use it only with one of these keys, entered with the server's address."
        actions={
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            disabled={createKey.isPending}
            onClick={() =>
              createKey.mutate(undefined, {
                onSuccess: setCreated,
                onError: (e) => notify(e.message, 'error'),
              })
            }
          >
            Create key
          </Button>
        }
      />
      {keys.isPending && <CircularProgress />}
      {keys.isError && <Alert severity="error">{keys.error.message}</Alert>}
      {keys.data && (
        <Table
          columns={[
            { header: 'Key', render: (k) => <Id value={k.id} /> },
            { header: 'Created', render: (k) => <Time value={k.createdAt} /> },
            { header: 'Last used', render: (k) => <Time value={k.lastValidated} /> },
            {
              render: (k) => (
                <RowAction
                  label="Revoke key"
                  icon={<BlockIcon fontSize="small" />}
                  onClick={() => setRevoking(k)}
                />
              ),
            },
          ]}
          rows={keys.data.items}
          keyFor={(k) => k.id}
          emptyMessage="No API keys yet."
          loading={keys.isFetching}
          pagination={{
            page,
            size,
            total: keys.data.totalElements,
            onPageChange: setPage,
            onSizeChange: setSize,
          }}
        />
      )}
      {created && <CreatedKeyDialog apiKey={created} onClose={() => setCreated(null)} />}
      {revoking && (
        <ConfirmDialog
          title={`Revoke key ${shortId(revoking.id)}?`}
          confirmLabel="Revoke key"
          onConfirm={async () => {
            await revokeKey.mutateAsync(revoking.id)
            notify(`Key ${shortId(revoking.id)} revoked.`)
          }}
          onClose={() => setRevoking(null)}
        >
          Apps using this key can no longer reach the server until they are given another one.
        </ConfirmDialog>
      )}
    </>
  )
}

function CreatedKeyDialog({ apiKey, onClose }: { apiKey: ApiKeyCreated; onClose: () => void }) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    await navigator.clipboard.writeText(apiKey.key)
    setCopied(true)
  }

  return (
    <Dialog open maxWidth="sm" fullWidth>
      <DialogTitle>New API key</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <Alert severity="warning">
          Copy the key now: the server keeps only its hash and can't show it again.
        </Alert>
        <Box
          component="code"
          sx={{
            p: 1.5,
            borderRadius: 1,
            bgcolor: 'action.hover',
            wordBreak: 'break-all',
            fontSize: '0.875rem',
          }}
        >
          {apiKey.key}
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={copy}>{copied ? 'Copied' : 'Copy'}</Button>
        <Button variant="contained" onClick={onClose}>
          Done
        </Button>
      </DialogActions>
    </Dialog>
  )
}
