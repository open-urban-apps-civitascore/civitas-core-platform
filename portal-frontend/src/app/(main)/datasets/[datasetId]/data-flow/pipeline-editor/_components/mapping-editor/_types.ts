import type { PortType } from '@/components/node-editor/types'

/** Visual style applied to edges that carry array (one-to-many) values. */
export const ARRAY_EDGE_STYLE = { strokeWidth: 3, stroke: 'hsl(var(--primary))' }

// ---------------------------------------------------------------------------
// Field tree (produced by the schema adapter, consumed by the MegaNodes)
// ---------------------------------------------------------------------------

/**
 * Concrete geometry types are first-class field types, but they are separate from
 * scalar primitives so Point vs Polygon mismatches can be handled explicitly.
 */
export type GeometryType =
  | 'Point'
  | 'LineString'
  | 'Polygon'
  | 'MultiPoint'
  | 'MultiLineString'
  | 'MultiPolygon'
  | 'GeometryCollection'

/**
 * `any` is a scalar wildcard (SensorThings' `result` accepts any scalar type): it matches every
 * scalar subtype in the port-compatibility checks. Only static target schemas use it — UML-derived
 * trees always carry a concrete type.
 */
export type FieldType = 'str' | 'int' | 'float' | 'bool' | 'date' | 'any' | GeometryType | 'array' | 'object'

export interface FieldNode {
  /** JSONPath, e.g. "$.klassen[].name" */
  path: string
  name: string
  type: FieldType
  portType: PortType
  /** Whether the field is required (UML {id} or multiplicity lower bound >= 1); absent = optional. */
  required?: boolean
  children?: FieldNode[]
}

export interface SchemaTree {
  name: string
  fields: FieldNode[]
}

// ---------------------------------------------------------------------------
// Mapping config (spec §13) — the single saved artifact
// ---------------------------------------------------------------------------

/**
 * Conversion ops aligned with Apache NiFi RecordPath functions:
 *  toString  → NiFi toString(field, charset)   — any scalar → string
 *  toInt     → NiFi type coercion to INT        — str/float → int
 *  toFloat   → NiFi type coercion to FLOAT      — str/int → float
 *  toDate    → NiFi toDate(field, format)       — str → date
 *  format    → NiFi format(field, format)       — date → str
 */
export type ConversionOp = 'toString' | 'toInt' | 'toFloat' | 'toDate' | 'format'

export type OpNode =
  | { op: 'copy'; sourcePath: string }
  | { op: 'const'; value: unknown; valueType?: string }
  | { op: 'concat'; separator?: string; inputs: ValueNode[] }
  | { op: ConversionOp; input: ValueNode; pattern?: string }
  | { op: 'geoPoint'; lon: ValueNode; lat: ValueNode }

/** A path string is shorthand for a copy. */
export type ValueNode = string | OpNode

export interface MappingConfig {
  /** JSON Schema URI — always "https://civitasconnect.digital/core/mapping/v1" */
  $schema?: string
  /**
   * Versioned CORE URN of the source DataStructure, built via
   * `buildDataStructureUrn` from `@/utils/urn`.
   */
  source?: string
  /**
   * Versioned CORE URN of the target DataStructure, built via
   * `buildDataStructureUrn` from `@/utils/urn`.
   */
  target?: string
  fields: Record<string, ValueNode>
  positions: Record<string, { x: number; y: number }>
}

export const emptyMappingConfig = (): MappingConfig => ({ fields: {}, positions: {} })

export const isOpNode = (v: ValueNode): v is OpNode => typeof v === 'object' && v !== null && 'op' in v
