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
  suffix?: JSX.Element
}
export const LinkCell = (props: LinkCellProps) => {
  const { children, href, className, target, isDisabled, suffix, ...linkProps } = props
  const ref = useRef<HTMLDivElement>(null)
  const isTruncated = useIsTruncated(ref)
  const hasSuffix = suffix !== undefined
  const suffixElement = hasSuffix ? <span className="shrink-0">{suffix}</span> : null

  return (
    <Tooltip open={isTruncated ? undefined : false}>
      <TooltipTrigger asChild>
        {isDisabled ? (
          <div className={cn('w-full h-full flex items-center', hasSuffix && 'gap-2', className)}>
            {children}
            {suffixElement}
          </div>
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
            {hasSuffix ? (
              <div className="flex min-w-0 flex-1 items-center gap-2">
                <div ref={ref} className="min-w-0 truncate">
                  {children}
                </div>
                {suffixElement}
              </div>
            ) : (
              <div ref={ref} className="flex-1 min-w-0 truncate">
                {children}
              </div>
            )}
            <ChevronRight className="w-5 h-5 text-muted-foreground opacity-0 group-hover/link:opacity-100 transition-opacity" />
          </GuardedLink>
        )}
      </TooltipTrigger>
      <TooltipContent variant="secondary">{children}</TooltipContent>
    </Tooltip>
  )
}
