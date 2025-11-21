'use client'

import { ArrowRight, ArrowUpRight, Diamond, Gem, MoveRight, MoveUpRight } from 'lucide-react'
import { useCallback } from 'react'

import { useActiveDiagram } from '../../hooks/useActiveDiagram'
import type { UMLRelationshipType } from '../../types/uml'

interface RelationshipToolProps {
  relationshipType: UMLRelationshipType
  label: string
  description: string
  icon: string
}

// Icon mapping for relationship elements
const ICON_MAP = {
  arrowUpRight: ArrowUpRight,
  moveUpRight: MoveUpRight,
  arrowRight: ArrowRight,
  gem: Gem,
  diamond: Diamond,
  moveRight: MoveRight,
} as const

export const RelationshipTool: React.FC<RelationshipToolProps> = ({ relationshipType, label, description, icon }) => {
  const { activeRelationshipType, setActiveRelationshipType } = useActiveDiagram()

  const isActive = activeRelationshipType === relationshipType

  const handleClick = useCallback(() => {
    if (isActive) {
      setActiveRelationshipType('association')
    } else {
      setActiveRelationshipType(relationshipType)
    }
  }, [relationshipType, isActive, setActiveRelationshipType])

  return (
    <button
      onClick={handleClick}
      className={`w-full flex items-center gap-3 p-2 rounded-lg border transition-colors text-left ${
        isActive ? 'border-blue-500 bg-blue-50 text-blue-900' : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50'
      }`}
      title={description}
    >
      {/* Icon */}
      <div className="flex-shrink-0 w-8 h-8 rounded border flex items-center justify-center text-sm bg-gray-50 border-gray-300">
        {(() => {
          const IconComponent = ICON_MAP[icon as keyof typeof ICON_MAP]
          return IconComponent ? <IconComponent size={16} /> : <span>{icon}</span>
        })()}
      </div>

      {/* Label and Description */}
      <div className="flex-1 min-w-0">
        <div className="font-medium text-sm truncate">{label}</div>
        <div className="text-xs text-gray-500 truncate">{description}</div>
      </div>

      {/* Active Indicator */}
      {isActive && (
        <div className="flex-shrink-0">
          <div className="w-2 h-2 rounded-full bg-blue-500"></div>
        </div>
      )}
    </button>
  )
}
