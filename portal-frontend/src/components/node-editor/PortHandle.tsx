'use client'

import { Handle, Position } from '@xyflow/react'

import type { PortType } from './types'

export type PortStatus = 'default' | 'mapped' | 'consumed' | 'unmapped' | 'mismatch' | 'unused'

export const MISMATCH_COLOR = '#dc2626'

const STATUS_COLOR: Record<PortStatus, string> = {
  default: '#64748b',
  mapped: '#16a34a',
  consumed: '#16a34a',
  unmapped: '#d97706',
  mismatch: MISMATCH_COLOR,
  unused: '#cbd5e1',
}

interface PortHandleProps {
  id: string
  portType: PortType
  side: 'left' | 'right'
  status?: PortStatus
}

const SHAPE_SIZE = 11

/**
 * The handle box stays an unrotated 11×11 square that React Flow can measure cleanly:
 * it derives the edge anchor from the handle's `getBoundingClientRect` top plus
 * `offsetHeight / 2`, and a `rotate(45deg)` on the handle itself inflates the bounding
 * box (diagonal ≈ 15.6px) so the anchor drifts upward — more for diamonds than circles,
 * which is why the drift grew on OBJECT rows. Rotation and per-type sizing therefore live
 * on the inner shape; centering the shape via flexbox keeps the handle's own box square.
 */
const shapeStyle = (portType: PortType, color: string): React.CSSProperties => {
  const size = portType === 'geometry' ? 9 : 11
  return {
    pointerEvents: 'none',
    width: size,
    height: size,
    background: portType === 'geometry' ? color : '#fff',
    border: `2px solid ${color}`,
    borderRadius: portType === 'scalar' ? '50%' : 2,
    transform: portType === 'object' || portType === 'geometry' ? 'rotate(45deg)' : undefined,
  }
}

/** Shape encodes the port type (§8): circle=scalar, hexagon=geometry, square=array, diamond=object. */
export const PortHandle = ({ id, portType, side, status = 'default' }: PortHandleProps) => (
  <Handle
    type={side === 'left' ? 'target' : 'source'}
    position={side === 'left' ? Position.Left : Position.Right}
    id={id}
    style={{
      position: 'relative',
      top: 'auto',
      left: 'auto',
      right: 'auto',
      // Neutralize React Flow's base `.react-flow__handle-left/right` transform
      // (`translate(±50%, -50%)`); the handle is a flex child here, so flexbox handles
      // centering and that translate would otherwise pull the box off the row's midline.
      transform: 'none',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      width: SHAPE_SIZE,
      height: SHAPE_SIZE,
      background: 'transparent',
      border: 'none',
    }}
  >
    <span style={shapeStyle(portType, STATUS_COLOR[status])} />
  </Handle>
)
