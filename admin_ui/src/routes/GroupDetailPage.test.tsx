import { describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError } from '../api/client'
import type { AGMember } from '../api/types'
import { renderPage } from '../test/render'
import { GroupDetailPage } from './GroupDetailPage'

const GROUP_ID = '449bfe1b-0596-41a5-b04e-cad6f9a08530'

const member: AGMember = {
  id: 'f7b09133-1337-4532-b508-7653b30ae0f4',
  createdAt: '2026-11-05T09:12:28',
  isAGAdmin: false,
  online: true,
  pushRegistered: false,
  lastLocationAt: null,
}

const admin: AGMember = {
  id: '22750cc3-c65b-43e9-a7c2-7db1e4ac1255',
  createdAt: '2026-11-01T09:12:28',
  isAGAdmin: true,
  online: false,
  pushRegistered: true,
  lastLocationAt: '2026-11-20T09:00:00',
}

const group = {
  id: GROUP_ID,
  createdAt: '2026-11-01T09:12:28',
  memberCount: 2,
  adminCount: 1,
  onlineCount: 1,
  pushRegisteredCount: 1,
  placeCount: 2,
  lastLocationAt: '2026-11-20T09:00:00',
}

describe('GroupDetailPage', () => {
  it('says when the group does not exist', async () => {
    renderPage(<GroupDetailPage />, {
      client: {
        getAnonymousGroup: vi.fn().mockRejectedValue(new ApiError(404, 'gone')),
        listGroupMembers: vi.fn().mockRejectedValue(new ApiError(404, 'gone')),
      },
      path: '/groups/:groupId',
      route: `/groups/${GROUP_ID}`,
    })

    expect(
      await screen.findByText('This group does not exist, it may have been deleted.'),
    ).toBeInTheDocument()
  })

  it('removes a member after confirming', async () => {
    const listGroupMembers = vi.fn().mockResolvedValueOnce([member]).mockResolvedValue([])
    const deleteGroupMember = vi.fn().mockResolvedValue(undefined)
    renderPage(<GroupDetailPage />, {
      client: {
        getAnonymousGroup: vi.fn().mockResolvedValue(group),
        listGroupMembers,
        deleteGroupMember,
      },
      path: '/groups/:groupId',
      route: `/groups/${GROUP_ID}`,
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Remove member' }))
    await userEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Remove member' }),
    )

    expect(deleteGroupMember).toHaveBeenCalledWith(GROUP_ID, member.id)
    expect(await screen.findByText('No members in this group.')).toBeInTheDocument()
  })

  it('shows who is online and asks before removing the last admin', async () => {
    const setGroupMemberAdmin = vi.fn().mockResolvedValue(undefined)
    renderPage(<GroupDetailPage />, {
      client: {
        getAnonymousGroup: vi.fn().mockResolvedValue(group),
        listGroupMembers: vi.fn().mockResolvedValue([admin, member]),
        setGroupMemberAdmin,
      },
      path: '/groups/:groupId',
      route: `/groups/${GROUP_ID}`,
    })

    const table = within(await screen.findByRole('table'))
    expect(table.getByText('Online')).toBeInTheDocument()
    expect(table.getByText('Offline')).toBeInTheDocument()
    await userEvent.click(screen.getByLabelText(`Admin ${admin.id}`))

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText('Remove the last admin?')).toBeInTheDocument()
    expect(setGroupMemberAdmin).not.toHaveBeenCalled()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Remove admin' }))
    expect(setGroupMemberAdmin).toHaveBeenCalledWith(GROUP_ID, admin.id, false)
  })

  it('makes another member admin at once', async () => {
    const setGroupMemberAdmin = vi.fn().mockResolvedValue(undefined)
    renderPage(<GroupDetailPage />, {
      client: {
        getAnonymousGroup: vi.fn().mockResolvedValue(group),
        listGroupMembers: vi.fn().mockResolvedValue([admin, member]),
        setGroupMemberAdmin,
      },
      path: '/groups/:groupId',
      route: `/groups/${GROUP_ID}`,
    })

    await userEvent.click(await screen.findByLabelText(`Admin ${member.id}`))

    expect(setGroupMemberAdmin).toHaveBeenCalledWith(GROUP_ID, member.id, true)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
