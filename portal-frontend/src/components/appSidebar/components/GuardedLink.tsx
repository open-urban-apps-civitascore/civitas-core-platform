'use client'

import Link from 'next/link'
import { MouseEvent } from 'react'

import { useUnsavedChanges } from '@/contexts/unsaved-changes/UnsavedChangesContext'

interface GuardedLinkProps {
  href: string
  children: React.ReactNode
  className?: string
  target?: React.HTMLAttributeAnchorTarget
  // eslint-disable-next-line @typescript-eslint/naming-convention
  'aria-current'?: React.AriaAttributes['aria-current']
}

export const GuardedLink = ({ href, children, className, target, 'aria-current': ariaCurrent }: GuardedLinkProps) => {
  const { hasUnsavedChanges, requestNavigation } = useUnsavedChanges()

  const handleClick = (e: MouseEvent<HTMLAnchorElement>) => {
    if (hasUnsavedChanges) {
      e.preventDefault()
      requestNavigation(href)
    }
  }

  return (
    <Link href={href} className={className} target={target} aria-current={ariaCurrent} onClick={handleClick}>
      {children}
    </Link>
  )
}
