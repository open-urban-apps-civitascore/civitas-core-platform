import { HTMLAttributes } from 'react'

import { cn } from '@/lib/utils'

export const PageBackground = (props: HTMLAttributes<HTMLDivElement>) => {
  const { children, className } = props
  return (
    <div
      className={cn('min-h-0 bg-muted p-[calc(var(--layout-padding))] h-[calc(100%-var(--title-height))]', className)}
    >
      {children}
    </div>
  )
}
