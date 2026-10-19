'use client'

import type { NodeProps } from '@xyflow/react'

import type { PortStatus } from './PortHandle'
import { PortHandle } from './PortHandle'
import type { NodeRegistry, PortDef, TransformNodeData } from './types'

interface RowProps {
  port: PortDef
  status?: PortStatus
}

// Fixed row height so input and output rows don't drift
const ROW_HEIGHT = 'h-[15px]'

/** Type badge style shared by inputs and outputs; a drag candidate only changes the color, never position or size. */
const typeBadgeClass = (status?: PortStatus) =>
  `text-[10px] uppercase leading-tight ${status === 'candidate' ? 'text-primary' : 'text-muted-foreground'}`

/**
 * A label that only states the port's type (e.g. a conversion input) is styled as a type badge,
 * like the outputs; a name (e.g. `value 1`, `longitude`) stays plain.
 */
const InputRow = ({ port, status }: RowProps) => {
  const isTypeLabel = port.label === (port.accepts?.join(' / ') ?? port.dataType ?? 'any scalar')
  return (
    <div
      className={`flex ${ROW_HEIGHT} items-center gap-1 pr-2 text-xs ${status === 'incompatible' ? 'opacity-40' : ''}`}
    >
      <PortHandle id={port.id} portType={port.type} side="left" status={status} />
      <span className={`truncate ${isTypeLabel ? typeBadgeClass(status) : 'leading-tight text-muted-foreground'}`}>
        {port.label}
      </span>
    </div>
  )
}

/**
 * When `label` and `dataType` differ (e.g. a Literal port labelled with its configured value),
 * the row is laid out like a MegaNode field row: label on the left, type badge on the right.
 * Otherwise it's just the type, flush right next to the handle.
 */
const OutputRow = ({ port, status }: RowProps) => {
  const hasSeparateType = port.dataType != null && port.dataType !== port.label
  const typeClass = `ml-auto ${typeBadgeClass(status)}`
  return (
    <div
      className={`flex ${ROW_HEIGHT} min-w-0 items-center pl-2 text-xs ${hasSeparateType ? 'gap-2' : 'gap-1'} ${
        status === 'incompatible' ? 'opacity-40' : ''
      }`}
    >
      <span className={`min-w-0 truncate leading-tight ${hasSeparateType ? 'flex-1' : typeClass}`}>{port.label}</span>
      {hasSeparateType && <span className={`shrink-0 leading-tight ${typeClass}`}>{port.dataType}</span>}
      <PortHandle id={port.id} portType={port.type} side="right" status={status} />
    </div>
  )
}

/** Builds a React Flow node component driven entirely by a registry entry. */
export const createTransformNodeType = (registry: NodeRegistry) => {
  const TransformNode = ({ data, selected: isSelected }: NodeProps) => {
    const d = data as TransformNodeData
    const def = registry.byType[d.defType]
    const inputs = d.inputs ?? def?.inputs ?? []
    const outputs = d.outputs ?? def?.outputs ?? []
    const Icon = def?.icon

    return (
      <div
        className={`max-w-[200px] min-w-[150px] rounded-md border bg-background shadow-sm ${
          d.hasError ? 'border-destructive' : isSelected ? 'border-primary' : 'border-border'
        } ${isSelected ? 'ring-2 ring-primary/30' : ''}`}
      >
        <div className="flex items-center gap-1.5 border-b border-border px-2 py-1 text-xs font-medium text-foreground">
          {Icon && <Icon className="h-3.5 w-3.5 text-muted-foreground" />}
          <span className="truncate">{d.label ?? def?.label ?? d.defType}</span>
        </div>
        <div className="grid grid-cols-2 gap-x-3 py-1">
          {/* Both columns stretch to the taller one's height; centering places the shorter
              column's ports in the middle, e.g. concat's output between its two inputs. */}
          {inputs.length > 0 && (
            <div className="flex flex-col justify-center">
              {inputs.map(port => (
                <InputRow key={port.id} port={port} status={d.portStatus?.[port.id]} />
              ))}
            </div>
          )}
          {/* Without inputs (e.g. Literal) the outputs take the full node width. */}
          <div className={`flex min-w-0 flex-col justify-center ${inputs.length === 0 ? 'col-span-2' : ''}`}>
            {outputs.map(port => (
              <OutputRow key={port.id} port={port} status={d.portStatus?.[port.id]} />
            ))}
          </div>
        </div>
      </div>
    )
  }
  TransformNode.displayName = 'TransformNode'
  return TransformNode
}
