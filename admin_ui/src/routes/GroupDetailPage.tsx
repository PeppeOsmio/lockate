import { useState } from 'react'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined'
import PersonRemoveOutlinedIcon from '@mui/icons-material/PersonRemoveOutlined'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Switch from '@mui/material/Switch'
import { isNotFound } from '../api/client'
import { useGroupMembers, useRemoveMember, useSetMemberAdmin } from '../api/queries'
import type { AGMember } from '../api/types'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { Id } from '../components/Id'
import { PageHeader } from '../components/PageHeader'
import { RowAction } from '../components/RowAction'
import { Table } from '../components/Table'
import { Time } from '../components/Time'
import { useNotify } from '../feedback/notify'
import { usePageTitle } from '../hooks/usePageTitle'
import { shortId } from '../utils/id'
import { DeleteGroupDialog } from './DeleteGroupDialog'

export function GroupDetailPage() {
  const { groupId = '' } = useParams<{ groupId: string }>()
  usePageTitle(`Group ${shortId(groupId)}`)
  const members = useGroupMembers(groupId)
  const removeMember = useRemoveMember(groupId)
  const setMemberAdmin = useSetMemberAdmin(groupId)
  const notify = useNotify()
  const navigate = useNavigate()
  const [deletingGroup, setDeletingGroup] = useState(false)
  const [removing, setRemoving] = useState<AGMember | null>(null)

  function toggleAdmin(member: AGMember, isAGAdmin: boolean) {
    setMemberAdmin.mutate(
      { memberId: member.id, isAGAdmin },
      {
        onSuccess: () =>
          notify(
            isAGAdmin
              ? `Member ${shortId(member.id)} is now an admin.`
              : `Member ${shortId(member.id)} is no longer an admin.`,
          ),
        onError: (e) =>
          notify(isNotFound(e) ? 'That member is no longer in the group.' : e.message, 'error'),
      },
    )
  }

  if (members.isError && isNotFound(members.error)) {
    return (
      <>
        <PageHeader
          title={`Group ${shortId(groupId)}`}
          crumb={shortId(groupId)}
          parent={{ label: 'Groups', to: '/groups' }}
        />
        <Alert
          severity="warning"
          action={
            <Button component={RouterLink} to="/groups" color="inherit" size="small">
              All groups
            </Button>
          }
        >
          This group does not exist, it may have been deleted.
        </Alert>
      </>
    )
  }

  return (
    <>
      <PageHeader
        title={
          <>
            Group <Id value={groupId} />
          </>
        }
        crumb={shortId(groupId)}
        parent={{ label: 'Groups', to: '/groups' }}
        actions={
          <Button
            color="error"
            variant="outlined"
            startIcon={<DeleteOutlinedIcon />}
            disabled={!members.data}
            onClick={() => setDeletingGroup(true)}
          >
            Delete group
          </Button>
        }
      />
      {members.isPending && <CircularProgress />}
      {members.isError && <Alert severity="error">{members.error.message}</Alert>}
      {members.data && (
        <Table
          columns={[
            { header: 'Member', render: (m) => <Id value={m.id} /> },
            { header: 'Joined', render: (m) => <Time value={m.createdAt} /> },
            {
              header: 'Admin',
              render: (m) => (
                <Switch
                  size="small"
                  checked={m.isAGAdmin}
                  disabled={setMemberAdmin.isPending}
                  onChange={(e) => toggleAdmin(m, e.target.checked)}
                  slotProps={{ input: { 'aria-label': `Admin ${m.id}` } }}
                />
              ),
            },
            { header: 'Last position saved', render: (m) => <Time value={m.lastLocationAt} /> },
            {
              render: (m) => (
                <RowAction
                  label="Remove member"
                  icon={<PersonRemoveOutlinedIcon fontSize="small" />}
                  onClick={() => setRemoving(m)}
                />
              ),
            },
          ]}
          rows={members.data}
          keyFor={(m) => m.id}
          emptyMessage="No members in this group."
          loading={members.isFetching}
        />
      )}
      {deletingGroup && members.data && (
        <DeleteGroupDialog
          groupId={groupId}
          memberCount={members.data.length}
          onDeleted={() => navigate('/groups')}
          onClose={() => setDeletingGroup(false)}
        />
      )}
      {removing && (
        <ConfirmDialog
          title={`Remove member ${shortId(removing.id)}?`}
          confirmLabel="Remove member"
          onConfirm={async () => {
            await removeMember.mutateAsync(removing.id)
            notify(`Member ${shortId(removing.id)} removed.`)
          }}
          onClose={() => setRemoving(null)}
        >
          The member leaves the group and its app is told by push (when the server has a push
          relay). It can come back only with a new invite.
        </ConfirmDialog>
      )}
    </>
  )
}
