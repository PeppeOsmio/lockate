import Box from '@mui/material/Box'
import Pagination from '@mui/material/Pagination'

interface TablePagerProps {
  page: number
  totalPages: number
  onChange: (page: number) => void
}

export function TablePager({ page, totalPages, onChange }: TablePagerProps) {
  if (totalPages <= 1) return null

  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
      <Pagination
        count={totalPages}
        page={page + 1}
        onChange={(_e, value) => onChange(value - 1)}
        color="primary"
        shape="rounded"
      />
    </Box>
  )
}
