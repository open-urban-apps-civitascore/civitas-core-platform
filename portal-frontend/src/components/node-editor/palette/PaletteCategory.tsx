'use client'

import { ChevronDown, ChevronRight } from 'lucide-react'
import type { ReactNode } from 'react'

interface PaletteCategoryProps {
  /** Category heading text. */
  label: string
  children: ReactNode
  /** When true, the category renders as a collapsible header with a chevron. */
  isCollapsible?: boolean
  /** Expanded state (only used when `isCollapsible`). */
  isExpanded?: boolean
  /** Toggle handler (only used when `isCollapsible`). */
  onToggle?: () => void
}

/**
 * Lists palette items under a category heading. Domain-agnostic.
 * Renders a simple label by default, or a collapsible header when `isCollapsible` is set.
 */
export const PaletteCategory = ({
  label,
  children,
  isCollapsible,
  isExpanded = true,
  onToggle,
}: PaletteCategoryProps) => {
  if (isCollapsible) {
    return (
      <div className="border-b border-border">
        <button
          type="button"
          onClick={onToggle}
          className="flex w-full items-center justify-between bg-muted/30 px-3 py-2 text-left transition-colors hover:bg-muted/50"
        >
          <span className="text-sm font-medium text-foreground">{label}</span>
          {isExpanded ? (
            <ChevronDown className="h-4 w-4 text-muted-foreground" />
          ) : (
            <ChevronRight className="h-4 w-4 text-muted-foreground" />
          )}
        </button>
        {isExpanded && <div className="p-2">{children}</div>}
      </div>
    )
  }

  return (
    <div className="space-y-1.5">
      <h4 className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</h4>
      <div className="space-y-1.5">{children}</div>
    </div>
  )
}
