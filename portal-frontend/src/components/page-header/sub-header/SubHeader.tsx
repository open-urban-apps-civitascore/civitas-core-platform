import { HTMLAttributes, ReactNode } from 'react'

import { cn } from '@/lib/utils'

interface SubHeaderProps extends HTMLAttributes<HTMLDivElement> {
  title: string
  subtitle?: string
  customElement?: ReactNode
}

export const SubHeader = (props: SubHeaderProps) => {
  const { title, subtitle, customElement, className } = props
  return (
    <div className={cn('flex justify-between', className)}>
      <div>
        <h2>{title}</h2>
        <p className="text-sm text-muted-foreground pt-1">{subtitle}</p>
      </div>
      {customElement}
    </div>
  )
}
