import { ChevronRight } from 'lucide-react'
import Link, { LinkProps } from 'next/link'
import React, { JSX, useRef } from 'react'

import { useIsTruncated } from '@/hooks/use-is-truncated'
import { cn } from '@/lib/utils'

import { Tooltip, TooltipContent, TooltipTrigger } from '../../ui/tooltip'
interface LinkCellProps extends LinkProps {
  children: JSX.Element | string
  className?: string
  target?: React.HTMLAttributeAnchorTarget
  isDisabled?: boolean
}
export const LinkCell = (props: LinkCellProps) => {
  const { children, href, className, target, isDisabled, ...linkProps } = props
  const ref = useRef<HTMLDivElement>(null)
  const isTruncated = useIsTruncated(ref)

  return (
    <Tooltip open={isTruncated ? undefined : false}>
      <TooltipTrigger asChild>
        {isDisabled ? (
          <div className={cn('w-full h-full flex items-center', className)}>{children}</div>
        ) : (
          <Link
            className={cn(
              'w-full h-full flex justify-between items-center gap-1.5 group/link hover:underline decoration-1.5 decoration-outline',
              className,
            )}
            href={href}
            target={target}
            {...linkProps}
          >
            <div ref={ref} className="flex-1 min-w-0 truncate">
              {children}
            </div>
            <ChevronRight className="w-5 h-5 text-muted-foreground opacity-0 group-hover/link:opacity-100 transition-opacity" />
          </Link>
        )}
      </TooltipTrigger>
      <TooltipContent variant="secondary">{children}</TooltipContent>
    </Tooltip>
  )
}
