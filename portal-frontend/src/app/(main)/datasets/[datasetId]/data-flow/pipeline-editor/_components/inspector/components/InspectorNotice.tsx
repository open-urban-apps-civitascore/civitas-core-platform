'use client'

/** Highlighted note above an inspector panel, e.g. why a node cannot be edited. */

import type { LucideIcon } from 'lucide-react'

interface InspectorNoticeProps {
  icon: LucideIcon
  title: string
  description: string
}

export const InspectorNotice: React.FC<InspectorNoticeProps> = ({ icon, title, description }) => {
  const Icon = icon

  return (
    <div className="m-4 rounded-md border border-border bg-muted/30 p-3">
      <div className="flex items-center gap-2">
        <Icon className="size-4 shrink-0 text-foreground" aria-hidden />
        <h4 className="text-sm font-medium text-foreground">{title}</h4>
      </div>
      <p className="mt-1 text-sm text-muted-foreground">{description}</p>
    </div>
  )
}
