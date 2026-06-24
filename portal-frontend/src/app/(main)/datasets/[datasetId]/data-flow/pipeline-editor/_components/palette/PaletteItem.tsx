'use client'

/**
 * PaletteItem Component
 *
 * Draggable node item in the palette.
 * Initiates drag-and-drop to the canvas.
 * Adapted from UML modeler's PaletteItem.tsx
 *
 */

import type { LucideIcon } from 'lucide-react'
import { GripVertical } from 'lucide-react'
import { useCallback } from 'react'

import { cn } from '@/lib/utils'

import type { PipelineNodeType } from '../../_types/pipeline'

// ============================================================================
// Props
// ============================================================================

interface PaletteItemProps {
  /** Node type identifier */
  nodeType: PipelineNodeType
  /** Display label */
  label: string
  /** Lucide icon component to display */
  icon: LucideIcon
  /** Short description (shown in tooltip) */
  description: string
  /** When true, the item cannot be dragged and is rendered as visually disabled. */
  isDisabled?: boolean
}

// ============================================================================
// Component
// ============================================================================

/**
 * Draggable palette item component.
 * Drag to the canvas to create a new node.
 *
 */
export const PaletteItem: React.FC<PaletteItemProps> = ({ nodeType, label, icon, description, isDisabled = false }) => {
  // Rename for JSX - React components must be PascalCase
  const IconComponent = icon

  /**
   * Handles drag start event.
   * Sets the node type in dataTransfer for the canvas to receive.
   */
  const onDragStart = useCallback(
    (event: React.DragEvent) => {
      event.dataTransfer.setData('application/reactflow', nodeType)
      event.dataTransfer.effectAllowed = 'move'
    },
    [nodeType],
  )

  return (
    <div
      draggable={!isDisabled}
      onDragStart={isDisabled ? undefined : onDragStart}
      aria-disabled={isDisabled}
      className={cn(
        'group flex items-center gap-2 rounded-md border border-border bg-background p-2 transition-colors',
        isDisabled ? 'cursor-not-allowed opacity-50' : 'cursor-move hover:border-primary/50 hover:bg-muted/50',
      )}
      title={description}
    >
      {/* Icon */}
      <div className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded border border-border bg-muted/30">
        <IconComponent className="h-5 w-5 text-foreground" />
      </div>

      {/* Label and Description */}
      <div className="min-w-0 flex-1">
        <div className="truncate text-sm font-medium text-foreground">{label}</div>
        <div className="truncate text-xs text-muted-foreground">{description}</div>
      </div>

      {/* Drag Handle Indicator */}
      {!isDisabled && (
        <div className="flex-shrink-0 opacity-0 transition-opacity group-hover:opacity-100">
          <GripVertical className="h-4 w-4 text-muted-foreground" />
        </div>
      )}
    </div>
  )
}
