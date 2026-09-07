import type { LucideIcon } from 'lucide-react'
import { Binary, Calendar, CalendarClock, Clock, Combine, Fingerprint, Hash, MapPin, Type } from 'lucide-react'

import type { ConfigField, PortDef, TransformDef } from '@/components/node-editor/types'
import { buildRegistry } from '@/components/node-editor/types'
import { UML_GEOMETRY_TYPES, UML_PRIMITIVE_TYPES } from '@/components/uml-modeler/constants/umlTypes'

import type { ConversionOp, OpNode, TransformOp, ValueNode } from '../_types'
import { NUMERIC_SUBTYPES } from '../_types'
import { GEOMETRY, PRIMITIVE } from '../schema/adapter'

/** A registry entry: the single source of truth for a node's ports, config and compiled op. */
export interface MappingTransformDef extends TransformDef {
  type: TransformOp
  op: TransformOp
  toValueNode: (inputs: ValueNode[], config: Record<string, unknown>) => ValueNode
  opInputs: (op: OpNode) => ValueNode[]
  opConfig: (op: OpNode) => Record<string, unknown>
  /** Per-field config errors as i18n keys, relative to `pipelineEditor.mappingEditor`. */
  validate?: (config: Record<string, unknown>) => Record<string, string> | undefined
}

const scalar = (id: string, label: string, dataType?: string): PortDef => ({ id, label, type: 'scalar', dataType })
const geometry = (id: string, label: string, dataType?: string): PortDef => ({ id, label, type: 'geometry', dataType })

/** Port label for a conversion input, derived from what the port actually accepts. */
const inputLabel = (inSubtype?: string, accepts?: readonly string[]): string =>
  accepts?.join(' / ') ?? inSubtype ?? 'any scalar'

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
 * @param inSubtype  The actual primitive subtype for type-matching (undefined = accepts any scalar)
 * @param outSubtype The actual primitive subtype produced
 * @param label  Display label for the node; defaults to the op name. Decoupled from `op` so the
 *   UI can show a different name (e.g. "toNumber") while the wire op stays the backend contract token.
 * @param accepts  When set, the input port accepts exactly these source subtypes (membership test)
 *   instead of the exact-match on `inSubtype` — e.g. numeric conversions accept str/int/number only.
 */
const conversion = (
  type: ConversionOp,
  inSubtype: string | undefined,
  outSubtype: string,
  icon: LucideIcon,
  config: ConfigField[] = [],
  label: string = type,
  accepts?: readonly string[],
): MappingTransformDef => ({
  type,
  category: 'categories.conversionFunctions',
  label,
  description: `transforms.${type}.description`,
  icon,
  inputs: [{ ...scalar('in', inputLabel(inSubtype, accepts), inSubtype), ...(accepts ? { accepts } : {}) }],
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
 * The port label shows that same subtype, matching the conversion nodes.
 */
export const literalOutputPort = (umlType: string): PortDef => {
  const isGeom = (GEOMETRY as Set<string>).has(umlType)
  const dataType = isGeom ? umlType : (PRIMITIVE[umlType] ?? 'str')
  return { id: 'out', label: dataType, type: isGeom ? 'geometry' : 'scalar', dataType }
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
  validate: cfg =>
    String(cfg.value ?? '').trim() === '' ? { value: 'transforms.literal.fields.value.required' } : undefined,
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
  inputs: [scalar('lon', 'longitude', 'number'), scalar('lat', 'latitude', 'number')],
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
  conversion('toString', undefined, 'str', Type),
  conversion('toInt', undefined, 'int', Binary, [], 'toInt', NUMERIC_SUBTYPES),
  // Wire op stays 'toFloat' (backend contract); only the display label is 'toNumber'.
  conversion('toFloat', undefined, 'number', Binary, [], 'toNumber', NUMERIC_SUBTYPES),
  // No RecordPath function backs toUuid — the value is passed through and the sink parses it.
  // A uuid source needs no transform; it matches a uuid target directly.
  conversion('toUuid', 'str', 'uuid', Fingerprint),
  conversion('toDate', undefined, 'date', Calendar, [patternField], 'toDate', ['str', 'date']),
  conversion(
    'toDateTime',
    undefined,
    'datetime',
    Clock,
    [
      {
        ...patternField,
        label: 'transforms.toDateTime.fields.pattern.label',
        default: "yyyy-MM-dd'T'HH:mm:ssXXX",
      },
    ],
    'toDateTime',
    ['str', 'datetime'],
  ),
  conversion(
    'format',
    undefined,
    'str',
    CalendarClock,
    [
      {
        ...patternField,
        label: 'transforms.format.fields.pattern.label',
      },
    ],
    'format',
    ['date', 'datetime'],
  ),
]

export const mappingRegistry = buildRegistry<MappingTransformDef>([literal, concat, geoPoint, ...conversions])

export const transformDef = (op: TransformOp) => mappingRegistry.byType[op]
