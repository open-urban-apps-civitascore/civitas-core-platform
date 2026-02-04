'use client'

/**
 * PipelinePalette Component
 *
 * Left sidebar containing categorized draggable node items.
 * Adapted from UML modeler's ElementPalette.tsx
 *
 */

import { useState } from 'react'

import { PIPELINE_PALETTE_CATEGORIES } from '../../_constants/paletteItems'
import { LAYOUT_DIMENSIONS } from '../../_constants/pipelineStyles'
import { PaletteCategory } from './PaletteCategory'
import { PaletteItem } from './PaletteItem'

// ============================================================================
// Props
// ============================================================================

interface PipelinePaletteProps {
  className?: string
}

// ============================================================================
// Component
// ============================================================================

/**
 * Pipeline palette component.
 * Contains all draggable node types organized by category.
 *
 */
export const PipelinePalette: React.FC<PipelinePaletteProps> = ({ className = '' }) => {
  // Track expanded state for each category
  const [expandedCategories, setExpandedCategories] = useState<Record<string, boolean>>(() => {
    // Initialize all categories as expanded by default
    const initial: Record<string, boolean> = {}
    PIPELINE_PALETTE_CATEGORIES.forEach(category => {
      initial[category.id] = !category.defaultCollapsed
    })
    return initial
  })

  /**
   * Toggles the expanded state of a category.
   */
  const toggleCategory = (categoryId: string) => {
    setExpandedCategories(prev => ({
      ...prev,
      [categoryId]: !prev[categoryId],
    }))
  }

  return (
    <div
      className={`flex flex-shrink-0 flex-col overflow-hidden border-r border-border bg-background ${className}`}
      style={{ width: LAYOUT_DIMENSIONS.paletteWidth }}
    >
      {/* Palette Header */}
      <div className="border-b border-border px-3 py-2">
        <h3 className="text-sm font-medium text-foreground">Nodes</h3>
      </div>

      {/* Categories */}
      <div className="flex-1 overflow-y-auto">
        {PIPELINE_PALETTE_CATEGORIES.map(category => (
          <PaletteCategory
            key={category.id}
            title={category.title}
            isExpanded={expandedCategories[category.id] ?? true}
            onToggle={() => toggleCategory(category.id)}
          >
            <div className="space-y-1">
              {category.items.map(item => (
                <PaletteItem
                  key={item.type}
                  nodeType={item.type}
                  label={item.label}
                  icon={item.icon}
                  description={item.description}
                />
              ))}
            </div>
          </PaletteCategory>
        ))}
      </div>
    </div>
  )
}
