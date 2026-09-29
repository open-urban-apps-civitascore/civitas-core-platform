'use client'

/** Highlighted note above an inspector panel, e.g. why a node cannot be edited. */

import type { LucideIcon } from 'lucide-react'

import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'

interface InspectorNoticeProps {
  icon: LucideIcon
  title: string
  variant?: 'row' | 'card'
  /** Only shown in the card variant. */
  description?: string
  tooltip?: string
}

export const InspectorNotice: React.FC<InspectorNoticeProps> = ({
  icon,
  title,
  variant = 'row',
  description,
  tooltip,
}) => {
  const Icon = icon

  const notice =
    variant === 'card' ? (
      <div className="m-4 rounded-md border border-border bg-muted p-3">
        <div className="flex items-center gap-2">
          <Icon className="size-4 shrink-0 text-foreground" aria-hidden />
          <h4 className="text-sm font-medium text-foreground">{title}</h4>
        </div>
        {description && <p className="mt-2 text-sm text-muted-foreground">{description}</p>}
      </div>
    ) : (
      <div className="flex w-fit items-center gap-3 p-4">
        <div className="flex h-8 w-8 items-center justify-center rounded-md border border-border bg-muted text-foreground">
          <Icon className="size-4" aria-hidden />
        </div>
        <span className="text-sm text-foreground">{title}</span>
      </div>
    )

  if (!tooltip) return notice

  return (
    <Tooltip>
      <TooltipTrigger asChild>{notice}</TooltipTrigger>
      <TooltipContent className="max-w-xs">{tooltip}</TooltipContent>
    </Tooltip>
  )
}
