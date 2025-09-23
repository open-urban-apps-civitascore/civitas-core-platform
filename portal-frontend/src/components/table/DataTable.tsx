import { ScrollArea } from '@radix-ui/react-scroll-area'
import { flexRender, Row, SortDirection, Table } from '@tanstack/react-table'
import { ComponentProps } from 'react'

import { ScrollBar } from '../ui/scroll-area'
import { Table as ShadCnTable, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../ui/table'
import TablePagination from './table-pagination/TablePagination'

export const getAriaSort = (sorting: false | SortDirection) => {
  switch (sorting) {
    case 'asc':
      return 'ascending'
    case 'desc':
      return 'descending'
    default:
      return 'none'
  }
}

export interface DataTableProps<T> extends ComponentProps<'table'> {
  table: Table<T>
  pageSize: number
  pageIndex: number
  totalPages: number
  onRowClick?: (row: Row<T>) => void
}

export const DataTable = <T,>(props: DataTableProps<T>) => {
  const { table, pageSize, pageIndex, totalPages, onRowClick, ...tableProps } = props
  return (
    <div className="@container h-full w-full">
      <div className="h-full [--pagination-height:calc(--spacing(18))] @max-md:[--pagination-height:calc(--spacing(28))]  [--pagination-padding:calc(--spacing(4))]">
        <ScrollArea className="h-[calc(100%-var(--pagination-height))] w-full">
          <ShadCnTable aria-labelledby="subheading" {...tableProps}>
            <TableHeader>
              {table.getHeaderGroups().map(group => (
                <TableRow key={group.id}>
                  {group.headers.map(header => (
                    <TableHead
                      key={header.id}
                      scope="col"
                      className="text-primary-light"
                      aria-sort={getAriaSort(header.column.getIsSorted())}
                      style={header.column.columnDef.meta?.style}
                    >
                      {flexRender(header.column.columnDef.header, header.getContext())}
                    </TableHead>
                  ))}
                </TableRow>
              ))}
            </TableHeader>
            <TableBody>
              {table.getRowModel().rows.map(row => (
                <TableRow
                  className={`h-16 ${onRowClick ? 'cursor-pointer' : ''}`}
                  key={row.id}
                  style={{ background: row.getIsSelected() ? 'var(--accent)' : 'white' }}
                  onClick={onRowClick ? () => onRowClick(row) : () => null}
                >
                  {row.getVisibleCells().map(cell => (
                    <TableCell className="whitespace-normal" key={cell.id}>
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </ShadCnTable>
          <ScrollBar orientation="horizontal" />
        </ScrollArea>
        <TablePagination
          className="h-[calc(var(--pagination-height))]"
          pageIndex={pageIndex}
          pageSize={pageSize}
          totalPages={totalPages}
          table={table}
        />
      </div>
    </div>
  )
}
