import type { ReactNode } from 'react'
import MuiTable from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'

export interface Column<T> {
  header: string
  render: (row: T) => ReactNode
}

interface TableProps<T> {
  columns: Column<T>[]
  rows: T[]
  keyFor: (row: T) => string
  onRowClick?: (row: T) => void
  emptyMessage?: string
}

export function Table<T>({ columns, rows, keyFor, onRowClick, emptyMessage }: TableProps<T>) {
  if (rows.length === 0) {
    return <Typography color="text.secondary">{emptyMessage ?? 'Nothing here yet.'}</Typography>
  }

  return (
    <MuiTable>
      <TableHead>
        <TableRow>
          {columns.map((column) => (
            <TableCell key={column.header}>
              <strong>{column.header}</strong>
            </TableCell>
          ))}
        </TableRow>
      </TableHead>
      <TableBody>
        {rows.map((row) => (
          <TableRow
            key={keyFor(row)}
            hover={!!onRowClick}
            onClick={onRowClick ? () => onRowClick(row) : undefined}
            sx={onRowClick ? { cursor: 'pointer' } : undefined}
          >
            {columns.map((column) => (
              <TableCell key={column.header}>{column.render(row)}</TableCell>
            ))}
          </TableRow>
        ))}
      </TableBody>
    </MuiTable>
  )
}
