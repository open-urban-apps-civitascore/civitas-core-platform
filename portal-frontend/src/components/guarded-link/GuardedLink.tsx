'use client'

import Link from 'next/link'
import { ComponentPropsWithoutRef, forwardRef, MouseEvent } from 'react'

import { useUnsavedChanges } from '@/contexts/unsaved-changes/UnsavedChangesContext'

interface GuardedLinkProps extends Omit<ComponentPropsWithoutRef<typeof Link>, 'href'> {
  href: string
}

export const GuardedLink = forwardRef<HTMLAnchorElement, GuardedLinkProps>(
  ({ href, children, onClick, ...props }, ref) => {
    const { hasUnsavedChanges, requestNavigation } = useUnsavedChanges()

    const handleClick = (e: MouseEvent<HTMLAnchorElement>) => {
      if (hasUnsavedChanges) {
        e.preventDefault()
        requestNavigation(href)
        return
      }

      onClick?.(e)
    }

    return (
      <Link ref={ref} href={href} onClick={handleClick} {...props}>
        {children}
      </Link>
    )
  },
)

GuardedLink.displayName = 'GuardedLink'
