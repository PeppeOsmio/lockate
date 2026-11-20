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
  lastLocationAt: null,
}

describe('GroupDetailPage', () => {
  it('says when the group does not exist', async () => {
    renderPage(<GroupDetailPage />, {
      client: { listGroupMembers: vi.fn().mockRejectedValue(new ApiError(404, 'gone')) },
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
      client: { listGroupMembers, deleteGroupMember },
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
})
