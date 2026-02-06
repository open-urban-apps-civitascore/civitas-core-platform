import { HTMLAttributes } from 'react'

import { cn } from '@/lib/utils'

interface PageBackgroundProps extends HTMLAttributes<HTMLDivElement> {
  hasBackground?: boolean
}

export const PageBackground = (props: PageBackgroundProps) => {
  const { children, className, hasBackground = false } = props
  return (
    <div
      className={cn(
        'min-h-0 bg-muted p-[calc(var(--layout-padding))] h-[calc(100%-var(--title-height))] overflow-auto',
        hasBackground && 'bg-[radial-gradient(rgba(0,0,0,0.15)_0.8px,transparent_0.8px)] bg-[length:14px_14px]',
        className,
      )}
    >
      {children}
    </div>
  )
}
