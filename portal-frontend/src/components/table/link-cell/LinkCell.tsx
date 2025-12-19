import { ChevronRight } from 'lucide-react'
import Link, { LinkProps } from 'next/link'
import React, { JSX } from 'react'

import { cn } from '@/lib/utils'

interface LinkCellProps extends LinkProps {
  children: JSX.Element | string
  className?: string
}
export const LinkCell = (props: LinkCellProps) => {
  const { children, href, className, ...linkProps } = props
  return (
    <Link
      className={cn(
        'w-full h-full flex justify-between items-center gap-1.5 group/link hover:underline decoration-1.5 decoration-outline',
        className,
      )}
      href={href}
      {...linkProps}
    >
      <div className="flex-1">{children}</div>
      <ChevronRight className="w-5 h-5 text-muted-foreground opacity-0 group-hover/link:opacity-100 transition-opacity" />
    </Link>
  )
}
