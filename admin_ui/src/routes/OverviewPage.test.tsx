import { describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import type { Overview } from '../api/types'
import { renderPage } from '../test/render'
import { OverviewPage } from './OverviewPage'

const healthy: Overview = {
  version: '0.9.0',
  apis: [1],
  startedAt: '2026-11-20T10:00:00',
  requireApiKey: true,
  pushRelayUrl: 'https://relay.example',
  locationRetentionSeconds: 7 * 24 * 3600,
  locationSaveIntervalSeconds: 120,
  locationRetentionCron: '0 0 0 * * *',
  databaseUp: true,
  redisUp: true,
  counts: {
    groups: 4,
    members: 9,
    pushRegisteredMembers: 6,
    places: 3,
    groupsWithoutAdmin: 1,
    inactiveGroups: 2,
    savedLocations: 120,
    apiKeys: 5,
  },
  onlineMembers: 2,
  locationSave: { lastRunAt: '2026-11-20T10:00:00', lastCount: 3, overdue: false },
  locationRetention: { lastRunAt: null, lastCount: null, overdue: false },
}

function renderOverview(overview: Overview) {
  renderPage(<OverviewPage />, {
    client: { getOverview: vi.fn().mockResolvedValue(overview) },
    path: '/',
    route: '/',
  })
}

describe('OverviewPage', () => {
  it('shows the counts, linking to the groups they count', async () => {
    renderOverview(healthy)

    expect(await screen.findByText('2 online now')).toBeInTheDocument()
    expect(screen.getByText('6 of 9')).toBeInTheDocument()
    expect(screen.getByText('Without an admin').closest('a')).toHaveAttribute(
      'href',
      '/groups?admin=none',
    )
    expect(screen.getByText('Inactive').closest('a')).toHaveAttribute('href', '/groups?inactive=30')
    expect(screen.getByText('7 days')).toBeInTheDocument()
    expect(screen.getByText('· 3 saved')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('says what is wrong', async () => {
    renderOverview({
      ...healthy,
      redisUp: false,
      onlineMembers: null,
      locationSave: null,
      locationRetention: null,
      pushRelayUrl: null,
      requireApiKey: false,
    })

    expect(await screen.findByText(/Redis isn't answering/)).toBeInTheDocument()
    expect(screen.getByText(/No push relay/)).toBeInTheDocument()
    expect(screen.getByText(/Any app can use this server/)).toBeInTheDocument()
    expect(screen.getAllByText('Unknown')).toHaveLength(2)
    expect(screen.queryByText(/online now/)).not.toBeInTheDocument()
  })

  it('flags a late job', async () => {
    renderOverview({
      ...healthy,
      locationSave: { lastRunAt: '2026-11-20T10:00:00', lastCount: 0, overdue: true },
    })

    expect(await screen.findByText(/Positions haven't been saved for a while/)).toBeInTheDocument()
    expect(screen.getByText('Late')).toBeInTheDocument()
  })

  it('shows what it can without the database', async () => {
    renderOverview({ ...healthy, databaseUp: false, counts: null })

    expect(await screen.findByText(/The database isn't answering/)).toBeInTheDocument()
    expect(screen.queryByText('Groups')).not.toBeInTheDocument()
    expect(screen.getByText('0.9.0 (API v1)')).toBeInTheDocument()
  })
})
