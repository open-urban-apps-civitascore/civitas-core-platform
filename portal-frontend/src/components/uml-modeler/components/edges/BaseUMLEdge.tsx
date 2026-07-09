'use client'

import { type EdgeProps, getBezierPath } from '@xyflow/react'
import React from 'react'

import { RELATIONSHIP_STYLES } from '../../constants/umlTypes'
import type { UMLEdge } from '../../types/diagram'
import type { UMLRelationshipType } from '../../types/uml'

interface BaseUMLEdgeProps extends EdgeProps<UMLEdge> {
  relationshipType: UMLRelationshipType
  markerEnd?: string
}

/**
 * Base component for all UML relationship edges
 * Provides common functionality for styling, labels, and path calculation
 */
export const BaseUMLEdge: React.FC<BaseUMLEdgeProps> = ({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  selected: isSelected,
  data,
  relationshipType,
  sourcePosition,
  targetPosition,
  markerEnd,
}) => {
  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
    curvature: 0.25,
  })

  const style = RELATIONSHIP_STYLES[relationshipType]
  const relationship = data?.relationship

  // Calculate middle point for labels
  const midX = (sourceX + targetX) / 2
  const midY = (sourceY + targetY) / 2

  return (
    <>
      {/* Invisible wider path for easier selection */}
      <path
        id={`${id}-selector`}
        d={edgePath}
        fill="none"
        stroke="transparent"
        strokeWidth={20}
        className="react-flow__edge-interaction"
      />
      {/* Visible edge path */}
      <path
        id={id}
        style={{
          ...style,
          stroke: isSelected ? '#0066cc' : style.stroke,
          strokeWidth: isSelected ? 2 : style.strokeWidth,
        }}
        className="react-flow__edge-path"
        d={edgePath}
        markerEnd={markerEnd ? `url(#${markerEnd})` : undefined}
      />

      {/* Source multiplicity label */}
      {relationship?.sourceMultiplicity && (
        <text
          x={sourceX + (midX - sourceX) * 0.2}
          y={sourceY + (midY - sourceY) * 0.2 - 10}
          className="react-flow__edge-text"
          style={{
            fontSize: '12px',
            fill: '#333333',
            textAnchor: 'middle',
            pointerEvents: 'none',
          }}
        >
          {relationship.sourceMultiplicity}
        </text>
      )}

      {/* Relationship name label (center) */}
      {relationship?.name && (
        <text
          x={labelX}
          y={labelY - 10}
          className="react-flow__edge-text"
          style={{
            fontSize: '12px',
            fill: '#333333',
            textAnchor: 'middle',
            pointerEvents: 'none',
            fontStyle: 'italic',
          }}
        >
          {relationship.name}
        </text>
      )}

      {/* Target multiplicity label */}
      {relationship?.targetMultiplicity && (
        <text
          x={targetX + (midX - targetX) * 0.2}
          y={targetY + (midY - targetY) * 0.2 - 10}
          className="react-flow__edge-text"
          style={{
            fontSize: '12px',
            fill: '#333333',
            textAnchor: 'middle',
            pointerEvents: 'none',
          }}
        >
          {relationship.targetMultiplicity}
        </text>
      )}

      {/* Source role label */}
      {relationship?.sourceRole && (
        <text
          x={sourceX + (midX - sourceX) * 0.3}
          y={sourceY + (midY - sourceY) * 0.3 + 15}
          className="react-flow__edge-text"
          style={{
            fontSize: '11px',
            fill: '#666666',
            textAnchor: 'middle',
            pointerEvents: 'none',
          }}
        >
          {relationship.sourceRole}
        </text>
      )}

      {/* Target role label */}
      {relationship?.targetRole && (
        <text
          x={targetX + (midX - targetX) * 0.3}
          y={targetY + (midY - targetY) * 0.3 + 15}
          className="react-flow__edge-text"
          style={{
            fontSize: '11px',
            fill: '#666666',
            textAnchor: 'middle',
            pointerEvents: 'none',
          }}
        >
          {relationship.targetRole}
        </text>
      )}
    </>
  )
}
