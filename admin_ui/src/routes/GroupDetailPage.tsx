import { useState } from 'react'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import CheckIcon from '@mui/icons-material/Check'
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined'
import PersonRemoveOutlinedIcon from '@mui/icons-material/PersonRemoveOutlined'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Switch from '@mui/material/Switch'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import { isNotFound } from '../api/client'
import { useGroup, useGroupMembers, useRemoveMember, useSetMemberAdmin } from '../api/queries'
import type { AdminGroup, AGMember } from '../api/types'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { Id } from '../components/Id'
import { PageHeader } from '../components/PageHeader'
import { RowAction } from '../components/RowAction'
import { Stats } from '../components/Stats'
import { Table } from '../components/Table'
import { Time } from '../components/Time'
import { useNotify } from '../feedback/notify'
import { usePageTitle } from '../hooks/usePageTitle'
import { shortId } from '../utils/id'
import { DeleteGroupDialog } from './DeleteGroupDialog'

const LAST_ADMIN_WARNING =
  'Nobody in the group could then delete it or make other members admins: only you, from here.'

export function GroupDetailPage() {
  const { groupId = '' } = useParams<{ groupId: string }>()
  usePageTitle(`Group ${shortId(groupId)}`)
  const group = useGroup(groupId)
  const members = useGroupMembers(groupId)
  const removeMember = useRemoveMember(groupId)
  const setMemberAdmin = useSetMemberAdmin(groupId)
  const notify = useNotify()
  const navigate = useNavigate()
  const [deletingGroup, setDeletingGroup] = useState(false)
  const [removing, setRemoving] = useState<AGMember | null>(null)
  const [demotingLastAdmin, setDemotingLastAdmin] = useState<AGMember | null>(null)
  const admins = members.data?.filter((m) => m.isAGAdmin).length ?? 0

  async function setAdmin(member: AGMember, isAGAdmin: boolean) {
    try {
      await setMemberAdmin.mutateAsync({ memberId: member.id, isAGAdmin })
      notify(
        isAGAdmin
          ? `Member ${shortId(member.id)} is now an admin.`
          : `Member ${shortId(member.id)} is no longer an admin.`,
      )
    } catch (e) {
      notify(
        isNotFound(e) ? 'That member is no longer in the group.' : (e as Error).message,
        'error',
      )
    }
  }

  function toggleAdmin(member: AGMember, isAGAdmin: boolean) {
    if (!isAGAdmin && admins === 1) setDemotingLastAdmin(member)
    else void setAdmin(member, isAGAdmin)
  }

  if (
    (members.isError && isNotFound(members.error)) ||
    (group.isError && isNotFound(group.error))
  ) {
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
      {group.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {group.error.message}
        </Alert>
      )}
      {group.data && <GroupStats group={group.data} />}
      {members.isPending && <CircularProgress />}
      {members.isError && <Alert severity="error">{members.error.message}</Alert>}
      {members.data && admins === 0 && members.data.length > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          The group has no admin: nobody in it can delete it or make other members admins, only you
          from here.
        </Alert>
      )}
      {members.data && (
        <Table
          columns={[
            { header: 'Member', render: (m) => <Id value={m.id} /> },
            { header: 'Joined', render: (m) => <Time value={m.createdAt} /> },
            { header: 'Status', render: (m) => <Online online={m.online} /> },
            {
              header: 'Notifications',
              render: (m) =>
                m.pushRegistered ? (
                  <Tooltip title="Its app registered for push notifications">
                    <CheckIcon fontSize="small" color="success" aria-label="Registered" />
                  </Tooltip>
                ) : (
                  <Typography variant="inherit" color="text.secondary">
                    Not registered
                  </Typography>
                ),
            },
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
            { header: 'Last position', render: (m) => <Time value={m.lastLocationAt} /> },
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
          {removing.isAGAdmin && admins === 1 && (members.data?.length ?? 0) > 1 && (
            <Alert severity="warning" sx={{ mt: 2 }}>
              This is the group's last admin. {LAST_ADMIN_WARNING}
            </Alert>
          )}
        </ConfirmDialog>
      )}
      {demotingLastAdmin && (
        <ConfirmDialog
          title="Remove the last admin?"
          confirmLabel="Remove admin"
          onConfirm={() => setAdmin(demotingLastAdmin, false)}
          onClose={() => setDemotingLastAdmin(null)}
        >
          Member {shortId(demotingLastAdmin.id)} is the group's only admin. {LAST_ADMIN_WARNING}
        </ConfirmDialog>
      )}
    </>
  )
}

function Online({ online }: { online: boolean }) {
  return (
    <Tooltip
      title={online ? 'Sent a position in the last 30 minutes' : 'No position for 30 minutes'}
    >
      <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.75 }}>
        <Box
          component="span"
          sx={{
            width: 8,
            height: 8,
            borderRadius: '50%',
            bgcolor: online ? 'success.main' : 'action.disabled',
          }}
        />
        {online ? 'Online' : 'Offline'}
      </Box>
    </Tooltip>
  )
}

function GroupStats({ group }: { group: AdminGroup }) {
  return (
    <Stats
      stats={[
        { label: 'Created', value: <Time value={group.createdAt} /> },
        { label: 'Members', value: group.memberCount },
        { label: 'Online', value: group.onlineCount },
        { label: 'Admins', value: group.adminCount, warning: group.adminCount === 0 },
        { label: 'Circles', value: group.circleCount },
        { label: 'Notifications', value: `${group.pushRegisteredCount} of ${group.memberCount}` },
        { label: 'Last position', value: <Time value={group.lastLocationAt} /> },
      ]}
    />
  )
}
