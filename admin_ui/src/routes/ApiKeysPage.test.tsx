import { describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { AdminAPIClient } from '../api/client'
import type { ApiKey, Overview, Page } from '../api/types'
import { renderPage } from '../test/render'
import { ApiKeysPage } from './ApiKeysPage'

const KEY_ID = '7c0e4a52-9a43-4a5e-8f3c-1f2a3b4c5d6e'

const key: ApiKey = {
  id: KEY_ID,
  label: 'Family phones',
  createdAt: '2026-11-01T10:00:00',
  expiresAt: '2026-11-02T10:00:00',
  lastValidated: null,
}

function page(items: ApiKey[]): Page<ApiKey> {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1 }
}

function renderKeys(client: Partial<AdminAPIClient>, requireApiKey = true) {
  renderPage(<ApiKeysPage />, {
    client: {
      listApiKeys: vi.fn().mockResolvedValue(page([key])),
      getOverview: vi.fn().mockResolvedValue({ requireApiKey } as Overview),
      ...client,
    },
    path: '/api-keys',
    route: '/api-keys',
  })
}

describe('ApiKeysPage', () => {
  it('shows each key with its label and flags an expired one', async () => {
    renderKeys({})

    expect(await screen.findByText('Family phones')).toBeInTheDocument()
    expect(screen.getByText('Expired')).toBeInTheDocument()
    expect(screen.queryByText(/REQUIRE_API_KEY=false/)).not.toBeInTheDocument()
  })

  it('says when keys are not checked', async () => {
    renderKeys({}, false)

    expect(await screen.findByText(/REQUIRE_API_KEY=false/)).toBeInTheDocument()
  })

  it('creates a key with a label and an expiry, then shows it once', async () => {
    const createApiKey = vi.fn().mockResolvedValue({
      ...key,
      label: 'Grandma',
      expiresAt: null,
      key: `${KEY_ID}:s3cret`,
    })
    renderKeys({ createApiKey })
    await screen.findByText('Family phones')

    await userEvent.click(screen.getByRole('button', { name: 'Create key' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Label'), '  Grandma ')
    await userEvent.click(within(dialog).getByLabelText('Expires'))
    await userEvent.click(screen.getByRole('option', { name: 'In 30 days' }))
    const before = Date.now()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create key' }))

    expect(await screen.findByText(`${KEY_ID}:s3cret`)).toBeInTheDocument()
    const body = createApiKey.mock.calls[0][0]
    expect(body.label).toBe('Grandma')
    const expires = new Date(`${body.expiresAt}Z`).getTime()
    expect(expires - before).toBeGreaterThan(29.9 * 24 * 3600 * 1000)
    expect(expires - before).toBeLessThan(30.1 * 24 * 3600 * 1000)
  })

  it('edits a key to never expire', async () => {
    const updateApiKey = vi.fn().mockResolvedValue(key)
    renderKeys({ updateApiKey })
    await screen.findByText('Family phones')

    await userEvent.click(screen.getByRole('button', { name: 'Edit key' }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByLabelText('Label')).toHaveValue('Family phones')
    await userEvent.click(within(dialog).getByLabelText('Expires'))
    await userEvent.click(screen.getByRole('option', { name: 'Never' }))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(updateApiKey).toHaveBeenCalledWith(KEY_ID, { label: 'Family phones', expiresAt: null })
    expect(await screen.findByText(`Key ${KEY_ID.slice(0, 8)} saved.`)).toBeInTheDocument()
  })
})
