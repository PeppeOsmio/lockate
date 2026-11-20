import { describe, expect, it } from 'vitest'
import { formatRelative, parseServerDate } from './format'

describe('parseServerDate', () => {
  it('reads a time without an offset as UTC', () => {
    expect(parseServerDate('2026-11-20T22:12:52').toISOString()).toBe('2026-11-20T22:12:52.000Z')
  })

  it('keeps an explicit offset', () => {
    expect(parseServerDate('2026-11-20T23:12:52+01:00').toISOString()).toBe(
      '2026-11-20T22:12:52.000Z',
    )
  })
})

describe('formatRelative', () => {
  const now = new Date('2026-11-20T12:00:00Z')

  it('says just now under a minute', () => {
    expect(formatRelative(new Date('2026-11-20T11:59:30Z'), now)).toBe('just now')
  })

  it('uses the largest whole unit', () => {
    expect(formatRelative(new Date('2026-11-20T11:57:00Z'), now)).toBe('3 minutes ago')
    expect(formatRelative(new Date('2026-11-20T09:00:00Z'), now)).toBe('3 hours ago')
    expect(formatRelative(new Date('2026-11-19T12:00:00Z'), now)).toBe('yesterday')
  })
})
