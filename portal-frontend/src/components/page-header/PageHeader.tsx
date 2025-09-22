import React, { HTMLAttributes, JSX } from 'react'

import { cn } from '@/lib/utils'

interface PageHeaderProps extends Pick<HTMLAttributes<HTMLDivElement>, 'className' | 'style'> {
  title: string
  subtitle?: string
  customElement?: JSX.Element
}

export const PageHeader = (props: PageHeaderProps) => {
  const { title, subtitle, customElement, className, style } = props
  return (
    <div id="heading" className={cn('flex justify-between h-[var(--title-height)]', className)} style={style}>
      <div>
        <h1 id="page-heading" className="my-1">
          {title}
        </h1>
        {subtitle && (
          <p id="page-subheading" className="text-primary-light">
            {subtitle}
          </p>
        )}
      </div>
      {customElement}
    </div>
  )
}
