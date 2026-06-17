'use client'

/**
 * PipelinePalette Component
 *
 * Left sidebar containing categorized draggable node items.
 * Categories and items are DERIVED from the node registry — adding a node type only
 * requires a new registry entry, not edits here.
 *
 */

import { useTranslations } from 'next-intl'
import { useMemo, useState } from 'react'

import { PaletteCategory } from '@/components/node-editor/palette/PaletteCategory'
import { PaletteItem } from '@/components/node-editor/palette/PaletteItem'

import { PIPELINE_NODE_DEFS, type PipelineNodeDef } from '../../_config/nodeRegistry'
import { NODE_CATEGORY_ORDER, type NodeCategory } from '../../_constants/nodeCategories'
import { LAYOUT_DIMENSIONS } from '../../_constants/pipelineStyles'

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
  const t = useTranslations('pipelineEditor')

  // Group registry node defs by category, preserving the configured category order.
  const categories = useMemo(() => {
    const byCategory = new Map<NodeCategory, PipelineNodeDef[]>()
    for (const def of PIPELINE_NODE_DEFS) {
      const list = byCategory.get(def.category) ?? []
      list.push(def)
      byCategory.set(def.category, list)
    }
    return NODE_CATEGORY_ORDER.filter(id => byCategory.has(id)).map(id => ({
      id,
      defs: byCategory.get(id) ?? [],
    }))
  }, [])

  // Track expanded state for each category (all expanded by default)
  const [expandedCategories, setExpandedCategories] = useState<Record<string, boolean>>(() =>
    Object.fromEntries(categories.map(c => [c.id, true])),
  )

  const toggleCategory = (categoryId: string) => {
    setExpandedCategories(prev => ({
      ...prev,
      [categoryId]: !prev[categoryId],
    }))
  }

  return (
    <div
      className={`flex h-full flex-shrink-0 flex-col overflow-hidden bg-background ${className}`}
      style={{ width: LAYOUT_DIMENSIONS.paletteWidth }}
    >
      {/* Palette Header */}
      <div className="border-b border-border px-3 py-2">
        <h3 className="text-sm font-medium text-foreground">{t('palette.title')}</h3>
      </div>

      {/* Categories */}
      <div className="flex-1 overflow-y-auto">
        {categories.map(category => (
          <PaletteCategory
            key={category.id}
            isCollapsible
            label={t(`categories.${category.id}`)}
            isExpanded={expandedCategories[category.id] ?? true}
            onToggle={() => toggleCategory(category.id)}
          >
            <div className="space-y-1">
              {category.defs.map(def => (
                <PaletteItem
                  key={def.type}
                  type={def.type}
                  label={t(`paletteItems.${def.paletteKey}`)}
                  icon={def.icon}
                  description={t(`paletteItems.${def.paletteKey}Desc`)}
                />
              ))}
            </div>
          </PaletteCategory>
        ))}
      </div>
    </div>
  )
}
