import { Column } from '@tanstack/react-table'
import { ArrowUpDown } from 'lucide-react'

import { cn } from '@/lib/utils'

import { Button, ButtonProps } from '../../ui/button'

interface SortableTableHeaderProps<T, TValue> extends ButtonProps {
  column: Column<T, TValue>
  title?: string
}

export const SortableTableHeader = <T, TValue>(props: SortableTableHeaderProps<T, TValue>) => {
  const { column, title, className } = props
  return (
    <div data-testid="sortableTableHeader">
      {title}
      <Button
        className={cn('hover:bg-transparent hover:cursor-pointer', className)}
        variant="ghost"
        onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}
      >
        <ArrowUpDown />
      </Button>
    </div>
  )
}
