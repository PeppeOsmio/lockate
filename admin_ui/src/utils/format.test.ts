import { describe, expect, it } from 'vitest'
import { formatDuration, formatRelative, parseServerDate, toServerDate } from './format'

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

describe('toServerDate', () => {
  it('writes UTC without an offset, as parseServerDate reads it', () => {
    const date = new Date('2026-11-20T22:12:52.000Z')
    expect(toServerDate(date)).toBe('2026-11-20T22:12:52.000')
    expect(parseServerDate(toServerDate(date))).toEqual(date)
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

describe('formatDuration', () => {
  it('uses the largest unit that fits exactly', () => {
    expect(formatDuration(7 * 24 * 3600)).toBe('7 days')
    expect(formatDuration(120)).toBe('2 minutes')
    expect(formatDuration(5400)).toBe('90 minutes')
    expect(formatDuration(3600)).toBe('1 hour')
    expect(formatDuration(45)).toBe('45 seconds')
  })
})
