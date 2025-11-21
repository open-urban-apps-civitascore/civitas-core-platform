'use client'

import { ChevronLeft, ChevronRight, Settings } from 'lucide-react'
import { useState } from 'react'

import { useActiveDiagram } from '../../hooks/useActiveDiagram'
import { EdgePropertyEditor } from './EdgePropertyEditor'
import { NodePropertyEditor } from './NodePropertyEditor'

interface PropertyInspectorProps {
  className?: string
}

export const PropertyInspector: React.FC<PropertyInspectorProps> = ({ className = '' }) => {
  const { selectedNode, selectedEdge } = useActiveDiagram()
  const [isCollapsed, setIsCollapsed] = useState(false)

  // Get the currently selected element
  const selectedElement = selectedNode || selectedEdge

  if (isCollapsed) {
    return (
      <div className={`w-12 bg-white border-l border-gray-200 ${className}`}>
        <button
          onClick={() => setIsCollapsed(false)}
          className="w-full h-12 flex items-center justify-center hover:bg-gray-50 border-b border-gray-200"
          title="Expand Inspector"
        >
          <ChevronLeft size={16} />
        </button>
      </div>
    )
  }

  return (
    <div className={`w-80 bg-white border-l border-gray-200 overflow-y-auto ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between p-3 border-b border-gray-200 bg-gray-50">
        <div className="flex items-center gap-2">
          <Settings size={16} className="text-gray-600" />
          <h3 className="font-semibold text-sm text-gray-700">Properties</h3>
        </div>
        <button
          onClick={() => setIsCollapsed(true)}
          className="p-1 hover:bg-gray-200 rounded"
          title="Collapse Inspector"
        >
          <ChevronRight size={16} />
        </button>
      </div>

      {/* Content */}
      <div className="p-4">
        {selectedNode && (
          <div>
            <div className="mb-4">
              <h4 className="font-medium text-sm text-gray-700 mb-2">
                {selectedNode.data.element.type === 'class' && 'Class Properties'}
                {selectedNode.data.element.type === 'interface' && 'Interface Properties'}
                {selectedNode.data.element.type === 'abstractClass' && 'Abstract Class Properties'}
                {selectedNode.data.element.type === 'enumeration' && 'Enumeration Properties'}
              </h4>
            </div>
            <NodePropertyEditor node={selectedNode} />
          </div>
        )}

        {selectedEdge && (
          <div>
            <div className="mb-4">
              <h4 className="font-medium text-sm text-gray-700 mb-2">Relationship Properties</h4>
            </div>
            <EdgePropertyEditor edge={selectedEdge} />
          </div>
        )}

        {!selectedElement && (
          <div className="text-center py-8 text-gray-500">
            <Settings size={48} className="mx-auto mb-3 opacity-30" />
            <p className="text-sm">No element selected</p>
            <p className="text-xs mt-1">Click on a UML element to edit its properties</p>
          </div>
        )}
      </div>
    </div>
  )
}
