import { ChevronRight } from 'lucide-react'
import { LinkProps } from 'next/link'
import React, { JSX, useRef } from 'react'

import { GuardedLink } from '@/components/guarded-link/GuardedLink'
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
          <GuardedLink
            className={cn(
              'w-full h-full flex justify-between items-center gap-1.5 group/link hover:underline decoration-1.5 decoration-outline',
              className,
            )}
            href={href as string}
            target={target}
            {...linkProps}
          >
            <div ref={ref} className="flex-1 min-w-0 truncate">
              {children}
            </div>
            <ChevronRight className="w-5 h-5 text-muted-foreground opacity-0 group-hover/link:opacity-100 transition-opacity" />
          </GuardedLink>
        )}
      </TooltipTrigger>
      <TooltipContent variant="secondary">{children}</TooltipContent>
    </Tooltip>
  )
}
