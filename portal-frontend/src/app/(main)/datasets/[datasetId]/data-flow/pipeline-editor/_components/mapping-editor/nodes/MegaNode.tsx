'use client'

import type { NodeProps } from '@xyflow/react'
import type { ReactNode } from 'react'
import { Fragment } from 'react'

import type { PortStatus } from '@/components/node-editor'
import { PortHandle } from '@/components/node-editor'

import type { FieldNode } from '../_types'

export type MegaRole = 'source' | 'target'

export interface MegaNodeData extends Record<string, unknown> {
  role: MegaRole
  schemaName: string
  fields: FieldNode[]
  portStatus?: Record<string, PortStatus>
}

interface RowProps {
  field: FieldNode
  role: MegaRole
  depth: number
  status?: PortStatus
}

const Row = ({ field, role, depth, status }: RowProps) => {
  const pad = 8 + depth * 12
  const typeBadge = <span className="text-[10px] uppercase text-muted-foreground">{field.type}</span>

  if (role === 'target') {
    return (
      <div className="flex items-center gap-2 py-0.5 text-xs">
        <PortHandle id={field.path} portType={field.portType} side="left" status={status} />
        <span className="truncate" style={{ paddingLeft: pad }}>
          {field.name}
        </span>
        <span className="ml-auto pr-2">{typeBadge}</span>
      </div>
    )
  }

  return (
    <div className="flex items-center gap-2 py-0.5 text-xs">
      <span className="truncate pl-2" style={{ paddingLeft: pad }}>
        {field.name}
      </span>
      <span className="ml-auto">{typeBadge}</span>
      <PortHandle id={field.path} portType={field.portType} side="right" status={status} />
    </div>
  )
}

const renderRows = (
  fields: FieldNode[],
  role: MegaRole,
  depth: number,
  status?: Record<string, PortStatus>,
): ReactNode =>
  fields.map(field => (
    <Fragment key={field.path}>
      <Row field={field} role={role} depth={depth} status={status?.[field.path]} />
      {field.children && renderRows(field.children, role, depth + 1, status)}
    </Fragment>
  ))

export const MegaNode = ({ data }: NodeProps) => {
  const d = data as MegaNodeData
  const title = `${d.role === 'source' ? 'SOURCE' : 'TARGET'} · ${d.schemaName}`

  return (
    <div className="w-[240px] rounded-md border border-border bg-background shadow-sm">
      <div className="border-b border-border bg-muted/50 px-2 py-1 text-xs font-semibold tracking-wide">{title}</div>
      <div className="py-1">{renderRows(d.fields, d.role, 0, d.portStatus)}</div>
    </div>
  )
}
