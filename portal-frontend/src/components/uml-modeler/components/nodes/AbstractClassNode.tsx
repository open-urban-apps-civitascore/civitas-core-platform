'use client'

import type { NodeProps } from '@xyflow/react'

import type { UMLNodeData } from '../../types/diagram'
import type { UMLAbstractClass, UMLParameter, UMLType } from '../../types/uml'
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

export const AbstractClassNode: React.FC<NodeProps> = ({ data, selected: isSelected = false }) => {
  const nodeData = data as UMLNodeData
  const element = nodeData.element as UMLAbstractClass

  return (
    <BaseUMLNode
      elementType="abstractClass"
      isSelected={isSelected}
      stereotype={element.stereotype}
      name={element.name}
      isRoot={element.isRoot}
    >
      {/* Attributes Section */}
      <NodeSection isEmpty={!element.attributes || element.attributes.length === 0}>
        {element.attributes?.map(attribute => (
          <NodeLine key={attribute.id}>
            {attribute.name}: {formatTypeName(attribute.type)}
            {attribute.isId && ' {id}'}
            {attribute.multiplicity && ` [${attribute.multiplicity}]`}
            {attribute.isStatic && ' {static}'}
            {attribute.isReadonly && ' {readonly}'}
            {attribute.defaultValue && ` = ${attribute.defaultValue}`}
          </NodeLine>
        ))}
      </NodeSection>

      {/* Operations Section */}
      <NodeSection isEmpty={!element.operations || element.operations.length === 0}>
        {element.operations?.map(operation => (
          <NodeLine key={operation.id} isAbstract={operation.isAbstract}>
            {operation.name}({formatParameters(operation.parameters)})
            {operation.returnType && `: ${formatTypeName(operation.returnType)}`}
            {operation.isStatic && ' {static}'}
            {operation.isAbstract && ' {abstract}'}
          </NodeLine>
        ))}
      </NodeSection>
    </BaseUMLNode>
  )
}
