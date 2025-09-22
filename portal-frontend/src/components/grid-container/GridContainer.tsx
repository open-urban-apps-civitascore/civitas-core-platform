import { HTMLAttributes } from 'react'

import { cn } from '@/lib/utils'

interface GrindContainerProps extends Pick<HTMLAttributes<HTMLDivElement>, 'className' | 'style' | 'children'> {
  columns: number
  shouldRespectSearchHeight?: boolean
  shouldRespectTitleHeight?: boolean
}

export const GridContainer = (props: GrindContainerProps) => {
  const { children, columns, shouldRespectSearchHeight = true, shouldRespectTitleHeight = true, className } = props
  const height = `calc(100%${shouldRespectTitleHeight ? ' - var(--title-height)' : ''}${shouldRespectSearchHeight ? ' - var(--search-height)' : ''})`
  const gridCols = `repeat(${columns}, minmax(0, 1fr))`
  return (
    <div
      className={cn(`grid ${gridCols} grid-rows-[minmax(0,1fr)] gap-4 `, className)}
      style={{ height: height, gridTemplateColumns: gridCols }}
    >
      {children}
    </div>
  )
}
