import type { PortType } from '@/components/node-editor/types'

// ---------------------------------------------------------------------------
// Field tree (produced by the schema adapter, consumed by the MegaNodes)
// ---------------------------------------------------------------------------

export type FieldType = 'str' | 'int' | 'float' | 'bool' | 'date' | 'geo' | 'array' | 'object'

export interface FieldNode {
  /** JSONPath, e.g. "$.klassen[].name" */
  path: string
  name: string
  type: FieldType
  portType: PortType
  children?: FieldNode[]
}

export interface SchemaTree {
  name: string
  fields: FieldNode[]
}

// ---------------------------------------------------------------------------
// Mapping config (spec §13) — the single saved artifact
// ---------------------------------------------------------------------------

export type ConversionOp = 'intToStr' | 'strToInt' | 'parseInt' | 'parseFloat' | 'strToDate' | 'dateToStr'

export type OpNode =
  | { op: 'copy'; sourcePath: string }
  | { op: 'const'; value: unknown }
  | { op: 'concat'; separator?: string; inputs: ValueNode[] }
  | { op: ConversionOp; input: ValueNode; pattern?: string }

/** A path string is shorthand for a copy. */
export type ValueNode = string | OpNode

export interface MappingConfig {
  sourceDatastructureId?: string
  sourceVersionId?: string
  targetDatastructureId?: string
  targetVersionId?: string
  fields: Record<string, ValueNode>
  positions: Record<string, { x: number; y: number }>
}

export const emptyMappingConfig = (): MappingConfig => ({ fields: {}, positions: {} })

export const isOpNode = (v: ValueNode): v is OpNode => typeof v === 'object' && v !== null && 'op' in v
