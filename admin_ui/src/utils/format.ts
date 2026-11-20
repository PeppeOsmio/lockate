// The backend sends UTC times without an offset (Java LocalDateTime), which Date would read as local
// time
export function parseServerDate(value: string): Date {
  const hasOffset = /(Z|[+-]\d\d:?\d\d)$/.test(value)
  return new Date(hasOffset ? value : `${value}Z`)
}

const dateTimeFormat = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'medium',
  hourCycle: 'h23',
})

export function formatDateTime(date: Date): string {
  return dateTimeFormat.format(date)
}

const relativeFormat = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 365 * 24 * 3600],
  ['month', 30 * 24 * 3600],
  ['week', 7 * 24 * 3600],
  ['day', 24 * 3600],
  ['hour', 3600],
  ['minute', 60],
]

export function formatRelative(date: Date, now: Date = new Date()): string {
  const seconds = Math.round((date.getTime() - now.getTime()) / 1000)
  for (const [unit, unitSeconds] of UNITS) {
    if (Math.abs(seconds) >= unitSeconds) {
      return relativeFormat.format(Math.trunc(seconds / unitSeconds), unit)
    }
  }
  return 'just now'
}

const DURATION_UNITS: [string, number][] = [
  ['day', 24 * 3600],
  ['hour', 3600],
  ['minute', 60],
  ['second', 1],
]

// A setting's length in its largest whole unit: "7 days", "90 minutes"
export function formatDuration(seconds: number): string {
  const [unit, unitSeconds] = DURATION_UNITS.find(([, s]) => seconds % s === 0) ?? ['second', 1]
  const count = seconds / unitSeconds
  return `${count} ${unit}${count === 1 ? '' : 's'}`
}
