'use client'

import type { NodeProps } from '@xyflow/react'
import { useUpdateNodeInternals } from '@xyflow/react'
import { useTranslations } from 'next-intl'
import type { ReactNode } from 'react'
import { Fragment, useEffect } from 'react'

import type { PortStatus } from '@/components/node-editor'
import { PORT_HANDLE_SIZE, PortHandle } from '@/components/node-editor'

import type { FieldNode } from '../_types'

export type MegaRole = 'source' | 'target'

export interface MegaNodeData extends Record<string, unknown> {
  role: MegaRole
  schemaName: string
  /** data structure version, e.g. "4.0.0"; null for a draft. */
  version?: string | null
  fields: FieldNode[]
  portStatus?: Record<string, PortStatus>
}

interface RowProps {
  field: FieldNode
  role: MegaRole
  depth: number
  status?: PortStatus
}

/** Object/array fields represent a nested entity rather than a plain attribute. */
const isEntityField = (field: FieldNode): boolean => field.portType === 'object' || field.portType === 'array'

const padFor = (depth: number): number => 8 + depth * 12

// Matches the row's `gap-2` Tailwind class (0.5rem = 8px at the default root font size).
const ROW_GAP_PX = 8

/**
 * Left offset for the divider so it lines up with the row's own text, not the row's edge.
 * A target row's handle comes BEFORE the name (source→target on the row's left), so its text
 * starts `PORT_HANDLE_SIZE + gap` further right than a source row's, whose handle trails at
 * the far right and doesn't push the text over at all.
 */
const dividerInset = (role: MegaRole, depth: number): number =>
  role === 'target' ? PORT_HANDLE_SIZE + ROW_GAP_PX + padFor(depth) : padFor(depth)

const Row = ({ field, role, depth, status }: RowProps) => {
  const pad = padFor(depth)
  const nameClass = `truncate leading-tight ${isEntityField(field) ? 'font-semibold' : ''}`
  // `field.type` already carries the concrete type, incl. geometries (Point, Polygon, …).
  const typeBadgeClass = `text-[10px] uppercase leading-tight ${
    status === 'candidate' ? 'text-primary' : 'text-muted-foreground'
  }`
  const typeBadge = <span className={typeBadgeClass}>{field.type}</span>
  // Dims the whole attribute — name, type and handle — while it's an incompatible drop target.
  const rowClass = `flex items-center gap-2 py-0.5 text-xs ${status === 'incompatible' ? 'opacity-40' : ''}`

  if (role === 'target') {
    return (
      <div className={rowClass}>
        <PortHandle id={field.path} portType={field.portType} side="left" status={status} />
        <span className={nameClass} style={{ paddingLeft: pad }}>
          {field.name}
        </span>
        <span className="ml-auto flex pr-2">{typeBadge}</span>
      </div>
    )
  }

  return (
    <div className={rowClass}>
      <span className={`pl-2 ${nameClass}`} style={{ paddingLeft: pad }}>
        {field.name}
      </span>
      <span className="ml-auto flex">{typeBadge}</span>
      <PortHandle id={field.path} portType={field.portType} side="right" status={status} />
    </div>
  )
}

/** Primitive/geometry attributes before nested entities, with a divider ahead of each entity. */
const renderRows = (
  fields: FieldNode[],
  role: MegaRole,
  depth: number,
  status?: Record<string, PortStatus>,
): ReactNode => {
  const attributes = fields.filter(field => !isEntityField(field))
  const entities = fields.filter(isEntityField)
  const ordered = [...attributes, ...entities]

  return ordered.map((field, index) => (
    <Fragment key={field.path}>
      {index > 0 && isEntityField(field) && (
        <div className="border-t border-border" style={{ marginLeft: dividerInset(role, depth) }} />
      )}
      <Row field={field} role={role} depth={depth} status={status?.[field.path]} />
      {field.children && renderRows(field.children, role, depth + 1, status)}
    </Fragment>
  ))
}

const HEADER_CLASS: Record<MegaRole, string> = {
  source:
    'border-b border-blue-200 bg-blue-50 px-3 py-1.5 text-xs font-semibold tracking-wide text-blue-800 dark:border-blue-800 dark:bg-blue-950/60 dark:text-blue-300',
  target:
    'border-b border-emerald-200 bg-emerald-50 px-3 py-1.5 text-xs font-semibold tracking-wide text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-300',
}

export const MegaNode = ({ id, data }: NodeProps) => {
  const t = useTranslations('pipelineEditor.mappingEditor.node')
  const d = data as MegaNodeData
  const title = [d.role === 'source' ? t('input') : t('output'), d.schemaName, d.version ? `v${d.version}` : null]
    .filter(Boolean)
    .join(' · ')

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
