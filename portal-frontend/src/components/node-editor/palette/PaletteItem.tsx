'use client'

import type { LucideIcon } from 'lucide-react'
import type { DragEvent } from 'react'

export const PALETTE_DND_TYPE = 'application/node-editor-type'

interface PaletteItemProps {
  type: string
  label: string
  description?: string
  icon?: LucideIcon
}

export const PaletteItem = ({ type, label, description, icon }: PaletteItemProps) => {
  const Icon = icon
  const onDragStart = (e: DragEvent) => {
    e.dataTransfer.setData(PALETTE_DND_TYPE, type)
    e.dataTransfer.effectAllowed = 'move'
  }

  return (
    <div
      draggable
      onDragStart={onDragStart}
      className="flex cursor-grab items-center gap-2 rounded-md border border-border bg-background px-2 py-1.5 text-sm hover:border-primary/50 hover:bg-muted/50"
    >
      {Icon && <Icon className="h-4 w-4 shrink-0 text-muted-foreground" />}
      <div className="min-w-0">
        <div className="truncate font-medium">{label}</div>
        {description && <div className="truncate text-xs text-muted-foreground">{description}</div>}
      </div>
    </div>
  )
}
