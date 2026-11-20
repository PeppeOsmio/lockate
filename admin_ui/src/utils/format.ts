// The backend sends UTC times without an offset (Java LocalDateTime), which Date would read as local
// time
export function formatDate(value: string | null): string {
  if (!value) return '—'
  const hasOffset = /(Z|[+-]\d\d:?\d\d)$/.test(value)
  return new Date(hasOffset ? value : `${value}Z`).toLocaleString()
}
