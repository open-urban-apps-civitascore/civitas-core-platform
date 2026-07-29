import { HTMLAttributes } from 'react'

import { cn } from '@/lib/utils'

export interface DetailsFieldContainerProps extends HTMLAttributes<HTMLDivElement> {
  isTitleField?: boolean
  hasBorder?: boolean
}
export const DetailsFieldContainer = (props: DetailsFieldContainerProps) => {
  const { className, children, isTitleField = false, hasBorder = true, ...restProps } = props
  return (
    <div
      className={cn('py-6', isTitleField && 'pt-0 pb-3 text-xl', hasBorder && 'border-b-1', className)}
      {...restProps}
    >
      {children}
    </div>
  )
}
