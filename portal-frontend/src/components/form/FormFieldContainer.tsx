import { HTMLAttributes } from 'react'

import { cn } from '@/lib/utils'

export const FormFieldContainer = (props: HTMLAttributes<HTMLDivElement>) => {
  const { className, children, ...restProps } = props
  return (
    <div className={cn('py-6 border-b-1', className)} {...restProps}>
      {children}
    </div>
  )
}
