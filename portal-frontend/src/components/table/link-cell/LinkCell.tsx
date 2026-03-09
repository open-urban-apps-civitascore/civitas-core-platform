import { ChevronRight } from 'lucide-react'
import Link, { LinkProps } from 'next/link'
import React, { JSX } from 'react'

import { cn } from '@/lib/utils'

interface LinkCellProps extends Omit<LinkProps, 'href'> {
  children: JSX.Element | string
  className?: string
  href?: string
  onClick?: () => void
}
export const LinkCell = (props: LinkCellProps) => {
  const { children, href, className, onClick, ...linkProps } = props

  const content = (
    <>
      <div className="flex-1">{children}</div>
      <ChevronRight className="w-5 h-5 text-muted-foreground opacity-0 group-hover/link:opacity-100 transition-opacity" />
    </>
  )

  const baseClassName = cn(
    'w-full h-full flex justify-between items-center gap-1.5 group/link hover:underline decoration-1.5 decoration-outline',
    className,
  )

  if (onClick) {
    return (
      <button onClick={onClick} className={cn(baseClassName, 'text-left cursor-pointer')} type="button">
        {content}
      </button>
    )
  }

  return (
    <Link className={baseClassName} href={href || '/'} {...linkProps}>
      {content}
    </Link>
  )
}
