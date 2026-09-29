'use client'

import { useReactFlow } from '@xyflow/react'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'

import { ELEMENT_PALETTE_ITEMS, RELATIONSHIP_PALETTE_ITEMS } from '../../constants/paletteItems'
import { useReadOnly } from '../../hooks/use-read-only'
import { PaletteCategory } from './PaletteCategory'
import { PaletteItem } from './PaletteItem'
import { RelationshipTool } from './RelationshipTool'

interface ElementPaletteProps {
  className?: string
}

export const ElementPalette: React.FC<ElementPaletteProps> = props => {
  const { className = '' } = props
  const { isReadOnly } = useReadOnly()
  const { fitView } = useReactFlow()
  const [isCollapsed, setIsCollapsed] = useState(false)
  const [areClassesExpanded, setAreClassesExpanded] = useState(true)
  const [areRelationshipsExpanded, setAreRelationshipsExpanded] = useState(true)

  const togglePalette = useCallback(
    (isNextCollapsed: boolean) => {
      setIsCollapsed(isNextCollapsed)
      requestAnimationFrame(() => {
        void fitView({ padding: 0.1 })
      })
    },
    [fitView],
  )

  useEffect(() => {
    togglePalette(isReadOnly)
  }, [isReadOnly, togglePalette])

  if (isReadOnly) return null

  if (isCollapsed) {
    return (
      <div className={`w-12 bg-white border-r border-gray-200 ${className}`}>
        <button
          onClick={() => togglePalette(false)}
          className="w-full h-12 flex items-center justify-center hover:bg-gray-50 border-b border-gray-200"
          title="Expand Palette"
        >
          <ChevronRight size={16} />
        </button>
      </div>
    )
  }

  return (
    <div className={`w-64 bg-white border-r border-gray-200 overflow-y-auto ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between p-3 border-b border-gray-200 bg-gray-50">
        <h3 className="font-semibold text-sm text-gray-700">Elements</h3>
        <button onClick={() => togglePalette(true)} className="p-1 hover:bg-gray-200 rounded" title="Collapse Palette">
          <ChevronDown size={16} />
        </button>
      </div>

      {/* Classes Category */}
      <PaletteCategory
        title="Classes"
        isExpanded={areClassesExpanded}
        onToggle={() => setAreClassesExpanded(!areClassesExpanded)}
      >
        <div className="space-y-1 p-2">
          {ELEMENT_PALETTE_ITEMS.map(item => (
            <PaletteItem
              key={item.id}
              elementType={item.type}
              label={item.label}
              description={item.description}
              icon={item.icon}
            />
          ))}
        </div>
      </PaletteCategory>

      {/* Relationships Category */}
      <PaletteCategory
        title="Relationships"
        isExpanded={areRelationshipsExpanded}
        onToggle={() => setAreRelationshipsExpanded(!areRelationshipsExpanded)}
      >
        <div className="space-y-1 p-2">
          {RELATIONSHIP_PALETTE_ITEMS.map(item => (
            <RelationshipTool
              key={item.id}
              relationshipType={item.type}
              label={item.label}
              description={item.description}
              icon={item.icon}
            />
          ))}
        </div>
      </PaletteCategory>
    </div>
  )
}
