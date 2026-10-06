'use client'

import { Handle, Position } from '@xyflow/react'

import type { PortType } from './types'

export type PortStatus =
  | 'default'
  | 'mapped'
  | 'consumed'
  | 'unmapped'
  | 'mismatch'
  | 'unused'
  // The two below are transient: only assigned while a connection is being dragged.
  | 'candidate'
  | 'incompatible'

export const MISMATCH_COLOR = 'var(--destructive)'
export const CANDIDATE_COLOR = 'var(--primary)'
/** A dimmed neutral, for states that are present but not actionable (unused/incompatible). */
const MUTED_LIGHT = 'color-mix(in oklch, var(--muted-foreground) 45%, transparent)'

const STATUS_COLOR: Record<PortStatus, string> = {
  default: 'var(--muted-foreground)',
  mapped: 'var(--success)',
  consumed: 'var(--success)',
  unmapped: 'var(--warn)',
  mismatch: MISMATCH_COLOR,
  unused: MUTED_LIGHT,
  candidate: CANDIDATE_COLOR,
  incompatible: MUTED_LIGHT,
}

interface PortHandleProps {
  id: string
  portType: PortType
  side: 'left' | 'right'
  status?: PortStatus
}

/** The handle's box size — exported so callers (e.g. MegaNode's divider) can align to it. */
export const PORT_HANDLE_SIZE = 11
const SHAPE_SIZE = PORT_HANDLE_SIZE

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
      transform: 'none',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      width: SHAPE_SIZE,
      height: SHAPE_SIZE,
      // The handle is a flex child of its row; without this, a tight row (long field name,
      // long translated label, low zoom) shrinks the handle's width — but not its height,
      // since width is the row's main axis — squashing the circle/diamond/square into an oval.
      flexShrink: 0,
      flexGrow: 0,
      background: 'transparent',
      border: 'none',
    }}
  >
    <span style={shapeStyle(portType, STATUS_COLOR[status])} />
  </Handle>
)
