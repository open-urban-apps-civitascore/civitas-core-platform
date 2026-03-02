'use client'

/**
 * PaletteCategory Component
 *
 * Collapsible category container for palette items.
 * Adapted from UML modeler's PaletteCategory.tsx
 *
 */

import { ChevronDown, ChevronRight } from 'lucide-react'
import type { ReactNode } from 'react'

// ============================================================================
// Props
// ============================================================================

interface PaletteCategoryProps {
  /** Category title displayed in the header */
  title: string
  /** Whether the category is expanded */
  isExpanded: boolean
  /** Callback when expand/collapse is toggled */
  onToggle: () => void
  /** Category content (usually PaletteItem components) */
  children: ReactNode
}

// ============================================================================
// Component
// ============================================================================

/**
 * Collapsible category for organizing palette items.
 *
 */
export const PaletteCategory: React.FC<PaletteCategoryProps> = ({ title, isExpanded, onToggle, children }) => {
  return (
    <div className="border-b border-border">
      {/* Category Header */}
      <button
        onClick={onToggle}
        className="flex w-full items-center justify-between bg-muted/30 px-3 py-2 text-left transition-colors hover:bg-muted/50"
      >
        <span className="text-sm font-medium text-foreground">{title}</span>
        {isExpanded ? (
          <ChevronDown className="h-4 w-4 text-muted-foreground" />
        ) : (
          <ChevronRight className="h-4 w-4 text-muted-foreground" />
        )}
      </button>

      {/* Category Content */}
      {isExpanded && <div className="p-2">{children}</div>}
    </div>
  )
}
