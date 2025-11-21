'use client'

import { useCallback } from 'react'

import { useUMLDiagram } from '../../hooks/UMLDiagramContext'
import type { UMLRelationshipType } from '../../types/uml'

interface RelationshipToolProps {
  relationshipType: UMLRelationshipType
  label: string
  description: string
  icon: string
}

export const RelationshipTool: React.FC<RelationshipToolProps> = ({ relationshipType, label, description, icon }) => {
  const { activeRelationshipType, setActiveRelationshipType } = useUMLDiagram()

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
        {icon}
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
