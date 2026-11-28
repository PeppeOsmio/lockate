import { describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError, type AdminAPIClient } from '../api/client'
import type { AdminGroup, Page } from '../api/types'
import { renderPage } from '../test/render'
import { GroupsPage } from './GroupsPage'

const GROUP_ID = '449bfe1b-0596-41a5-b04e-cad6f9a08530'

function page(items: AdminGroup[]): Page<AdminGroup> {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1 }
}

const group: AdminGroup = {
  id: GROUP_ID,
  createdAt: '2026-11-20T22:12:28',
  memberCount: 3,
  adminCount: 0,
  onlineCount: 2,
  pushRegisteredCount: 1,
  placeCount: 4,
  lastLocationAt: null,
}

describe('GroupsPage', () => {
  it('lists groups by short id', async () => {
    const client: Partial<AdminAPIClient> = {
      listAnonymousGroups: vi.fn().mockResolvedValue(page([group])),
    }
    renderPage(<GroupsPage />, { client, path: '/groups', route: '/groups' })

    expect(await screen.findByText('449bfe1b')).toBeInTheDocument()
    expect(screen.getByText('Never')).toBeInTheDocument()
    expect(screen.getByText('2 online')).toBeInTheDocument()
    expect(screen.getByText('None')).toBeInTheDocument()
    expect(screen.getByText('1 of 3')).toBeInTheDocument()
  })

  it('searches, sorts and filters through the URL', async () => {
    const listAnonymousGroups = vi.fn().mockResolvedValue(page([group]))
    renderPage(<GroupsPage />, {
      client: { listAnonymousGroups },
      path: '/groups',
      route: '/groups?sort=activity&inactive=30&page=1',
    })
    await screen.findByText('449bfe1b')
    expect(listAnonymousGroups).toHaveBeenCalledWith({
      page: 0,
      size: 20,
      q: undefined,
      sort: 'activity',
      inactiveDays: 30,
    })

    await userEvent.type(screen.getByLabelText('Search by group or member id'), '449BFE')

    await vi.waitFor(() =>
      expect(listAnonymousGroups).toHaveBeenLastCalledWith({
        page: 0,
        size: 20,
        q: '449bfe',
        sort: 'activity',
        inactiveDays: 30,
      }),
    )
  })

  it('keeps only the groups without an admin', async () => {
    const listAnonymousGroups = vi.fn().mockResolvedValue(page([group]))
    renderPage(<GroupsPage />, {
      client: { listAnonymousGroups },
      path: '/groups',
      route: '/groups?admin=none',
    })

    await screen.findByText('449bfe1b')
    expect(listAnonymousGroups).toHaveBeenCalledWith(expect.objectContaining({ noAdmin: true }))
  })

  it('does not search for what no id can start with', async () => {
    const listAnonymousGroups = vi.fn().mockResolvedValue(page([group]))
    renderPage(<GroupsPage />, {
      client: { listAnonymousGroups },
      path: '/groups',
      route: '/groups',
    })
    await screen.findByText('449bfe1b')

    await userEvent.type(screen.getByLabelText('Search by group or member id'), 'xyz')

    expect(screen.getByText('Ids have only 0-9, a-f and dashes')).toBeInTheDocument()
    await new Promise((resolve) => setTimeout(resolve, 400))
    expect(listAnonymousGroups).toHaveBeenCalledTimes(1)
  })

  it('deletes the selected groups', async () => {
    const other = { ...group, id: '81d4d5a8-e8bc-4038-80eb-385be3236a50' }
    const listAnonymousGroups = vi
      .fn()
      .mockResolvedValueOnce(page([group, other]))
      .mockResolvedValue(page([]))
    const deleteAnonymousGroup = vi.fn().mockResolvedValue(undefined)
    renderPage(<GroupsPage />, {
      client: { listAnonymousGroups, deleteAnonymousGroup },
      path: '/groups',
      route: '/groups',
    })

    await userEvent.click(await screen.findByLabelText('Select all on this page'))
    expect(screen.getByText('2 groups selected')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Delete selected' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Type 2 to confirm'), '2')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete groups' }))

    expect(deleteAnonymousGroup).toHaveBeenCalledWith(GROUP_ID)
    expect(deleteAnonymousGroup).toHaveBeenCalledWith(other.id)
    expect(await screen.findByText('2 groups deleted.')).toBeInTheDocument()
  })

  it('deletes a group after its short id is typed', async () => {
    const listAnonymousGroups = vi
      .fn()
      .mockResolvedValueOnce(page([group]))
      .mockResolvedValue(page([]))
    const deleteAnonymousGroup = vi.fn().mockResolvedValue(undefined)
    renderPage(<GroupsPage />, {
      client: { listAnonymousGroups, deleteAnonymousGroup },
      path: '/groups',
      route: '/groups',
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Delete group' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Type 449bfe1b to confirm'), '449bfe1b')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete group' }))

    expect(deleteAnonymousGroup).toHaveBeenCalledWith(GROUP_ID)
    expect(await screen.findByText('No groups yet.')).toBeInTheDocument()
    expect(screen.getByText('Group 449bfe1b deleted.')).toBeInTheDocument()
  })

  it('counts a group already gone as deleted', async () => {
    const deleteAnonymousGroup = vi.fn().mockRejectedValue(new ApiError(404, 'gone'))
    renderPage(<GroupsPage />, {
      client: {
        listAnonymousGroups: vi.fn().mockResolvedValue(page([group])),
        deleteAnonymousGroup,
      },
      path: '/groups',
      route: '/groups',
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Delete group' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Type 449bfe1b to confirm'), '449bfe1b')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete group' }))

    expect(await screen.findByText('Group 449bfe1b deleted.')).toBeInTheDocument()
  })

  it('goes back to the last page when the asked one is past the end', async () => {
    const listAnonymousGroups = vi.fn().mockResolvedValue(page([group]))
    renderPage(<GroupsPage />, {
      client: { listAnonymousGroups },
      path: '/groups',
      route: '/groups?page=5',
    })

    expect(await screen.findByText('449bfe1b')).toBeInTheDocument()
    expect(listAnonymousGroups).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 0, size: 20 }),
    )
  })
})
