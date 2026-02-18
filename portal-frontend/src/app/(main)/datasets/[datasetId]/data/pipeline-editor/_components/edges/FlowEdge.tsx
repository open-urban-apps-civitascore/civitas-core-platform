'use client'

/**
 * FlowEdge Component
 *
 * Custom edge component for pipeline flow connections.
 * Styled smooth step edge with selection highlight.
 *
 */

import { type EdgeProps, getSmoothStepPath } from '@xyflow/react'

import { PIPELINE_COLORS } from '../../_constants/pipelineStyles'

// ============================================================================
// Component
// ============================================================================

/**
 * Custom flow edge component.
 * Renders a smooth step path between nodes with selection styling.
 *
 */
export const FlowEdge: React.FC<EdgeProps> = ({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  selected: isSelected,
  markerEnd,
}) => {
  const [edgePath] = getSmoothStepPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
  })

  return (
    <g className="react-flow__edge">
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
        d={edgePath}
        fill="none"
        stroke={isSelected ? PIPELINE_COLORS.edgeSelected : PIPELINE_COLORS.edgeDefault}
        strokeWidth={isSelected ? 2.5 : 2}
        markerEnd={markerEnd}
        className="react-flow__edge-path"
        style={{
          transition: 'stroke 0.15s ease, stroke-width 0.15s ease',
        }}
      />
    </g>
  )
}
