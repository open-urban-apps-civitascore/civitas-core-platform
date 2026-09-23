import type { UMLGeometryType, UMLPrimitiveType, UMLTypeReference } from '../types/uml'

// Selectable UML primitive and geometry types. The `UMLPrimitiveType` /
// `UMLGeometryType` unions are the source of truth; these lists drive the
// type dropdowns and are checked against those unions at compile time.
export const UML_PRIMITIVE_TYPES: readonly UMLPrimitiveType[] = [
  'String',
  'Integer',
  'Boolean',
  'Number',
  'Date',
  'DateTime',
  'Uuid',
  'Json',
]

export const UML_GEOMETRY_TYPES: readonly UMLGeometryType[] = [
  'Point',
  'LineString',
  'Polygon',
  'MultiPoint',
  'MultiLineString',
  'MultiPolygon',
  'GeometryCollection',
]

// Categorized primitive types for UI dropdowns
export const PRIMITIVE_TYPE_CATEGORIES = {
  Text: ['String', 'Uuid'] as UMLPrimitiveType[],
  Numbers: ['Integer', 'Number'] as UMLPrimitiveType[],
  Other: ['Boolean', 'Date', 'DateTime', 'Json'] as UMLPrimitiveType[],
}

// Common external type references (Java, C#, etc.)
export const COMMON_EXTERNAL_TYPES: UMLTypeReference[] = [
  // Java types
  { id: 'java.lang.String', name: 'String', isExternal: true },
  { id: 'java.lang.Integer', name: 'Integer', isExternal: true },
  { id: 'java.lang.Boolean', name: 'Boolean', isExternal: true },
  { id: 'java.lang.Double', name: 'Double', isExternal: true },
  { id: 'java.lang.Float', name: 'Float', isExternal: true },
  { id: 'java.lang.Long', name: 'Long', isExternal: true },
  { id: 'java.util.Date', name: 'Date', isExternal: true },
  { id: 'java.util.List', name: 'List', isExternal: true },
  { id: 'java.util.Map', name: 'Map', isExternal: true },
  { id: 'java.util.Set', name: 'Set', isExternal: true },

  // C# types
  { id: 'System.String', name: 'string', isExternal: true },
  { id: 'System.Int32', name: 'int', isExternal: true },
  { id: 'System.Boolean', name: 'bool', isExternal: true },
  { id: 'System.Double', name: 'double', isExternal: true },
  { id: 'System.Single', name: 'float', isExternal: true },
  { id: 'System.DateTime', name: 'DateTime', isExternal: true },
  { id: 'System.Collections.Generic.List', name: 'List<T>', isExternal: true },
  { id: 'System.Collections.Generic.Dictionary', name: 'Dictionary<K,V>', isExternal: true },
]

// Default multiplicity values
export const MULTIPLICITY_VALUES = [
  '', // No multiplicity
  '1', // Exactly one
  '0..1', // Zero or one
  '1..*', // One or more
  '0..*', // Zero or more
  '*', // Many (shorthand for 0..*)
]

// Cardinalities offered by the per-property dropdown (issue #1707). These drive
// the JSON Schema mapping: `..1` values stay scalar, `..*` values become arrays;
// a lower bound of 1 marks the property as `required`. Deliberately omits the
// `*` shorthand that MULTIPLICITY_VALUES carries for relationship edges, since
// it would be redundant with `0..*` here.
export const PROPERTY_CARDINALITY_VALUES = ['0..1', '1', '0..*', '1..*'] as const

export type PropertyCardinality = (typeof PROPERTY_CARDINALITY_VALUES)[number]

/**
 * Maps a stored `multiplicity` value to the cardinality the dropdown should
 * display. An unset multiplicity is treated as `1` (exactly one) everywhere
 * downstream (see {@link parseMultiplicity}), so it surfaces as `1`.
 */
export const cardinalityForDisplay = (multiplicity?: string): string => multiplicity || '1'

/**
 * Maps a dropdown selection back to the value to persist on the attribute.
 * Selecting `1` clears the field (writes `undefined`) because unset already
 * means exactly-one downstream; persisting a literal `"1"` would needlessly
 * mutate models that previously had no multiplicity set.
 */
export const cardinalityForStorage = (value: string): string | undefined => (value === '1' ? undefined : value)

// UML Stereotypes
export const UML_STEREOTYPES = {
  class: ['<<entity>>', '<<boundary>>', '<<control>>', '<<utility>>', '<<service>>'],
  interface: ['<<interface>>', '<<service>>', '<<repository>>', '<<component>>'],
  abstractClass: ['<<abstract>>', '<<template>>', '<<mixin>>'],
  enumeration: ['<<enumeration>>', '<<codelist>>'],
}

// Default naming patterns
export const DEFAULT_NAMES = {
  class: 'NeueKlasse',
  interface: 'NeuesInterface',
  abstractClass: 'NeueAbstrakteKlasse',
  enumeration: 'NeueAufzählung',
  attribute: 'neuesAttribut',
  operation: 'neueOperation',
  parameter: 'parameter',
  enumLiteral: 'WERT',
}

// Node dimensions and styling constants
export const NODE_DIMENSIONS = {
  minWidth: 150,
  minHeight: 80,
  headerHeight: 30,
  sectionPadding: 8,
  lineHeight: 20,
  separatorHeight: 1,
}

// Color scheme for UML elements
export const UML_COLORS = {
  class: {
    background: '#fff2cc',
    border: '#d6b656',
    text: '#333333',
  },
  interface: {
    background: '#e1d5e7',
    border: '#9673a6',
    text: '#333333',
  },
  abstractClass: {
    background: '#dae8fc',
    border: '#6c8ebf',
    text: '#333333',
  },
  enumeration: {
    background: '#d5e8d4',
    border: '#82b366',
    text: '#333333',
  },
  selected: {
    border: '#0066cc',
    shadow: '0 0 0 2px rgba(0, 102, 204, 0.3)',
  },
}

// Edge/Relationship styling
export const RELATIONSHIP_STYLES = {
  association: {
    stroke: '#333333',
    strokeWidth: 1,
    markerEnd: 'none',
  },
  aggregation: {
    stroke: '#333333',
    strokeWidth: 1,
    markerEnd: 'aggregation',
  },
  composition: {
    stroke: '#333333',
    strokeWidth: 1,
    markerEnd: 'composition',
  },
  inheritance: {
    stroke: '#333333',
    strokeWidth: 1,
    markerEnd: 'inheritance',
  },
  realization: {
    stroke: '#333333',
    strokeWidth: 1,
    strokeDasharray: '5,5',
    markerEnd: 'realization',
  },
  dependency: {
    stroke: '#666666',
    strokeWidth: 1,
    strokeDasharray: '3,3',
    markerEnd: 'dependency',
  },
}
