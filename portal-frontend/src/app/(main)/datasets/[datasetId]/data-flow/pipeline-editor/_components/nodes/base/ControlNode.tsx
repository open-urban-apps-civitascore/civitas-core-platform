'use client'

/**
 * ControlNode Component
 *
 * UML Activity Diagram start / end nodes rendered as circles:
 *  - start: filled circle, output handle only (right)
 *  - end:   outlined circle with thick border, input handle only (left)
 *
 * Selected via the registry def's `shape` (`'controlStart'` | `'controlEnd'`),
 * so adding/altering control nodes stays a registry-only change.
 */

import { Handle, Position } from '@xyflow/react'
import type { CSSProperties } from 'react'

import { NODE_DIMENSIONS, PIPELINE_COLORS } from '../../../_constants/pipelineStyles'

export type ControlVariant = 'start' | 'end'

const getContainerStyle = (variant: ControlVariant): CSSProperties => ({
  width: NODE_DIMENSIONS.controlNode.size,
  height: NODE_DIMENSIONS.controlNode.size,
  borderRadius: '50%',
  backgroundColor: variant === 'start' ? PIPELINE_COLORS.startNodeFill : '#ffffff',
  border:
    variant === 'end'
      ? `${NODE_DIMENSIONS.controlNode.borderWidth + 1}px solid ${PIPELINE_COLORS.endNodeStroke}`
      : 'none',
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

interface ControlNodeProps {
  variant: ControlVariant
  isSelected: boolean
}

/** Circular control node (UML start / end). */
export const ControlNode: React.FC<ControlNodeProps> = ({ variant, isSelected }) => (
  <div className={`pipeline-node pipeline-node--${variant}`} style={getContainerStyle(variant)}>
    {variant === 'start' ? (
      <Handle type="source" position={Position.Right} id="output" style={getHandleStyle(isSelected)} />
    ) : (
      <Handle type="target" position={Position.Left} id="input" style={getHandleStyle(isSelected)} />
    )}
  </div>
)
