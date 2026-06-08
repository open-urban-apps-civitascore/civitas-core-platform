import type { LucideIcon } from 'lucide-react'
import { ArrowRightLeft, Binary, Calendar, CalendarClock, Combine, Hash, Network, Type } from 'lucide-react'

import type { ConfigField, PortDef, TransformDef } from '@/components/node-editor/types'
import { buildRegistry } from '@/components/node-editor/types'

import type { ConversionOp, OpNode, ValueNode } from '../_types'

/** A registry entry: the single source of truth for a node's ports, config and compiled op. */
export interface MappingTransformDef extends TransformDef {
  op: string
  toValueNode: (inputs: ValueNode[], config: Record<string, unknown>) => ValueNode
  opInputs: (op: OpNode) => ValueNode[]
  opConfig: (op: OpNode) => Record<string, unknown>
}

const scalar = (id: string, label: string, dataType?: string): PortDef => ({ id, label, type: 'scalar', dataType })

/** stringConcat is variadic; ports are grown on demand and persisted on the node. */
export const concatInputPorts = (count: number): PortDef[] =>
  Array.from({ length: Math.max(2, count) }, (_, i) => scalar(`in${i}`, `value ${i + 1}`))

const patternField: ConfigField = { key: 'pattern', label: 'Pattern', control: 'text', default: 'yyyy-MM-dd' }

const conversion = (
  type: ConversionOp,
  inType: string,
  outType: string,
  icon: LucideIcon,
  config: ConfigField[] = [],
): MappingTransformDef => ({
  type,
  category: 'Conversion Functions',
  label: type,
  description: `${inType} → ${outType}`,
  icon,
  inputs: [scalar('in', inType, inType)],
  outputs: [scalar('out', outType, outType)],
  config,
  op: type,
  toValueNode: (inputs, cfg) => {
    const node = { op: type, input: inputs[0] ?? '' } as Extract<OpNode, { op: ConversionOp }>
    if (cfg.pattern) node.pattern = String(cfg.pattern)
    return node
  },
  opInputs: op => ('input' in op ? [op.input] : []),
  opConfig: op => ('pattern' in op && op.pattern != null ? { pattern: op.pattern } : {}),
})

const copy: MappingTransformDef = {
  type: 'copy',
  category: 'RecordPath',
  label: 'RecordPath',
  description: 'Pass a field through by its path.',
  icon: ArrowRightLeft,
  inputs: [scalar('in', 'in')],
  outputs: [scalar('out', 'out')],
  config: [],
  op: 'copy',
  toValueNode: inputs => inputs[0] ?? '',
  opInputs: op => (op.op === 'copy' ? [op.sourcePath] : []),
  opConfig: () => ({}),
}

const recordPathMapping: MappingTransformDef = {
  type: 'recordPathMapping',
  category: 'RecordPath Mapping',
  label: 'RecordPath Mapping',
  description: 'Restructure a field tree.',
  icon: Network,
  inputs: [{ id: 'in', label: 'in', type: 'object' }],
  outputs: [{ id: 'out', label: 'out', type: 'object' }],
  config: [],
  op: 'copy',
  toValueNode: inputs => inputs[0] ?? '',
  opInputs: () => [],
  opConfig: () => ({}),
}

const literal: MappingTransformDef = {
  type: 'const',
  category: 'Literal',
  label: 'Literal',
  description: 'Emit a fixed value.',
  icon: Hash,
  inputs: [],
  outputs: [scalar('out', 'value')],
  config: [{ key: 'value', label: 'Value', control: 'text', default: '' }],
  op: 'const',
  toValueNode: (_inputs, cfg) => ({ op: 'const', value: cfg.value ?? '' }),
  opInputs: () => [],
  opConfig: op => (op.op === 'const' ? { value: op.value } : {}),
}

const concat: MappingTransformDef = {
  type: 'concat',
  category: 'RecordPath Functions',
  label: 'stringConcat',
  description: 'Concatenate two or more values.',
  icon: Combine,
  inputs: concatInputPorts(2),
  outputs: [scalar('out', 'out', 'str')],
  config: [{ key: 'separator', label: 'Separator', control: 'text', default: '' }],
  op: 'concat',
  toValueNode: (inputs, cfg) => ({
    op: 'concat',
    ...(cfg.separator ? { separator: String(cfg.separator) } : {}),
    inputs,
  }),
  opInputs: op => (op.op === 'concat' ? op.inputs : []),
  opConfig: op => (op.op === 'concat' ? { separator: op.separator ?? '' } : {}),
}

const conversions: MappingTransformDef[] = [
  conversion('intToStr', 'int', 'str', Type),
  conversion('strToInt', 'str', 'int', Binary),
  conversion('parseInt', 'str', 'int', Binary),
  conversion('parseFloat', 'str', 'float', Binary),
  conversion('strToDate', 'str', 'date', Calendar, [patternField]),
  conversion('dateToStr', 'date', 'str', CalendarClock, [patternField]),
]

export const mappingRegistry = buildRegistry<MappingTransformDef>([
  copy,
  recordPathMapping,
  literal,
  concat,
  ...conversions,
])
