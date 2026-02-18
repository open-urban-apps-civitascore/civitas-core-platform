import { Row, RowData } from '@tanstack/react-table'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { MouseEvent, ReactNode, useCallback } from 'react'

import { Button, ButtonProps } from '@/components/ui/button'
import { cn } from '@/lib/utils'

interface ExpanderCellProps<T extends RowData> {
  row: Row<T>
  buttonProps?: ButtonProps
  children: ReactNode
  className?: string
}
export const ExpanderCell = <T,>(props: ExpanderCellProps<T>) => {
  const { row, children, buttonProps, className, ...divProps } = props
  const expanderWidth = `${1.75 + row.depth}rem`

  const handleExpanderClick = useCallback(
    (e: MouseEvent<HTMLButtonElement>) => {
      e.stopPropagation()
      row.toggleExpanded()
    },
    [row],
  )

  return (
    <div data-testid="expanderCell" className={cn('flex items-center min-w-[200px] w-[35%]', className)} {...divProps}>
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
              onClick={e => handleExpanderClick(e)}
              className="h-6 w-6 p-0"
              {...buttonProps}
            >
              {row.getIsExpanded() ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
            </Button>
          </div>{' '}
          <div>{children}</div>
        </>
      ) : (
        <span style={{ paddingLeft: expanderWidth }}>{children}</span>
      )}
    </div>
  )
}
