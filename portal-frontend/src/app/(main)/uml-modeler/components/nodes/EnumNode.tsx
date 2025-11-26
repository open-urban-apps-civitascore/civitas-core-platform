'use client'

import type { NodeProps } from '@xyflow/react'

import type { UMLNodeData } from '../../types/diagram'
import type { UMLEnumeration } from '../../types/uml'
import { BaseUMLNode, NodeLine, NodeSection } from './BaseUMLNode'

export const EnumNode: React.FC<NodeProps> = ({ data, selected: isSelected = false }) => {
  const nodeData = data as UMLNodeData
  const element = nodeData.element as UMLEnumeration

  return (
    <BaseUMLNode
      elementType="enumeration"
      isSelected={isSelected}
      stereotype={element.stereotype || '<<enumeration>>'}
      name={element.name}
    >
      {/* Literals Section - Enumerations have literal values */}
      <NodeSection isEmpty={!element.literals || element.literals.length === 0}>
        {element.literals?.map(literal => (
          <NodeLine key={literal.id}>
            {literal.name}
            {literal.value !== undefined && ` = ${literal.value}`}
          </NodeLine>
        ))}
      </NodeSection>
    </BaseUMLNode>
  )
}
