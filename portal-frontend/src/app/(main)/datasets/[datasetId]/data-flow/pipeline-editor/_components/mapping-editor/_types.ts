import type { PortType } from '@/components/node-editor/types'

/** Visual style applied to edges that carry array (one-to-many) values. */
export const ARRAY_EDGE_STYLE = { strokeWidth: 3, stroke: 'hsl(var(--primary))' }

// ---------------------------------------------------------------------------
// Field tree (produced by the schema adapter, consumed by the MegaNodes)
// ---------------------------------------------------------------------------

/**
 * Concrete geometry types are first-class field types (alongside the scalar
 * primitives) so Point vs Polygon mismatches are caught by the same exact-subtype
 * matching used for int↔int / str↔str.
 */
export type GeometryType =
  | 'Point'
  | 'LineString'
  | 'Polygon'
  | 'MultiPoint'
  | 'MultiLineString'
  | 'MultiPolygon'
  | 'GeometryCollection'

export type FieldType = 'str' | 'int' | 'float' | 'bool' | 'date' | GeometryType | 'array' | 'object'

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
   * Versioned CORE URN of the source DataStructure.
   * Format: urn:core:datastructure:<datastructureId>:<versionId>
   * (exact segment layout to be confirmed by schema owner)
   */
  source?: string
  /**
   * Versioned CORE URN of the target DataStructure.
   * Format: urn:core:datastructure:<datastructureId>:<versionId>
   * (exact segment layout to be confirmed by schema owner)
   */
  target?: string
  fields: Record<string, ValueNode>
  positions: Record<string, { x: number; y: number }>
}

export const emptyMappingConfig = (): MappingConfig => ({ fields: {}, positions: {} })

export const isOpNode = (v: ValueNode): v is OpNode => typeof v === 'object' && v !== null && 'op' in v
