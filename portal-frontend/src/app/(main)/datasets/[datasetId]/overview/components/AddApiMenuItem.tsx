'use client'

import { GuardedLink } from '@/components/guarded-link/GuardedLink'
import { DropdownMenuItem } from '@/components/ui/dropdown-menu'

interface AddApiMenuItemProps {
  href: string
  title: string
  subtitle: string
  canCreate: boolean
}

export const AddApiMenuItem = (props: AddApiMenuItemProps) => {
  const { href, title, subtitle, canCreate } = props

  return (
    <DropdownMenuItem disabled={!canCreate} asChild={canCreate}>
      {canCreate ? (
        <GuardedLink href={href} className="flex items-center gap-3">
          <span>{title}</span>
          <span className="text-xs text-muted-foreground">{subtitle}</span>
        </GuardedLink>
      ) : (
        <div className="flex items-center gap-3">
          <span className="text-muted-foreground">{title}</span>
          <span className="text-xs text-muted-foreground">{subtitle}</span>
        </div>
      )}
    </DropdownMenuItem>
  )
}
