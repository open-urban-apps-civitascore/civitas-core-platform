import * as TooltipPrimitive from '@radix-ui/react-tooltip'
import { JSX, ReactElement } from 'react'

import { cn } from '@/lib/utils'

import { Tooltip, TooltipTrigger } from '../ui/tooltip'

interface BasicTooltipProps {
  children: ReactElement
  tooltipContent: JSX.Element
  className?: string
}

export const BasicTooltip = (props: BasicTooltipProps) => {
  const { children, tooltipContent, className } = props
  return (
    <Tooltip>
      <TooltipTrigger asChild>{children}</TooltipTrigger>
      <TooltipPrimitive.Portal>
        <TooltipPrimitive.Content
          sideOffset={5}
          className={cn('max-w-[200px] p-3 bg-white border-1 border-solid border-accent rounded-sm', className)}
        >
          {tooltipContent}
        </TooltipPrimitive.Content>
      </TooltipPrimitive.Portal>
    </Tooltip>
  )
}
