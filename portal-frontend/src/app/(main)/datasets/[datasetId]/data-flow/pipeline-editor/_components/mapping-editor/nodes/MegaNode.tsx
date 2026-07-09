'use client'

import type { NodeProps } from '@xyflow/react'
import { useUpdateNodeInternals } from '@xyflow/react'
import { useTranslations } from 'next-intl'
import type { ReactNode } from 'react'
import { Fragment, useEffect } from 'react'

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
  // `field.type` already carries the concrete type, incl. geometries (Point, Polygon, …).
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

const HEADER_CLASS: Record<MegaRole, string> = {
  source:
    'border-b border-blue-200 bg-blue-50 px-3 py-1.5 text-xs font-semibold tracking-wide text-blue-800 dark:border-blue-800 dark:bg-blue-950/60 dark:text-blue-300',
  target:
    'border-b border-emerald-200 bg-emerald-50 px-3 py-1.5 text-xs font-semibold tracking-wide text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-300',
}

export const MegaNode = ({ id, data }: NodeProps) => {
  const t = useTranslations('pipelineEditor.mappingEditor.node')
  const d = data as MegaNodeData
  const title = `${d.role === 'source' ? t('input') : t('output')} · ${d.schemaName}`

  // React Flow caches each handle's position on first mount, dividing by the zoom
  // active at that moment. The editor mounts at zoom 1 and `fitView` then animates to
  // the fitted zoom, so the cached handle Y values stay scaled for the wrong zoom —
  // the edge anchors drift further from their port the lower the field sits. Re-measure
  // once the fitView animation has settled (and again on the next frame) so the bounds
  // reflect the final zoom. 400ms covers React Flow's default fitView animation.
  const updateNodeInternals = useUpdateNodeInternals()
  useEffect(() => {
    const remeasure = () => updateNodeInternals(id)
    const frame = requestAnimationFrame(remeasure)
    const timer = setTimeout(remeasure, 400)
    return () => {
      cancelAnimationFrame(frame)
      clearTimeout(timer)
    }
  }, [id, d.fields, updateNodeInternals])

  return (
    <div className="min-w-[300px] rounded-md border border-border bg-background shadow-sm">
      <div className={HEADER_CLASS[d.role]}>{title}</div>
      <div className="py-1">{renderRows(d.fields, d.role, 0, d.portStatus)}</div>
    </div>
  )
}
