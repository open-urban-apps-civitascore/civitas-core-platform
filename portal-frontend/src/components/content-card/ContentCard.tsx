import React, { HTMLAttributes, JSX } from 'react'

import { cn } from '@/lib/utils'

export interface ContentCardProps extends HTMLAttributes<HTMLDivElement> {
  footerElement?: JSX.Element
}

export const ContentCard = (props: ContentCardProps) => {
  const { children, className, footerElement } = props
  return (
    <div
      className={cn(
        'flex flex-col justify-between bg-white p-[calc(var(--layout-padding))] border-1 rounded-sm',
        className,
      )}
    >
      {children}
      {footerElement && <div className="mt-6 text-xs">{footerElement}</div>}
    </div>
  )
}
