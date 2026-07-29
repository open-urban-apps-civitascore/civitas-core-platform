'use client'

import type { NodeProps } from '@xyflow/react'

import type { UMLNodeData } from '../../types/diagram'
import type { UMLInterface, UMLParameter, UMLType } from '../../types/uml'
import { BaseUMLNode, NodeLine, NodeSection } from './BaseUMLNode'

// Helper to format UML type names
const formatTypeName = (type: UMLType): string => {
  if (typeof type === 'string') {
    return type
  }
  return type.name
}

// Helper to format parameter list
const formatParameters = (parameters: UMLParameter[]): string => {
  if (!parameters || parameters.length === 0) return ''
  return parameters.map(param => `${param.name}: ${formatTypeName(param.type)}`).join(', ')
}

export const InterfaceNode: React.FC<NodeProps> = ({ data, selected: isSelected = false }) => {
  const nodeData = data as UMLNodeData
  const element = nodeData.element as UMLInterface

  return (
    <BaseUMLNode
      elementType="interface"
      isSelected={isSelected}
      stereotype={element.stereotype || '<<interface>>'}
      name={element.name}
      isRoot={element.isRoot}
    >
      {/* Operations Section - Interfaces only have operations */}
      <NodeSection isEmpty={!element.operations || element.operations.length === 0}>
        {element.operations?.map(operation => (
          <NodeLine key={operation.id} isAbstract={true}>
            {operation.name}({formatParameters(operation.parameters)})
            {operation.returnType && `: ${formatTypeName(operation.returnType)}`}
            {operation.isStatic && ' {static}'}
          </NodeLine>
        ))}
      </NodeSection>
    </BaseUMLNode>
  )
}
