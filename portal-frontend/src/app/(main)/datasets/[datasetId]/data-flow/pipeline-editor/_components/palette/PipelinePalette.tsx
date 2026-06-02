'use client'

/**
 * PipelinePalette Component
 *
 * Left sidebar containing categorized draggable node items.
 * Adapted from UML modeler's ElementPalette.tsx
 *
 */

import { useTranslations } from 'next-intl'
import { useState } from 'react'

import { PIPELINE_PALETTE_CATEGORIES } from '../../_constants/paletteItems'
import { LAYOUT_DIMENSIONS } from '../../_constants/pipelineStyles'
import type { PipelineNodeType } from '../../_types/pipeline'
import { PaletteCategory } from './PaletteCategory'
import { PaletteItem } from './PaletteItem'

// ============================================================================
// Props
// ============================================================================

interface PipelinePaletteProps {
  className?: string
}

// ============================================================================
// Translation Key Mapping
// ============================================================================

/**
 * Maps node types to their translation keys in paletteItems namespace
 */
const NODE_TYPE_TO_TRANSLATION_KEY: Record<PipelineNodeType, string> = {
  start: 'flowStart',
  end: 'flowEnd',
  dataSource: 'dataSource',
  cron: 'cron',
  frost: 'frostServer',
  geoPersistence: 'geoPersistence',
  mapping: 'mapping',
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
        <h3 className="text-sm font-medium text-foreground">{t('palette.title')}</h3>
      </div>

      {/* Categories */}
      <div className="flex-1 overflow-y-auto">
        {PIPELINE_PALETTE_CATEGORIES.map(category => {
          // Translate category title using category.id
          const translatedTitle = t(`categories.${category.id}`)

          return (
            <PaletteCategory
              key={category.id}
              title={translatedTitle}
              isExpanded={expandedCategories[category.id] ?? true}
              onToggle={() => toggleCategory(category.id)}
            >
              <div className="space-y-1">
                {category.items.map(item => {
                  // Translate item label and description using mapping
                  const translationKey = NODE_TYPE_TO_TRANSLATION_KEY[item.type]
                  const translatedLabel = t(`paletteItems.${translationKey}`)
                  const translatedDescription = t(`paletteItems.${translationKey}Desc`)

                  return (
                    <PaletteItem
                      key={item.type}
                      nodeType={item.type}
                      label={translatedLabel}
                      icon={item.icon}
                      description={translatedDescription}
                    />
                  )
                })}
              </div>
            </PaletteCategory>
          )
        })}
      </div>
    </div>
  )
}
