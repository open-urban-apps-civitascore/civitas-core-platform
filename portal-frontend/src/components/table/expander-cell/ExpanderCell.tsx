import { Row, RowData } from '@tanstack/react-table'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { HTMLAttributes, MouseEvent } from 'react'

import { Button, ButtonProps } from '@/components/ui/button'
import { cn } from '@/lib/utils'

interface ExpanderCellProps<T extends RowData> extends HTMLAttributes<HTMLDivElement> {
  row: Row<T>
  buttonProps?: ButtonProps
  value: string
}
export const ExpanderCell = <T,>(props: ExpanderCellProps<T>) => {
  const { row, value, buttonProps, className,  ...divProps } = props
  const expanderWidth = `${1.75 + row.depth}rem`

  const handleExpanderClick = (e: MouseEvent<HTMLButtonElement>, row: Row<T>) => {
    e.stopPropagation()
    row.toggleExpanded()
  }

  return (
    <div className={cn('flex items-center min-w-[200px] w-[35%]', className)} {...divProps}>
      {row.getCanExpand() ? (
        <>
          <div
            style={{
              width: expanderWidth,
              display: 'flex',
              justifyContent: 'flex-end',
              paddingRight: '0.25rem',
            }}
          >
            <Button
              variant="ghost"
              size="icon"
              onClick={e => handleExpanderClick(e, row)}
              className="h-6 w-6 p-0"
              {...buttonProps}
            >
              {row.getIsExpanded() ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
            </Button>
          </div>{' '}
          <span>{value}</span>
        </>
      ) : (
        <span style={{ paddingLeft: expanderWidth }}>{value}</span>
      )}
    </div>
  )
}
