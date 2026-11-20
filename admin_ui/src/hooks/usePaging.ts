import { useSearchParams } from 'react-router-dom'

export const PAGE_SIZES = [10, 20, 50, 100]
const DEFAULT_SIZE = 20

function positiveInt(value: string | null): number | null {
  const n = Number(value)
  return Number.isInteger(n) && n > 0 ? n : null
}

// The page lives in the URL (1-based, as people read it) so links and the back button keep it
export function usePaging() {
  const [params, setParams] = useSearchParams()
  const page = (positiveInt(params.get('page')) ?? 1) - 1
  const requestedSize = positiveInt(params.get('size'))
  const size = requestedSize && PAGE_SIZES.includes(requestedSize) ? requestedSize : DEFAULT_SIZE

  function search(nextPage: number, nextSize: number): URLSearchParams {
    const next = new URLSearchParams(params)
    if (nextPage > 0) next.set('page', String(nextPage + 1))
    else next.delete('page')
    if (nextSize !== DEFAULT_SIZE) next.set('size', String(nextSize))
    else next.delete('size')
    return next
  }

  return {
    page,
    size,
    setPage: (nextPage: number) => setParams(search(nextPage, size)),
    setSize: (nextSize: number) => setParams(search(0, nextSize)),
    // A page emptied by deletions, or one typed in the URL, falls back to the last one
    lastPageSearch: (totalPages: number): string | null =>
      page > 0 && page >= totalPages ? search(Math.max(0, totalPages - 1), size).toString() : null,
  }
}
