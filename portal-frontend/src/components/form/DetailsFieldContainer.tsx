import { HTMLAttributes } from 'react'

import { cn } from '@/lib/utils'

interface DetailsFieldContainer extends HTMLAttributes<HTMLDivElement> {
  isTitleField?: boolean
}
export const DetailsFieldContainer = (props: DetailsFieldContainer) => {
  const { className, children, isTitleField = false, ...restProps } = props
  return (
    <div className={cn(`py-6 border-b-1 ${isTitleField && 'pt-0 pb-3 text-xl'}`, className)} {...restProps}>
      {children}
    </div>
  )
}
