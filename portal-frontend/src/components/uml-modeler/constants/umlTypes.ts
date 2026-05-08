import type { UMLGeometryType, UMLPrimitiveType, UMLTypeReference } from '../types/uml'

// UML 2.5 Primitive Types with their official URIs
export const UML_PRIMITIVE_TYPES: Record<UMLPrimitiveType, { name: string; uri: string }> = {
  String: {
    name: 'String',
    uri: 'http://www.eclipse.org/uml2/5.0.0/Types#String',
  },
  Integer: {
    name: 'Integer',
    uri: 'http://www.eclipse.org/uml2/5.0.0/Types#Integer',
  },
  Boolean: {
    name: 'Boolean',
    uri: 'http://www.eclipse.org/uml2/5.0.0/Types#Boolean',
  },
  Float: {
    name: 'Float',
    uri: 'http://www.eclipse.org/uml2/5.0.0/Types#Real',
  },
  Double: {
    name: 'Double',
    uri: 'http://www.eclipse.org/uml2/5.0.0/Types#Real',
  },
  Long: {
    name: 'Long',
    uri: 'http://www.eclipse.org/uml2/5.0.0/Types#Integer',
  },
  Short: {
    name: 'Short',
    uri: 'http://www.eclipse.org/uml2/5.0.0/Types#Integer',
  },
  Byte: {
    name: 'Byte',
    uri: 'http://www.eclipse.org/uml2/5.0.0/Types#Integer',
  },
  Character: {
    name: 'Character',
    uri: 'http://www.eclipse.org/uml2/5.0.0/Types#String',
  },
  Date: {
    name: 'Date',
    uri: 'http://www.eclipse.org/uml2/5.0.0/Types#String',
  },
  void: {
    name: 'void',
    uri: 'http://www.eclipse.org/uml2/5.0.0/Types#void',
  },
}
export const UML_GEOMETRY_TYPES: Record<UMLGeometryType, { name: string; uri: string }> = {
  Point: {
    name: 'Point',
    uri: 'http://models.civitasconnect.org/models/postgis/1.0#//Point',
  },
  LineString: {
    name: 'LineString',
    uri: 'http://models.civitasconnect.org/models/postgis/1.0#//LineString',
  },
  Polygon: {
    name: 'Polygon',
    uri: 'http://models.civitasconnect.org/models/postgis/1.0#//Polygon',
  },
  MultiPoint: {
    name: 'MultiPoint',
    uri: 'http://models.civitasconnect.org/models/postgis/1.0#//Point',
  },
  MultiLineString: {
    name: 'MultiLineString',
    uri: 'http://models.civitasconnect.org/models/postgis/1.0#//MultiLineString',
  },
  MultiPolygon: {
    name: 'MultiPolygon',
    uri: 'http://models.civitasconnect.org/models/postgis/1.0#//MultiPolygon',
  },
  GeometryCollection: {
    name: 'GeometryCollection',
    uri: 'http://models.civitasconnect.org/models/postgis/1.0#//GeometryCollection',
  },
}

// Categorized primitive types for UI dropdowns
export const PRIMITIVE_TYPE_CATEGORIES = {
  Text: ['String', 'Character'] as UMLPrimitiveType[],
  Numbers: ['Integer', 'Long', 'Short', 'Byte', 'Float', 'Double'] as UMLPrimitiveType[],
  Other: ['Boolean', 'Date', 'void'] as UMLPrimitiveType[],
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
