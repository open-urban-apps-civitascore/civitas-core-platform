import { HTMLAttributes, ReactNode } from 'react'

import { cn } from '@/lib/utils'

interface SubHeaderProps extends HTMLAttributes<HTMLDivElement> {
  title: string
  subtitle?: string
  customElement?: ReactNode
  titleClassName?: string
}

export const SubHeader = (props: SubHeaderProps) => {
  const { title, subtitle, customElement, className, titleClassName } = props
  return (
    <div className={cn('flex justify-between', className)}>
      <div>
        <h2 className={cn('text-xl', titleClassName)}>{title}</h2>
        {subtitle && <p className="text-sm text-muted-foreground pt-1">{subtitle}</p>}
      </div>
      {customElement}
    </div>
  )
}
