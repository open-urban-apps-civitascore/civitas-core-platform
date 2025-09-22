import { Table } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { HTMLAttributes } from 'react'

import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { cn } from '@/lib/utils'

interface TablePaginationProps<T> {
  table: Table<T>
  pageSize: number
  pageIndex: number
  totalPages: number
  className?: HTMLAttributes<HTMLDivElement>['className']
}

const TablePagination = <T,>(props: TablePaginationProps<T>) => {
  const t = useTranslations('pagination')
  const { table, pageSize, pageIndex, className, totalPages } = props

  return (
    <div
      className={cn('@container flex items-end justify-end text-sm h-[calc(var(--pagination-height))]', className)}
      role="navigation"
      aria-label={t('aria.pagination')}
    >
      <div className="flex items-center gap-x-8 gap-y-2 justify-center @max-md:flex-wrap">
        <div className="flex items-center gap-8">
          <div className="flex items-center space-x-2">
            <span className="@max-md:hidden ">{t('resultsPerPage')}</span>
            <Select value={String(pageSize)} onValueChange={value => table.setPageSize(Number(value))}>
              <SelectTrigger className="w-[80px]" aria-label={t('aria.resultsPerPage')}>
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
          <p data-testid="currentPage" className="inline-block whitespace-nowrap text-center">
            {t('page')} {pageIndex + 1} <wbr />
            {t('of')} {totalPages || 1}
          </p>
        </div>
        <div className="flex space-x-2">
          <Button
            variant="outline"
            size="icon"
            aria-label={t('aria.firstPage')}
            onClick={() => table.setPageIndex(0)}
            disabled={!table.getCanPreviousPage()}
          >
            «
          </Button>
          <Button
            variant="outline"
            size="icon"
            aria-label={t('aria.previousPage')}
            onClick={() => table.previousPage()}
            disabled={!table.getCanPreviousPage()}
          >
            ‹
          </Button>
          <Button
            variant="outline"
            size="icon"
            aria-label={t('aria.nextPage')}
            onClick={() => table.nextPage()}
            disabled={!table.getCanNextPage()}
          >
            ›
          </Button>
          <Button
            variant="outline"
            size="icon"
            aria-label={t('aria.lastPage')}
            onClick={() => table.setPageIndex(totalPages - 1)}
            disabled={!table.getCanNextPage()}
          >
            »
          </Button>
        </div>
      </div>
    </div>
  )
}

export default TablePagination
