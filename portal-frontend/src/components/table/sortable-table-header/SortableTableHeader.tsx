import { Column } from '@tanstack/react-table'
import { ArrowUpDown } from 'lucide-react'

import { Button, ButtonProps } from '../../ui/button'

interface SortableTableHeaderProps<T, TValue> extends ButtonProps {
  column: Column<T, TValue>
  title?: string
}

export const SortableTableHeader = <T, TValue>(props: SortableTableHeaderProps<T, TValue>) => {
  const { column, title } = props
  return (
    <>
      {title}
      <Button
        className="hover:bg-transparent hover:cursor-pointer"
        variant="ghost"
        onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}
      >
        <ArrowUpDown />
      </Button>
    </>
  )
}
