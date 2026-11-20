import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined'
import Alert from '@mui/material/Alert'
import CircularProgress from '@mui/material/CircularProgress'
import { useGroups } from '../api/queries'
import type { AnonymousGroupSummary } from '../api/types'
import { Id } from '../components/Id'
import { PageHeader } from '../components/PageHeader'
import { RowAction } from '../components/RowAction'
import { Table } from '../components/Table'
import { Time } from '../components/Time'
import { usePageTitle } from '../hooks/usePageTitle'
import { usePaging } from '../hooks/usePaging'
import { DeleteGroupDialog } from './DeleteGroupDialog'

export function GroupsPage() {
  usePageTitle('Groups')
  const { page, size, setPage, setSize, lastPageSearch } = usePaging()
  const groups = useGroups(page, size)
  const navigate = useNavigate()
  const [deleting, setDeleting] = useState<AnonymousGroupSummary | null>(null)

  const fallback = groups.data ? lastPageSearch(groups.data.totalPages) : null
  if (fallback !== null) {
    return <Navigate to={{ search: fallback }} replace />
  }

  return (
    <>
      <PageHeader
        title="Groups"
        description="Groups on this server. Names, members' names and positions are encrypted on the phones, so only ids, counts and times are shown."
      />
      {groups.isPending && <CircularProgress />}
      {groups.isError && <Alert severity="error">{groups.error.message}</Alert>}
      {groups.data && (
        <Table
          columns={[
            { header: 'Group', render: (g) => <Id value={g.id} /> },
            { header: 'Created', render: (g) => <Time value={g.createdAt} /> },
            { header: 'Members', render: (g) => g.memberCount },
            { header: 'Last position saved', render: (g) => <Time value={g.lastLocationAt} /> },
            {
              render: (g) => (
                <RowAction
                  label="Delete group"
                  icon={<DeleteOutlinedIcon fontSize="small" />}
                  onClick={() => setDeleting(g)}
                />
              ),
            },
          ]}
          rows={groups.data.items}
          keyFor={(g) => g.id}
          onRowClick={(g) => navigate(`/groups/${g.id}`)}
          emptyMessage="No groups yet."
          loading={groups.isFetching}
          pagination={{
            page,
            size,
            total: groups.data.totalElements,
            onPageChange: setPage,
            onSizeChange: setSize,
          }}
        />
      )}
      {deleting && (
        <DeleteGroupDialog
          groupId={deleting.id}
          memberCount={deleting.memberCount}
          onClose={() => setDeleting(null)}
        />
      )}
    </>
  )
}
