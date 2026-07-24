import type { LucideIcon } from 'lucide-react'
import { Binary, Calendar, CalendarClock, Combine, Hash, MapPin, Type } from 'lucide-react'

import type { ConfigField, PortDef, TransformDef } from '@/components/node-editor/types'
import { buildRegistry } from '@/components/node-editor/types'
import { UML_GEOMETRY_TYPES, UML_PRIMITIVE_TYPES } from '@/components/uml-modeler/constants/umlTypes'

import type { ConversionOp, OpNode, ValueNode } from '../_types'
import { GEOMETRY, PRIMITIVE } from '../schema/adapter'

/** A registry entry: the single source of truth for a node's ports, config and compiled op. */
export interface MappingTransformDef extends TransformDef {
  op: string
  toValueNode: (inputs: ValueNode[], config: Record<string, unknown>) => ValueNode
  opInputs: (op: OpNode) => ValueNode[]
  opConfig: (op: OpNode) => Record<string, unknown>
}

const scalar = (id: string, label: string, dataType?: string): PortDef => ({ id, label, type: 'scalar', dataType })
const geometry = (id: string, label: string, dataType?: string): PortDef => ({ id, label, type: 'geometry', dataType })

/** stringConcat is variadic; ports are grown on demand and persisted on the node. */
export const concatInputPorts = (count: number): PortDef[] =>
  Array.from({ length: Math.max(2, count) }, (_, i) => scalar(`in${i}`, `value ${i + 1}`))

const patternField: ConfigField = {
  key: 'pattern',
  label: 'transforms.toDate.fields.pattern.label',
  control: 'text',
  default: 'yyyy-MM-dd',
}

/**
 * Build a conversion node definition.
 * @param inLabel  Human-readable label for the input port (e.g. "str/float")
 * @param inSubtype  The actual primitive subtype for type-matching (undefined = accepts any scalar)
 * @param outSubtype The actual primitive subtype produced
 */
const conversion = (
  type: ConversionOp,
  inLabel: string,
  inSubtype: string | undefined,
  outSubtype: string,
  icon: LucideIcon,
  config: ConfigField[] = [],
): MappingTransformDef => ({
  type,
  category: 'categories.conversionFunctions',
  label: type,
  description: `transforms.${type}.description`,
  icon,
  inputs: [scalar('in', inLabel, inSubtype)],
  outputs: [scalar('out', outSubtype, outSubtype)],
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

/**
 * Dropdown options for the Literal node — derived directly from the UML modeler's
 * type constants so the two stay in sync automatically.
 * Each option uses the UML type name as its unique value.
 */
export const LITERAL_TYPE_OPTIONS = [...UML_PRIMITIVE_TYPES, ...UML_GEOMETRY_TYPES].map(name => ({
  label: name,
  value: name,
}))

/** Default UML type name for a freshly-dropped Literal node. */
export const LITERAL_DEFAULT_TYPE = 'String'

/**
 * Given a UML type name, returns the output PortDef for a literal node.
 * Reuses adapter.ts's GEOMETRY set and PRIMITIVE map — single source of truth.
 * Geometries are first-class typed ports: their `dataType` is the concrete
 * geometry name (e.g. 'Point') so Point vs Polygon is matched by exact type;
 * other primitives → scalar port with the matching subtype.
 */
export const literalOutputPort = (umlType: string): PortDef => {
  const isGeom = (GEOMETRY as Set<string>).has(umlType)
  const dataType = isGeom ? umlType : (PRIMITIVE[umlType] ?? 'str')
  return { id: 'out', label: 'value', type: isGeom ? 'geometry' : 'scalar', dataType }
}

const literal: MappingTransformDef = {
  type: 'const',
  category: 'categories.literal',

  label: 'Literal',
  description: 'transforms.literal.description',
  icon: Hash,
  inputs: [],
  // Default output port is String (scalar/str); updated dynamically when type is changed.
  outputs: [literalOutputPort(LITERAL_DEFAULT_TYPE)],
  config: [
    {
      key: 'type',
      label: 'transforms.literal.fields.type.label',
      control: 'select',
      default: LITERAL_DEFAULT_TYPE,
      placeholder: 'transforms.literal.fields.type.placeholder',
      options: LITERAL_TYPE_OPTIONS,
    },
    { key: 'value', label: 'transforms.literal.fields.value.label', control: 'text', default: '' },
  ],
  op: 'const',
  toValueNode: (_inputs, cfg) => ({
    op: 'const',
    value: cfg.value ?? '',
    ...(cfg.type ? { valueType: String(cfg.type) } : {}),
  }),
  opInputs: () => [],
  opConfig: op => (op.op === 'const' ? { value: op.value, type: op.valueType ?? LITERAL_DEFAULT_TYPE } : {}),
}

const concat: MappingTransformDef = {
  type: 'concat',
  category: 'categories.recordPathFunctions',
  label: 'stringConcat',
  description: 'transforms.concat.description',
  icon: Combine,
  inputs: concatInputPorts(2),
  outputs: [scalar('out', 'out', 'str')],
  config: [
    {
      key: 'separator',
      label: 'transforms.concat.fields.separator.label',
      control: 'text',
      default: '',
    },
  ],
  op: 'concat',
  toValueNode: (inputs, cfg) => ({
    op: 'concat',
    ...(cfg.separator ? { separator: String(cfg.separator) } : {}),
    // concat is variadic: an unconnected port is simply absent, so drop empty slots.
    inputs: inputs.filter(v => v !== ''),
  }),
  opInputs: op => (op.op === 'concat' ? op.inputs : []),
  opConfig: op => (op.op === 'concat' ? { separator: op.separator ?? '' } : {}),
}

const geoPoint: MappingTransformDef = {
  type: 'geoPoint',
  category: 'categories.conversionFunctions',
  label: 'geoPoint',
  description: 'transforms.geoPoint.description',
  icon: MapPin,
  inputs: [scalar('lon', 'longitude', 'float'), scalar('lat', 'latitude', 'float')],
  outputs: [geometry('out', 'Point', 'Point')],
  config: [],
  op: 'geoPoint',
  toValueNode: inputs => ({
    op: 'geoPoint' as const,
    lon: inputs[0] ?? '',
    lat: inputs[1] ?? '',
  }),
  opInputs: op => (op.op === 'geoPoint' ? [op.lon, op.lat] : []),
  opConfig: () => ({}),
}

/**
 * Conversion nodes
 */
const conversions: MappingTransformDef[] = [
  // toString: accepts any scalar (no subtype restriction on input), produces str
  conversion('toString', 'any scalar', undefined, 'str', Type),
  // toInt: accepts str or float (no subtype restriction — conversion node wires freely), produces int
  conversion('toInt', 'str / float', undefined, 'int', Binary),
  // toFloat: accepts str or int, produces float
  conversion('toFloat', 'str / int', undefined, 'float', Binary),
  // toDate: accepts str, produces date
  conversion('toDate', 'str', 'str', 'date', Calendar, [patternField]),
  // format: accepts date, produces str — reuse patternField but with the format-specific translation key
  conversion('format', 'date', 'date', 'str', CalendarClock, [
    {
      ...patternField,
      label: 'transforms.format.fields.pattern.label',
    },
  ]),
]

export const mappingRegistry = buildRegistry<MappingTransformDef>([literal, concat, geoPoint, ...conversions])
