import type { ReactNode } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import RefreshIcon from '@mui/icons-material/Refresh'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Link from '@mui/material/Link'
import CircularProgress from '@mui/material/CircularProgress'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useOverview } from '../api/queries'
import type { JobStatus, Overview } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { Stats } from '../components/Stats'
import { Time } from '../components/Time'
import { usePageTitle } from '../hooks/usePageTitle'
import { formatDuration } from '../utils/format'

// The days the server counts a group as inactive after
const INACTIVE_DAYS = 30

export function OverviewPage() {
  usePageTitle('Overview')
  const overview = useOverview()

  return (
    <>
      <PageHeader
        title="Overview"
        description="This server at a glance: whether it is healthy, how it is set up and what it holds."
        actions={
          <Button
            startIcon={<RefreshIcon />}
            onClick={() => overview.refetch()}
            disabled={overview.isFetching}
          >
            Refresh
          </Button>
        }
      />
      {overview.isPending && <CircularProgress />}
      {overview.isError && <Alert severity="error">{overview.error.message}</Alert>}
      {overview.data && <OverviewContent overview={overview.data} />}
    </>
  )
}

function OverviewContent({ overview }: { overview: Overview }) {
  const { counts } = overview
  return (
    <>
      <Problems overview={overview} />
      {counts && (
        <Stats
          stats={[
            {
              label: 'Groups',
              value: counts.groups,
              to: '/groups',
            },
            {
              label: 'Members',
              value: counts.members,
              detail: overview.onlineMembers !== null && `${overview.onlineMembers} online now`,
            },
            {
              label: 'Notifications',
              value: `${counts.pushRegisteredMembers} of ${counts.members}`,
              detail: 'members registered',
            },
            { label: 'Places', value: counts.places },
            {
              label: 'Without an admin',
              value: counts.groupsWithoutAdmin,
              detail: 'groups',
              to: '/groups?admin=none',
              warning: counts.groupsWithoutAdmin > 0,
            },
            {
              label: 'Inactive',
              value: counts.inactiveGroups,
              detail: `groups, no position for ${INACTIVE_DAYS} days`,
              to: `/groups?inactive=${INACTIVE_DAYS}`,
            },
            { label: 'Saved positions', value: counts.savedLocations },
            { label: 'API keys', value: counts.apiKeys, to: '/api-keys' },
          ]}
        />
      )}
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' },
          gap: 2,
        }}
      >
        <Section title="Server">
          <Field label="Version">
            {overview.version} (API {overview.apis.map((a) => `v${a}`).join(', ')})
          </Field>
          <Field label="Running since">
            <Time value={overview.startedAt} />
          </Field>
          <Field label="Database">
            <Health up={overview.databaseUp} />
          </Field>
          <Field label="Redis">
            <Health up={overview.redisUp} />
          </Field>
          <Field label="API keys required">{overview.requireApiKey ? 'Yes' : 'No'}</Field>
          <Field label="Push relay">
            {overview.pushRelayUrl ?? (
              <Typography component="span" variant="inherit" color="text.secondary">
                None
              </Typography>
            )}
          </Field>
        </Section>
        <Section title="Positions">
          <Field label="Kept for">{formatDuration(overview.locationRetentionSeconds)}</Field>
          <Field label="Saved every">{formatDuration(overview.locationSaveIntervalSeconds)}</Field>
          <Field label="Last save run">
            <Job status={overview.locationSave} count={(n) => `${n} saved`} />
          </Field>
          <Field label="Old ones deleted at (cron)">
            <Box component="span" sx={{ fontFamily: 'monospace' }}>
              {overview.locationRetentionCron}
            </Box>
          </Field>
          <Field label="Last deletion run">
            <Job status={overview.locationRetention} count={(n) => `${n} deleted`} />
          </Field>
        </Section>
      </Box>
    </>
  )
}

function Problems({ overview }: { overview: Overview }) {
  const problems: { severity: 'error' | 'warning' | 'info'; text: ReactNode }[] = []
  if (!overview.databaseUp) {
    problems.push({
      severity: 'error',
      text: "The database isn't answering: apps can't join, post or load their groups.",
    })
  }
  if (!overview.redisUp) {
    problems.push({
      severity: 'error',
      text: "Redis isn't answering: invites, live positions and presence don't work.",
    })
  }
  if (overview.locationSave?.overdue) {
    problems.push({
      severity: 'warning',
      text: "Positions haven't been saved for a while: the last ones would be lost on a restart. Check the backend's logs.",
    })
  }
  if (overview.locationRetention?.overdue) {
    problems.push({
      severity: 'warning',
      text: `Old positions weren't deleted when they should have been, so some are kept longer than ${formatDuration(overview.locationRetentionSeconds)}. Check the backend's logs.`,
    })
  }
  if (!overview.pushRelayUrl) {
    problems.push({
      severity: 'warning',
      text: 'No push relay (PUSH_RELAY_URL): apps get no notifications for place crossings, removals or deleted groups.',
    })
  }
  if (!overview.requireApiKey) {
    problems.push({
      severity: 'info',
      text: (
        <>
          Any app can use this server. To allow only apps with a key, set REQUIRE_API_KEY=true and
          give each one a key from{' '}
          <Link component={RouterLink} to="/api-keys" color="inherit">
            API keys
          </Link>
          .
        </>
      ),
    })
  }
  if (problems.length === 0) return null
  return (
    <Stack spacing={1} sx={{ mb: 3 }}>
      {problems.map((p, i) => (
        <Alert key={i} severity={p.severity}>
          {p.text}
        </Alert>
      ))}
    </Stack>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography variant="subtitle1" component="h2" sx={{ fontWeight: 600, mb: 1 }}>
        {title}
      </Typography>
      <Box component="dl" sx={{ m: 0 }}>
        {children}
      </Box>
    </Paper>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Box
      sx={{
        display: 'flex',
        justifyContent: 'space-between',
        gap: 2,
        py: 0.75,
        borderTop: 1,
        borderColor: 'divider',
        '&:first-of-type': { borderTop: 0 },
      }}
    >
      <Typography component="dt" variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography
        component="dd"
        variant="body2"
        sx={{ m: 0, textAlign: 'right', overflowWrap: 'anywhere' }}
      >
        {children}
      </Typography>
    </Box>
  )
}

function Health({ up }: { up: boolean }) {
  return (
    <Chip
      size="small"
      variant="outlined"
      color={up ? 'success' : 'error'}
      label={up ? 'Up' : 'Down'}
    />
  )
}

function Job({ status, count }: { status: JobStatus | null; count: (n: number) => string }) {
  if (!status) {
    return (
      <Typography component="span" variant="inherit" color="text.secondary">
        Unknown
      </Typography>
    )
  }
  return (
    <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 1 }}>
      <Time value={status.lastRunAt} />
      {status.lastCount !== null && (
        <Typography component="span" variant="inherit" color="text.secondary">
          · {count(status.lastCount)}
        </Typography>
      )}
      {status.overdue && <Chip size="small" color="warning" variant="outlined" label="Late" />}
    </Box>
  )
}
