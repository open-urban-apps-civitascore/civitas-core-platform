'use client'

/**
 * StartNode Component
 *
 * UML Activity Diagram Start node - a filled black circle.
 * This node only has an output handle (right side) since it's the entry point.
 * Start nodes are always "configured" as they require no user input.
 *
 */

import type { NodeProps } from '@xyflow/react'
import { Handle, Position } from '@xyflow/react'
import type { CSSProperties } from 'react'

import { NODE_DIMENSIONS, PIPELINE_COLORS } from '../../../_constants/pipelineStyles'

// ============================================================================
// Styles
// ============================================================================

const getStartNodeStyle = (): CSSProperties => ({
  width: NODE_DIMENSIONS.controlNode.size,
  height: NODE_DIMENSIONS.controlNode.size,
  borderRadius: '50%',
  backgroundColor: PIPELINE_COLORS.startNodeFill,
  border: 'none',
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
 * Start node component - filled black circle.
 * Entry point for pipeline flow. Only has output handle (right).
 *
 */
export const StartNode: React.FC<NodeProps> = ({ selected: isSelected = false }) => {
  return (
    <div className="pipeline-node pipeline-node--start" style={getStartNodeStyle()}>
      {/* Output handle only (right side) - flow starts here */}
      <Handle type="source" position={Position.Right} id="output" style={getHandleStyle(isSelected)} />
    </div>
  )
}
