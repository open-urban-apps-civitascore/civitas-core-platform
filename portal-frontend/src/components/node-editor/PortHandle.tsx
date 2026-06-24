'use client'

import { Handle, Position } from '@xyflow/react'

import type { PortType } from './types'

export type PortStatus = 'default' | 'mapped' | 'consumed' | 'unmapped' | 'mismatch' | 'unused'

const STATUS_COLOR: Record<PortStatus, string> = {
  default: '#64748b',
  mapped: '#16a34a',
  consumed: '#16a34a',
  unmapped: '#d97706',
  mismatch: '#dc2626',
  unused: '#cbd5e1',
}

interface PortHandleProps {
  id: string
  portType: PortType
  side: 'left' | 'right'
  status?: PortStatus
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
      width: portType === 'geometry' ? 9 : 11,
      height: portType === 'geometry' ? 9 : 11,
      background: portType === 'geometry' ? STATUS_COLOR[status] : '#fff',
      border: `2px solid ${STATUS_COLOR[status]}`,
      transform: portType === 'object' || portType === 'geometry' ? 'rotate(45deg)' : 'none',
      borderRadius: portType === 'scalar' ? '50%' : 2,
    }}
  />
)
