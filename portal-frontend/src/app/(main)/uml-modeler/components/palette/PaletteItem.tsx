'use client'

import { useCallback } from 'react'

import { UML_COLORS } from '../../constants/umlTypes'
import type { UMLElementType } from '../../types/uml'

interface PaletteItemProps {
  elementType: UMLElementType
  label: string
  description: string
  icon: string
}

export const PaletteItem: React.FC<PaletteItemProps> = ({ elementType, label, description, icon }) => {
  const onDragStart = useCallback((event: React.DragEvent, nodeType: UMLElementType) => {
    event.dataTransfer.setData('application/reactflow', nodeType)
    event.dataTransfer.effectAllowed = 'move'
  }, [])

  const colors = UML_COLORS[elementType] || UML_COLORS.class

  return (
    <div
      draggable
      onDragStart={event => onDragStart(event, elementType)}
      className="flex items-center gap-3 p-2 rounded-lg border border-gray-200 hover:border-gray-300 hover:bg-gray-50 cursor-move transition-colors group"
      title={description}
    >
      {/* Icon Preview */}
      <div
        className="flex-shrink-0 w-8 h-8 rounded border flex items-center justify-center text-xs"
        style={{
          backgroundColor: colors.background,
          borderColor: colors.border,
          color: colors.text,
        }}
      >
        {icon}
      </div>

      {/* Label and Description */}
      <div className="flex-1 min-w-0">
        <div className="font-medium text-sm text-gray-900 truncate">{label}</div>
        <div className="text-xs text-gray-500 truncate">{description}</div>
      </div>

      {/* Drag Hint */}
      <div className="opacity-0 group-hover:opacity-100 transition-opacity">
        <svg width="12" height="12" viewBox="0 0 12 12" className="text-gray-400">
          <path
            d="M2 2h2v2H2V2zm4 0h2v2H6V2zm4 0h2v2h-2V2zM2 6h2v2H2V6zm4 0h2v2H6V6zm4 0h2v2h-2V6zM2 10h2v2H2v-2zm4 0h2v2H6v-2zm4 0h2v2h-2v-2z"
            fill="currentColor"
          />
        </svg>
      </div>
    </div>
  )
}
