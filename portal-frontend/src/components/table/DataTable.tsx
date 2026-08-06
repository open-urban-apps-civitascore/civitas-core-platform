import { flexRender, Row, SortDirection, Table } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { ComponentProps, ReactNode, useRef } from 'react'

import { ContentCard } from '@/components/content-card/ContentCard'
import { useIsTruncated } from '@/hooks/use-is-truncated'
import { cn } from '@/lib/utils'

import { ScrollArea, ScrollBar } from '../ui/scroll-area'
import { Skeleton } from '../ui/skeleton'
import { Table as ShadCnTable, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../ui/table'
import { Tooltip, TooltipContent, TooltipTrigger } from '../ui/tooltip'
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
  isPaginationHidden?: boolean
  testId?: string
  isRowClickable?: (row: Row<T>) => boolean
  onRowClick?: (row: Row<T>) => void
  tableTitle?: string
  tableSubtitle?: string
  tableAction?: ReactNode
}

const TruncatedCell = ({ children }: { children: ReactNode }) => {
  const ref = useRef<HTMLDivElement>(null)
  const isTruncated = useIsTruncated(ref)

  return (
    <Tooltip open={isTruncated ? undefined : false}>
      <TooltipTrigger asChild>
        <div ref={ref} tabIndex={0} className="truncate">
          {children}
        </div>
      </TooltipTrigger>
      <TooltipContent variant="secondary">{children}</TooltipContent>
    </Tooltip>
  )
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
    isPaginationHidden = false,
    onRowClick,
    isRowClickable = () => true,
    testId,
    tableTitle,
    tableSubtitle,
    tableAction,
    ...tableProps
  } = props

  const t = useTranslations('common')

  const tableContent = (
    <div className={cn('@container w-full', !isPaginationHidden && 'h-full')} data-testid={testId}>
      <div
        className={cn(
          !isPaginationHidden &&
            'flex h-full flex-col [--pagination-height:calc(--spacing(18))] @max-md:[--pagination-height:calc(--spacing(28))] [--pagination-padding:calc(--spacing(4))]',
        )}
      >
        <ScrollArea
          data-testid="dataTableScrollArea"
          className={cn(
            'w-full bg-white',
            hasCard && !tableTitle && 'rounded-md border-1',
            !isPaginationHidden && 'max-h-[calc(100%-var(--pagination-height))]',
          )}
        >
          <ShadCnTable
            aria-labelledby="subheading"
            tableContainerProps={{ className: 'overflow-x-visible overflow-y-visible' }}
            {...tableProps}
          >
            <TableHeader className="sticky top-0 z-10 bg-white [&_tr]:border-b-0">
              {table.getHeaderGroups().map(group => (
                <TableRow key={group.id}>
                  {group.headers.map(header => (
                    <TableHead
                      key={header.id}
                      scope="col"
                      className="text-primary-light bg-white px-3 shadow-[inset_0_-1px_0_0_var(--color-border)]"
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
                table.getRowModel().rows.map((row, index) => (
                  <TableRow
                    className={cn(
                      `group h-16 ${onRowClick && isRowClickable(row) ? 'cursor-pointer' : ''} ${row.depth > 0 ? 'border-0' : 'border-0 border-t-1'}`,
                    )}
                    key={row.id}
                    onClick={onRowClick && isRowClickable(row) ? () => onRowClick(row) : () => null}
                    style={{ borderWidth: 0, borderTopWidth: row.depth === 0 && index > 0 ? 1 : 0 }}
                  >
                    {row.getVisibleCells().map(cell => (
                      <TableCell
                        className={cn(
                          'px-3 group/cell relative h-16',
                          cell.column.columnDef.meta?.truncate ? 'max-w-0' : 'whitespace-normal',
                        )}
                        key={cell.id}
                        style={cell.column.columnDef.meta?.style}
                      >
                        {cell.column.columnDef.meta?.truncate ? (
                          <TruncatedCell>{flexRender(cell.column.columnDef.cell, cell.getContext())}</TruncatedCell>
                        ) : (
                          flexRender(cell.column.columnDef.cell, cell.getContext())
                        )}
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

        {!isPaginationHidden && (
          <TablePagination
            className="h-[calc(var(--pagination-height))]"
            pageIndex={pageIndex}
            pageSize={pageSize}
            totalPages={totalPages}
            table={table}
          />
        )}
      </div>
    </div>
  )

  if (tableTitle) {
    return (
      <ContentCard className="px-0 pb-0">
        <div className="flex items-start justify-between p-[calc(var(--layout-padding))] pt-0">
          <div className="max-w-2xl">
            <h2 className="text-2xl leading-none font-bold">{tableTitle}</h2>
            {tableSubtitle && <p className="text-sm text-muted-foreground mt-1">{tableSubtitle}</p>}
          </div>
          {tableAction}
        </div>
        <hr className="border-border" />
        {tableContent}
      </ContentCard>
    )
  }

  return (
    <>
      {tableAction && <div className="flex justify-end mb-4">{tableAction}</div>}
      {tableContent}
    </>
  )
}
