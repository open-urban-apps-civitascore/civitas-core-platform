import { MISMATCH_COLOR } from '@/components/node-editor'
import type { PortType } from '@/components/node-editor/types'

/** Visual style applied to edges that carry array (one-to-many) values. */
export const ARRAY_EDGE_STYLE = { strokeWidth: 3, stroke: 'hsl(var(--primary))' }

/** Overlaid on edges whose endpoints no longer type-match; same red as a mismatched port. */
export const INVALID_EDGE_STYLE = { stroke: MISMATCH_COLOR }

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

export type FieldType = 'str' | 'int' | 'float' | 'bool' | 'date' | GeometryType | 'array' | 'object'

export const GEOMETRY: ReadonlySet<GeometryType> = new Set<GeometryType>([
  'Point',
  'LineString',
  'Polygon',
  'MultiPoint',
  'MultiLineString',
  'MultiPolygon',
  'GeometryCollection',
])

/** Type guard: is the given type name one of the concrete geometry types? */
export const isGeometryType = (value: string): value is GeometryType => (GEOMETRY as ReadonlySet<string>).has(value)

export interface FieldNode {
  /** JSONPath, e.g. "$.klassen[].name" */
  path: string
  name: string
  type: FieldType
  portType: PortType
  /** Whether the field is required (UML {id} or multiplicity lower bound >= 1); absent = optional. */
  required?: boolean
  /** Whether the field carries the x-core-primaryKey marker (UML {id}); absent = no. */
  primaryKey?: boolean
  children?: FieldNode[]
}

/** The port category is a pure function of the field type. */
export const portTypeFor = (type: FieldType): PortType => {
  if (type === 'array') return 'array'
  if (type === 'object') return 'object'
  if (isGeometryType(type)) return 'geometry'
  return 'scalar'
}

/**
 * The single {@link FieldNode} factory for every tree producer: derives `portType` from `type` and
 * keeps `required`/`children` absent rather than false/empty, so all producers emit the same
 * canonical node shape.
 */
export const field = (
  path: string,
  name: string,
  type: FieldType,
  required: boolean,
  children?: FieldNode[],
): FieldNode => ({
  path,
  name,
  type,
  portType: portTypeFor(type),
  ...(required ? { required } : {}),
  ...(children && children.length > 0 ? { children } : {}),
})

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

/** Ops with their own canvas node; 'copy' is the direct edge. */
export type TransformOp = Exclude<OpNode['op'], 'copy'>

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
