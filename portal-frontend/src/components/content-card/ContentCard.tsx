import React, { HTMLAttributes } from 'react'

import { cn } from '@/lib/utils'

export const ContentCard = (props: HTMLAttributes<HTMLDivElement>) => {
  const { children, className } = props
  return <div className={cn('bg-white p-[calc(var(--layout-padding))] border-1 rounded-sm', className)}>{children}</div>
}
