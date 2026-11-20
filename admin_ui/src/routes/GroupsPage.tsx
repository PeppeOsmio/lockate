import { useEffect, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined'
import SearchIcon from '@mui/icons-material/Search'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import InputAdornment from '@mui/material/InputAdornment'
import MenuItem from '@mui/material/MenuItem'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Tooltip from '@mui/material/Tooltip'
import { useDeleteGroups, useGroups } from '../api/queries'
import type { AdminGroup, GroupSort } from '../api/types'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { Id } from '../components/Id'
import { PageHeader } from '../components/PageHeader'
import { RowAction } from '../components/RowAction'
import { Table } from '../components/Table'
import { Time } from '../components/Time'
import { useNotify } from '../feedback/notify'
import { ID_PREFIX, INACTIVE_DAYS, SORTS, useGroupFilters } from '../hooks/useGroupFilters'
import { usePageTitle } from '../hooks/usePageTitle'
import { usePaging } from '../hooks/usePaging'
import { DeleteGroupDialog } from './DeleteGroupDialog'

export function GroupsPage() {
  usePageTitle('Groups')
  const { page, size, setPage, setSize, lastPageSearch } = usePaging()
  const filters = useGroupFilters()
  const query = {
    page,
    size,
    q: filters.q || undefined,
    sort: filters.sort,
    inactiveDays: filters.inactiveDays,
    noAdmin: filters.noAdmin || undefined,
  }
  const groups = useGroups(query)
  const deleteGroups = useDeleteGroups()
  const notify = useNotify()
  const navigate = useNavigate()
  const [deleting, setDeleting] = useState<AdminGroup | null>(null)
  const [deletingSelected, setDeletingSelected] = useState(false)
  // Kept with the list it was made on, so another page or filter starts with nothing selected
  const listKey = JSON.stringify(query)
  const [selection, setSelection] = useState({ listKey, ids: new Set<string>() })
  const selected = selection.listKey === listKey ? selection.ids : new Set<string>()

  const fallback = groups.data ? lastPageSearch(groups.data.totalPages) : null
  if (fallback !== null) {
    return <Navigate to={{ search: fallback }} replace />
  }

  const filtered = !!filters.q || !!filters.inactiveDays || filters.noAdmin

  return (
    <>
      <PageHeader
        title="Groups"
        description="Groups on this server. Names, members' names and positions are encrypted on the phones, so only ids, counts and times are shown."
      />
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1.5}
        useFlexGap
        sx={{ mb: 2, flexWrap: 'wrap' }}
      >
        <SearchField value={filters.q} onSearch={filters.setQ} />
        <TextField
          select
          size="small"
          label="Sort"
          value={filters.sort}
          onChange={(e) => filters.setSort(e.target.value as GroupSort)}
          sx={{ minWidth: 170 }}
        >
          {SORTS.map((s) => (
            <MenuItem key={s.value} value={s.value}>
              {s.label}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          size="small"
          label="Activity"
          value={filters.inactiveDays ?? 0}
          onChange={(e) => filters.setInactiveDays(Number(e.target.value) || undefined)}
          sx={{ minWidth: 190 }}
        >
          <MenuItem value={0}>Any</MenuItem>
          {INACTIVE_DAYS.map((days) => (
            <MenuItem key={days} value={days}>
              No position for {days} days
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          size="small"
          label="Admins"
          value={filters.noAdmin ? 'none' : 'any'}
          onChange={(e) => filters.setNoAdmin(e.target.value === 'none')}
          sx={{ minWidth: 130 }}
        >
          <MenuItem value="any">Any</MenuItem>
          <MenuItem value="none">No admin</MenuItem>
        </TextField>
      </Stack>
      {selected.size > 0 && (
        <Alert
          severity="info"
          icon={false}
          sx={{ mb: 2, alignItems: 'center' }}
          action={
            <Stack direction="row" spacing={1}>
              <Button
                color="inherit"
                size="small"
                onClick={() => setSelection({ listKey, ids: new Set() })}
              >
                Clear
              </Button>
              <Button
                color="error"
                variant="contained"
                size="small"
                startIcon={<DeleteOutlinedIcon />}
                onClick={() => setDeletingSelected(true)}
              >
                Delete selected
              </Button>
            </Stack>
          }
        >
          {selected.size} {selected.size === 1 ? 'group' : 'groups'} selected
        </Alert>
      )}
      {groups.isPending && <CircularProgress />}
      {groups.isError && <Alert severity="error">{groups.error.message}</Alert>}
      {groups.data && (
        <Table
          columns={[
            { header: 'Group', render: (g) => <Id value={g.id} /> },
            { header: 'Created', render: (g) => <Time value={g.createdAt} /> },
            { header: 'Members', render: (g) => <Members group={g} /> },
            { header: 'Admins', render: (g) => <Admins group={g} /> },
            { header: 'Circles', render: (g) => g.circleCount },
            {
              header: 'Notifications',
              render: (g) => (
                <Tooltip title="Members whose app registered for push notifications">
                  <span>
                    {g.pushRegisteredCount} of {g.memberCount}
                  </span>
                </Tooltip>
              ),
            },
            { header: 'Last position', render: (g) => <Time value={g.lastLocationAt} /> },
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
          emptyMessage={filtered ? 'No groups match.' : 'No groups yet.'}
          loading={groups.isFetching}
          selection={{ selected, onChange: (ids) => setSelection({ listKey, ids }) }}
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
      {deletingSelected && (
        <ConfirmDialog
          title={`Delete ${selected.size} ${selected.size === 1 ? 'group' : 'groups'}?`}
          confirmLabel="Delete groups"
          typeToConfirm={String(selected.size)}
          onConfirm={async () => {
            const { deleted, failed } = await deleteGroups.mutateAsync([...selected])
            setSelection({ listKey, ids: new Set() })
            if (failed > 0) {
              notify(`${deleted} deleted, ${failed} could not be deleted.`, 'error')
            } else {
              notify(`${deleted} ${deleted === 1 ? 'group' : 'groups'} deleted.`)
            }
          }}
          onClose={() => setDeletingSelected(false)}
        >
          The groups, their members, circles and saved positions are deleted for good, and the
          members' apps are told by push (when the server has a push relay). This can't be undone.
        </ConfirmDialog>
      )}
    </>
  )
}

function Members({ group }: { group: AdminGroup }) {
  return (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
      <span>{group.memberCount}</span>
      {group.onlineCount > 0 && (
        <Tooltip title="Members that sent a position in the last 30 minutes">
          <Chip
            size="small"
            color="success"
            variant="outlined"
            label={`${group.onlineCount} online`}
          />
        </Tooltip>
      )}
    </Stack>
  )
}

function Admins({ group }: { group: AdminGroup }) {
  if (group.adminCount === 0 && group.memberCount > 0) {
    return (
      <Tooltip title="Nobody in the group can delete it or make others admins">
        <Chip size="small" color="warning" variant="outlined" label="None" />
      </Tooltip>
    )
  }
  return <>{group.adminCount}</>
}

// Searches once typing pauses, and only for what an id can start with
function SearchField({ value, onSearch }: { value: string; onSearch: (q: string) => void }) {
  const [text, setText] = useState(value)
  const normalized = text.trim().toLowerCase()
  const valid = normalized === '' || ID_PREFIX.test(normalized)

  useEffect(() => {
    if (!valid || normalized === value) return
    const timer = setTimeout(() => onSearch(normalized), 300)
    return () => clearTimeout(timer)
  }, [normalized, valid, value, onSearch])

  return (
    <TextField
      size="small"
      placeholder="Group or member id"
      value={text}
      onChange={(e) => setText(e.target.value)}
      error={!valid}
      helperText={valid ? undefined : 'Ids have only 0-9, a-f and dashes'}
      sx={{ flex: 1, minWidth: { sm: 220 }, maxWidth: { sm: 360 } }}
      slotProps={{
        input: {
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon fontSize="small" />
            </InputAdornment>
          ),
        },
        htmlInput: { 'aria-label': 'Search by group or member id', spellCheck: false },
      }}
    />
  )
}
