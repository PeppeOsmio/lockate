import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfirmDialog } from './ConfirmDialog'

describe('ConfirmDialog', () => {
  it('confirms only once the asked text is typed', async () => {
    const onConfirm = vi.fn().mockResolvedValue(undefined)
    const onClose = vi.fn()
    render(
      <ConfirmDialog
        title="Delete?"
        confirmLabel="Delete"
        typeToConfirm="abcd1234"
        onConfirm={onConfirm}
        onClose={onClose}
      >
        Gone for good.
      </ConfirmDialog>,
    )
    const confirm = screen.getByRole('button', { name: 'Delete' })
    expect(confirm).toBeDisabled()

    await userEvent.type(screen.getByLabelText('Type abcd1234 to confirm'), 'abcd1234')
    await userEvent.click(confirm)

    expect(onConfirm).toHaveBeenCalledOnce()
    expect(onClose).toHaveBeenCalledOnce()
  })

  it('stays open and shows the error when the action fails', async () => {
    const onClose = vi.fn()
    render(
      <ConfirmDialog
        title="Remove?"
        confirmLabel="Remove"
        onConfirm={() => Promise.reject(new Error('The server answered 500.'))}
        onClose={onClose}
      >
        Sure?
      </ConfirmDialog>,
    )

    await userEvent.click(screen.getByRole('button', { name: 'Remove' }))

    expect(await screen.findByText('The server answered 500.')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })
})
