import { describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError, type AdminAPIClient } from '../api/client'
import type { AnonymousGroupSummary, Page } from '../api/types'
import { renderPage } from '../test/render'
import { GroupsPage } from './GroupsPage'

const GROUP_ID = '449bfe1b-0596-41a5-b04e-cad6f9a08530'

function page(items: AnonymousGroupSummary[]): Page<AnonymousGroupSummary> {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1 }
}

const group: AnonymousGroupSummary = {
  id: GROUP_ID,
  createdAt: '2026-11-20T22:12:28',
  memberCount: 3,
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
    expect(listAnonymousGroups).toHaveBeenLastCalledWith(0, 20)
  })
})
