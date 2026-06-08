'use client'

import type { NodeProps } from '@xyflow/react'

import { PortHandle } from './PortHandle'
import type { NodeRegistry, PortDef, TransformNodeData } from './types'

const InputRow = ({ port }: { port: PortDef }) => (
  <div className="flex items-center gap-1 py-0.5 pr-2 text-xs">
    <PortHandle id={port.id} portType={port.type} side="left" />
    <span className="truncate text-muted-foreground">{port.label}</span>
  </div>
)

const OutputRow = ({ port }: { port: PortDef }) => (
  <div className="flex items-center justify-end gap-1 py-0.5 pl-2 text-xs">
    <span className="truncate text-muted-foreground">{port.label}</span>
    <PortHandle id={port.id} portType={port.type} side="right" />
  </div>
)

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
        className={`min-w-[150px] rounded-md border bg-background shadow-sm ${
          isSelected ? 'border-primary ring-2 ring-primary/30' : 'border-border'
        }`}
      >
        <div className="flex items-center gap-1.5 border-b border-border px-2 py-1 text-xs font-medium text-foreground">
          {Icon && <Icon className="h-3.5 w-3.5 text-muted-foreground" />}
          <span className="truncate">{d.label ?? def?.label ?? d.defType}</span>
        </div>
        <div className="grid grid-cols-2 gap-x-3 py-1">
          <div>
            {inputs.map(port => (
              <InputRow key={port.id} port={port} />
            ))}
          </div>
          <div>
            {outputs.map(port => (
              <OutputRow key={port.id} port={port} />
            ))}
          </div>
        </div>
      </div>
    )
  }
  TransformNode.displayName = 'TransformNode'
  return TransformNode
}
