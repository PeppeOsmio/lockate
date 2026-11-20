import { useSearchParams } from 'react-router-dom'
import type { GroupSort } from '../api/types'

export const SORTS: { value: GroupSort; label: string }[] = [
  { value: 'created', label: 'Newest first' },
  { value: 'members', label: 'Most members' },
  { value: 'activity', label: 'Latest position' },
]

export const INACTIVE_DAYS = [7, 30, 90]

// What a group or member id can start with; anything else can't match and the server refuses it
export const ID_PREFIX = /^[0-9a-f-]{1,36}$/

// Search, sort and the inactivity filter live in the URL, like the page, which they reset
export function useGroupFilters() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const sortParam = params.get('sort')
  const sort = SORTS.find((s) => s.value === sortParam)?.value ?? 'created'
  const inactiveParam = Number(params.get('inactive'))
  const inactiveDays = INACTIVE_DAYS.includes(inactiveParam) ? inactiveParam : undefined

  function update(key: string, value: string | null) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    next.delete('page')
    setParams(next, { replace: key === 'q' })
  }

  return {
    q,
    sort,
    inactiveDays,
    setQ: (value: string) => update('q', value),
    setSort: (value: GroupSort) => update('sort', value === 'created' ? null : value),
    setInactiveDays: (value: number | undefined) =>
      update('inactive', value ? String(value) : null),
  }
}
