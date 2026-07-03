'use client'

import type { NodeProps } from '@xyflow/react'

import type { UMLNodeData } from '../../types/diagram'
import type { UMLClass, UMLType } from '../../types/uml'
import { VISIBILITY_SYMBOLS } from '../../types/uml'
import { BaseUMLNode, NodeLine, NodeSection } from './BaseUMLNode'

// Helper to format UML type names
const formatTypeName = (type: UMLType): string => {
  if (typeof type === 'string') {
    return type
  }
  return type.name
}

export const ClassNode: React.FC<NodeProps> = ({ data, selected: isSelected = false }) => {
  const nodeData = data as UMLNodeData
  const element = nodeData.element as UMLClass

  return (
    <BaseUMLNode elementType="class" isSelected={isSelected} stereotype={element.stereotype} name={element.name}>
      {/* Attributes Section */}
      <NodeSection isEmpty={!element.attributes || element.attributes.length === 0}>
        {element.attributes?.map(attribute => (
          <NodeLine key={attribute.id}>
            {VISIBILITY_SYMBOLS[attribute.visibility]} {attribute.name}: {formatTypeName(attribute.type)}
            {attribute.isId && ' {id}'}
            {attribute.multiplicity && ` [${attribute.multiplicity}]`}
            {attribute.isStatic && ' {static}'}
            {attribute.isReadonly && ' {readonly}'}
            {attribute.defaultValue && ` = ${attribute.defaultValue}`}
          </NodeLine>
        ))}
      </NodeSection>
    </BaseUMLNode>
  )
}
