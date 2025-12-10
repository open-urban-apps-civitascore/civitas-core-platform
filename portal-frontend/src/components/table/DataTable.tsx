import { ScrollArea } from '@radix-ui/react-scroll-area'
import { flexRender, Row, SortDirection, Table } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { ComponentProps } from 'react'

import { cn } from '@/lib/utils'

import { ScrollBar } from '../ui/scroll-area'
import { Skeleton } from '../ui/skeleton'
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
  isLoading?: boolean
  hasCard?: boolean
  testId?: string
  isRowClickable?: (row: Row<T>) => boolean
  onRowClick?: (row: Row<T>) => void
}

const LoadingSkeleton = () => (
  <div data-testid="loadingSkeleton">
    <Skeleton className="h-10 w-full mb-2.5 mt-2" />
    <Skeleton className="h-10 w-full mb-2.5" />
    <Skeleton className="h-10 w-full mb-2.5" />
    <Skeleton className="h-10 w-full" />
  </div>
)

export const DataTable = <T,>(props: DataTableProps<T>) => {
  const {
    table,
    pageSize,
    pageIndex,
    totalPages,
    isLoading,
    hasCard = true,
    onRowClick,
    isRowClickable = () => true,
    testId,
    ...tableProps
  } = props

  const t = useTranslations('common')

  return (
    <div className="@container h-full w-full" data-testid={testId}>
      <div className="h-full [--pagination-height:calc(--spacing(18))] @max-md:[--pagination-height:calc(--spacing(28))]  [--pagination-padding:calc(--spacing(4))]">
        <ScrollArea
          data-testid="dataTableScrollArea"
          className={cn('h-[calc(100%-var(--pagination-height))] w-full bg-white', hasCard && 'rounded-md border-1')}
        >
          <ShadCnTable aria-labelledby="subheading" tableContainerProps={{ className: '' }} {...tableProps}>
            <TableHeader>
              {table.getHeaderGroups().map(group => (
                <TableRow key={group.id}>
                  {group.headers.map(header => (
                    <TableHead
                      key={header.id}
                      scope="col"
                      className="text-primary-light px-3"
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
              {table.getRowModel().rows?.length ? (
                table.getRowModel().rows.map(row => (
                  <TableRow
                    className={cn(
                      `group h-16 ${onRowClick && isRowClickable(row) ? 'cursor-pointer' : ''} ${row.depth > 0 ? 'border-0' : 'border-0 border-t-1'}`,
                    )}
                    key={row.id}
                    onClick={onRowClick && isRowClickable(row) ? () => onRowClick(row) : () => null}
                    style={{ borderWidth: 0, borderTopWidth: row.depth === 0 ? 1 : 0 }}
                  >
                    {row.getVisibleCells().map(cell => (
                      <TableCell
                        className="whitespace-normal px-3"
                        key={cell.id}
                        style={cell.column.columnDef.meta?.style}
                      >
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </TableCell>
                    ))}
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={table.getAllColumns().length} className="h-24 text-center">
                    {isLoading ? <LoadingSkeleton /> : t('noResults')}
                  </TableCell>
                </TableRow>
              )}
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
