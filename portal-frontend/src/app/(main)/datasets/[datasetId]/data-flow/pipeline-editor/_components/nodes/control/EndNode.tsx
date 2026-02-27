'use client'

/**
 * EndNode Component
 *
 * UML Activity Diagram End node - an outlined circle with thick border.
 * This node only has an input handle (left side) since it's the exit point.
 * End nodes are always "configured" as they require no user input.
 *
 */

import type { NodeProps } from '@xyflow/react'
import { Handle, Position } from '@xyflow/react'
import type { CSSProperties } from 'react'

import { NODE_DIMENSIONS, PIPELINE_COLORS } from '../../../_constants/pipelineStyles'

// ============================================================================
// Styles
// ============================================================================

const getEndNodeStyle = (): CSSProperties => ({
  width: NODE_DIMENSIONS.controlNode.size,
  height: NODE_DIMENSIONS.controlNode.size,
  borderRadius: '50%',
  backgroundColor: '#ffffff',
  border: `${NODE_DIMENSIONS.controlNode.borderWidth + 1}px solid ${PIPELINE_COLORS.endNodeStroke}`,
  boxShadow: '0 1px 3px rgba(0,0,0,0.2)',
  cursor: 'grab',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
})

const getHandleStyle = (isSelected: boolean): CSSProperties => ({
  width: NODE_DIMENSIONS.handle.size,
  height: NODE_DIMENSIONS.handle.size,
  backgroundColor: isSelected ? '#3b82f6' : '#666666',
  border: '2px solid #ffffff',
})

// ============================================================================
// Component
// ============================================================================

/**
 * End node component - outlined circle with thick border.
 * Exit point for pipeline flow. Only has input handle (left).
 *
 */
export const EndNode: React.FC<NodeProps> = ({ selected: isSelected = false }) => {
  return (
    <div className="pipeline-node pipeline-node--end" style={getEndNodeStyle()}>
      {/* Input handle only (left side) - flow ends here */}
      <Handle type="target" position={Position.Left} id="input" style={getHandleStyle(isSelected)} />
    </div>
  )
}
