import type { ReactNode } from 'react'
import Box from '@mui/material/Box'
import Checkbox from '@mui/material/Checkbox'
import LinearProgress from '@mui/material/LinearProgress'
import MuiTable from '@mui/material/Table'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TablePagination from '@mui/material/TablePagination'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import { useMediaQuery, useTheme } from '@mui/material'
import { PAGE_SIZES } from '../hooks/usePaging'

export interface Column<T> {
  // Columns without a header hold the row's actions, at the end of the row or the card
  header?: string
  render: (row: T) => ReactNode
}

export interface Pagination {
  page: number
  size: number
  total: number
  onPageChange: (page: number) => void
  onSizeChange: (size: number) => void
}

export interface Selection {
  selected: ReadonlySet<string>
  onChange: (selected: Set<string>) => void
}

interface TableProps<T> {
  columns: Column<T>[]
  rows: T[]
  keyFor: (row: T) => string
  onRowClick?: (row: T) => void
  emptyMessage: string
  loading?: boolean
  pagination?: Pagination
  selection?: Selection
}

export function Table<T>({
  columns,
  rows,
  keyFor,
  onRowClick,
  emptyMessage,
  loading,
  pagination,
  selection,
}: TableProps<T>) {
  const theme = useTheme()
  const narrow = useMediaQuery(theme.breakpoints.down('sm'))
  const keys = rows.map(keyFor)
  const selectedHere = selection ? keys.filter((k) => selection.selected.has(k)).length : 0

  function toggleAll() {
    if (!selection) return
    const next = new Set(selection.selected)
    if (selectedHere === keys.length) keys.forEach((k) => next.delete(k))
    else keys.forEach((k) => next.add(k))
    selection.onChange(next)
  }

  return (
    <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
      <Box sx={{ height: 4 }}>{loading && <LinearProgress />}</Box>
      {rows.length === 0 ? (
        <Typography color="text.secondary" sx={{ p: 3, textAlign: 'center' }}>
          {emptyMessage}
        </Typography>
      ) : narrow ? (
        <CardList
          columns={columns}
          rows={rows}
          keyFor={keyFor}
          onRowClick={onRowClick}
          selection={selection}
        />
      ) : (
        <TableContainer>
          <MuiTable size="small">
            <TableHead>
              <TableRow>
                {selection && (
                  <TableCell padding="checkbox">
                    <Checkbox
                      size="small"
                      checked={selectedHere > 0 && selectedHere === keys.length}
                      indeterminate={selectedHere > 0 && selectedHere < keys.length}
                      onChange={toggleAll}
                      slotProps={{ input: { 'aria-label': 'Select all on this page' } }}
                    />
                  </TableCell>
                )}
                {columns.map((column, i) => (
                  <TableCell key={i}>{column.header}</TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => (
                <TableRow
                  key={keyFor(row)}
                  hover={!!onRowClick}
                  onClick={onRowClick ? () => onRowClick(row) : undefined}
                  selected={selection?.selected.has(keyFor(row))}
                  sx={onRowClick ? { cursor: 'pointer' } : undefined}
                >
                  {selection && (
                    <TableCell padding="checkbox">
                      <RowCheckbox id={keyFor(row)} selection={selection} />
                    </TableCell>
                  )}
                  {columns.map((column, i) => (
                    <TableCell
                      key={i}
                      align={column.header ? 'left' : 'right'}
                      sx={{ whiteSpace: 'nowrap' }}
                    >
                      {column.render(row)}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </MuiTable>
        </TableContainer>
      )}
      {pagination && pagination.total > 0 && (
        <TablePagination
          component="div"
          count={pagination.total}
          page={pagination.page}
          rowsPerPage={pagination.size}
          rowsPerPageOptions={PAGE_SIZES}
          onPageChange={(_e, page) => pagination.onPageChange(page)}
          onRowsPerPageChange={(e) => pagination.onSizeChange(Number(e.target.value))}
          sx={{ borderTop: 1, borderColor: 'divider' }}
        />
      )}
    </Paper>
  )
}

function RowCheckbox({ id, selection }: { id: string; selection: Selection }) {
  return (
    <Checkbox
      size="small"
      checked={selection.selected.has(id)}
      onClick={(e) => e.stopPropagation()}
      onChange={(e) => {
        const next = new Set(selection.selected)
        if (e.target.checked) next.add(id)
        else next.delete(id)
        selection.onChange(next)
      }}
      slotProps={{ input: { 'aria-label': `Select ${id}` } }}
    />
  )
}

function CardList<T>({
  columns,
  rows,
  keyFor,
  onRowClick,
  selection,
}: Pick<TableProps<T>, 'columns' | 'rows' | 'keyFor' | 'onRowClick' | 'selection'>) {
  const fields = columns.filter((column) => column.header)
  const actions = columns.filter((column) => !column.header)
  return (
    <Stack divider={<Box sx={{ borderTop: 1, borderColor: 'divider' }} />}>
      {rows.map((row) => (
        <Box
          key={keyFor(row)}
          onClick={onRowClick ? () => onRowClick(row) : undefined}
          sx={{
            p: 2,
            cursor: onRowClick ? 'pointer' : undefined,
            '&:hover': onRowClick ? { bgcolor: 'action.hover' } : undefined,
          }}
        >
          {fields.map((column, i) => (
            <Box
              key={i}
              sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, py: 0.25 }}
            >
              <Typography variant="body2" color="text.secondary">
                {column.header}
              </Typography>
              <Typography variant="body2" component="div" sx={{ textAlign: 'right' }}>
                {column.render(row)}
              </Typography>
            </Box>
          ))}
          {(actions.length > 0 || selection) && (
            <Box sx={{ display: 'flex', alignItems: 'center', mt: 1 }}>
              {selection && <RowCheckbox id={keyFor(row)} selection={selection} />}
              <Box sx={{ flex: 1 }} />
              {actions.map((column, i) => (
                <Box key={i}>{column.render(row)}</Box>
              ))}
            </Box>
          )}
        </Box>
      ))}
    </Stack>
  )
}
