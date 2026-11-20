import { useState } from 'react'
import { Navigate } from 'react-router-dom'
import AddIcon from '@mui/icons-material/Add'
import BlockIcon from '@mui/icons-material/Block'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Stack from '@mui/material/Stack'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import {
  useApiKeys,
  useCreateApiKey,
  useOverview,
  useRevokeApiKey,
  useUpdateApiKey,
} from '../api/queries'
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
import { formatDateTime, parseServerDate } from '../utils/format'
import { shortId } from '../utils/id'
import { ApiKeyDialog } from './ApiKeyDialog'

export function ApiKeysPage() {
  usePageTitle('API keys')
  const { page, size, setPage, setSize, lastPageSearch } = usePaging()
  const keys = useApiKeys(page, size)
  const createKey = useCreateApiKey()
  const revokeKey = useRevokeApiKey()
  const notify = useNotify()
  const [created, setCreated] = useState<ApiKeyCreated | null>(null)
  const [revoking, setRevoking] = useState<ApiKey | null>(null)
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<ApiKey | null>(null)
  const updateKey = useUpdateApiKey()
  const overview = useOverview()

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
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>
            Create key
          </Button>
        }
      />
      {overview.data && !overview.data.requireApiKey && (
        <Alert severity="info" sx={{ mb: 2 }}>
          This server runs with REQUIRE_API_KEY=false: any app can use it and these keys aren't
          checked.
        </Alert>
      )}
      {keys.isPending && <CircularProgress />}
      {keys.isError && <Alert severity="error">{keys.error.message}</Alert>}
      {keys.data && (
        <Table
          columns={[
            { header: 'Key', render: (k) => <Id value={k.id} /> },
            {
              header: 'Label',
              render: (k) =>
                k.label ?? (
                  <Typography variant="inherit" color="text.secondary">
                    None
                  </Typography>
                ),
            },
            { header: 'Created', render: (k) => <Time value={k.createdAt} /> },
            {
              header: 'Expires',
              render: (k) => <Expires value={k.expiresAt} checkedAt={keys.dataUpdatedAt} />,
            },
            {
              header: 'Last used',
              render: (k) => (
                <Tooltip title="Within 10 minutes" placement="top">
                  <span>
                    <Time value={k.lastValidated} />
                  </span>
                </Tooltip>
              ),
            },
            {
              render: (k) => (
                <Stack direction="row">
                  <RowAction
                    label="Edit key"
                    icon={<EditOutlinedIcon fontSize="small" />}
                    onClick={() => setEditing(k)}
                  />
                  <RowAction
                    label="Revoke key"
                    icon={<BlockIcon fontSize="small" />}
                    onClick={() => setRevoking(k)}
                  />
                </Stack>
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
      {creating && (
        <ApiKeyDialog
          onSave={async (body) => setCreated(await createKey.mutateAsync(body))}
          onClose={() => setCreating(false)}
        />
      )}
      {editing && (
        <ApiKeyDialog
          apiKey={editing}
          onSave={async (body) => {
            await updateKey.mutateAsync({ id: editing.id, body })
            notify(`Key ${shortId(editing.id)} saved.`)
          }}
          onClose={() => setEditing(null)}
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

// Expired as of when the list was loaded
function Expires({ value, checkedAt }: { value: string | null; checkedAt: number }) {
  if (value && parseServerDate(value).getTime() <= checkedAt) {
    return (
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
        <Chip size="small" color="error" variant="outlined" label="Expired" />
        <Time value={value} />
      </Stack>
    )
  }
  return <Time value={value} />
}

function CreatedKeyDialog({ apiKey, onClose }: { apiKey: ApiKeyCreated; onClose: () => void }) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    await navigator.clipboard.writeText(apiKey.key)
    setCopied(true)
  }

  return (
    <Dialog open maxWidth="sm" fullWidth>
      <DialogTitle>{apiKey.label ? `New API key: ${apiKey.label}` : 'New API key'}</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <Alert severity="warning">
          Copy the key now: the server keeps only its hash and can't show it again.
        </Alert>
        {apiKey.expiresAt && (
          <Typography variant="body2">
            It stops working {formatDateTime(parseServerDate(apiKey.expiresAt))}.
          </Typography>
        )}
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
