import { Table } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { HTMLAttributes } from 'react'

import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { cn } from '@/lib/utils'

import { Dataset } from '../page'

interface TablePaginationProps {
  table: Table<Dataset>
  rowCount: number
  pageSize: number
  pageIndex: number
  className?: HTMLAttributes<HTMLDivElement>['className']
}

const TablePagination = (props: TablePaginationProps) => {
  const t = useTranslations('datasets')
  const { table, pageSize, pageIndex, rowCount, className } = props
  const totalPages = Math.ceil(rowCount / pageSize)

  return (
    <div
      className={cn('flex items-end justify-end gap-8 text-sm h-[calc(var(--pagination-height))]', className)}
    >
      <div className="flex items-center space-x-2">
        <span>{t('pagination.resultsPerPage')}</span>
        <Select value={String(pageSize)} onValueChange={value => table.setPageSize(Number(value))}>
          <SelectTrigger className="w-[80px]">
            <SelectValue placeholder={pageSize} />
          </SelectTrigger>
          <SelectContent>
            {[5, 10, 20, 25].map(size => (
              <SelectItem key={size} value={String(size)}>
                {size}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div data-testid="currentPage">
        {t('pagination.page')} {pageIndex + 1} {t('pagination.of')} {totalPages || 1}
      </div>
      <div className="flex space-x-2">
        <Button
          variant="outline"
          size="icon"
          onClick={() => table.setPageIndex(0)}
          disabled={!table.getCanPreviousPage()}
        >
          «
        </Button>
        <Button
          variant="outline"
          size="icon"
          onClick={() => table.previousPage()}
          disabled={!table.getCanPreviousPage()}
        >
          ‹
        </Button>
        <Button variant="outline" size="icon" onClick={() => table.nextPage()} disabled={!table.getCanNextPage()}>
          ›
        </Button>
        <Button
          variant="outline"
          size="icon"
          onClick={() => table.setPageIndex(totalPages - 1)}
          disabled={!table.getCanNextPage()}
        >
          »
        </Button>
      </div>
    </div>
  )
}

export default TablePagination
