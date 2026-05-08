export type Visibility = 'public' | 'private' | 'protected' | 'package'

export const VISIBILITY_SYMBOLS: Record<Visibility, string> = {
  public: '+',
  private: '-',
  protected: '#',
  package: '~',
}

export type UMLPrimitiveType =
  | 'String'
  | 'Integer'
  | 'Boolean'
  | 'Float'
  | 'Double'
  | 'Long'
  | 'Short'
  | 'Byte'
  | 'Character'
  | 'Date'
  | 'void'

export type UMLGeometryType =
  | 'Point'
  | 'LineString'
  | 'Polygon'
  | 'MultiPoint'
  | 'MultiLineString'
  | 'MultiPolygon'
  | 'GeometryCollection'

export interface UMLTypeReference {
  id: string
  name: string
  isExternal?: boolean
  href?: string // For XMI external references
}

export type UMLType = UMLPrimitiveType | UMLGeometryType | UMLTypeReference

export interface UMLParameter {
  id: string
  name: string
  type: UMLType
  direction?: 'in' | 'out' | 'inout' | 'return'
  multiplicity?: string // e.g., "0..1", "1..*", "*"
}

export type AttributeMeta = {
  gisInfo?: {
    crs: string
  }
}
export interface UMLAttribute {
  id: string
  name: string
  type: UMLType
  visibility: Visibility
  isStatic?: boolean
  isId?: boolean
  isReadonly?: boolean
  multiplicity?: string
  defaultValue?: string
  meta?: AttributeMeta
}

export interface UMLOperation {
  id: string
  name: string
  returnType?: UMLType
  visibility: Visibility
  isStatic?: boolean
  isAbstract?: boolean
  parameters: UMLParameter[]
}

export interface UMLEnumLiteral {
  id: string
  name: string
  value?: string | number
}

export type UMLElementType = 'class' | 'interface' | 'abstractClass' | 'enumeration'

export interface BaseUMLElement {
  id: string
  name: string
  type: UMLElementType
  stereotype?: string
  documentation?: string
  package?: string
}

export interface UMLClass extends BaseUMLElement {
  type: 'class'
  attributes: UMLAttribute[]
  operations: UMLOperation[]
  isAbstract?: boolean
}

export interface UMLInterface extends BaseUMLElement {
  type: 'interface'
  operations: UMLOperation[]
}

export interface UMLAbstractClass extends BaseUMLElement {
  type: 'abstractClass'
  attributes: UMLAttribute[]
  operations: UMLOperation[]
}

export interface UMLEnumeration extends BaseUMLElement {
  type: 'enumeration'
  literals: UMLEnumLiteral[]
}

export type UMLElement = UMLClass | UMLInterface | UMLAbstractClass | UMLEnumeration

// Relationship types
export type UMLRelationshipType =
  | 'association'
  | 'aggregation'
  | 'composition'
  | 'inheritance'
  | 'realization'
  | 'dependency'

export interface UMLRelationship {
  id: string
  type: UMLRelationshipType
  source: string // UMLElement id
  target: string // UMLElement id
  sourceMultiplicity?: string
  targetMultiplicity?: string
  sourceRole?: string
  targetRole?: string
  name?: string
  isNavigable?: boolean
  isBidirectional?: boolean
}

// Helper functions
export const isClassifierElement = (element: UMLElement): element is UMLClass | UMLInterface | UMLAbstractClass => {
  return ['class', 'interface', 'abstractClass'].includes(element.type)
}

export const hasAttributes = (element: UMLElement): element is UMLClass | UMLAbstractClass => {
  return element.type === 'class' || element.type === 'abstractClass'
}

export const hasOperations = (element: UMLElement): element is UMLClass | UMLInterface | UMLAbstractClass => {
  return ['class', 'interface', 'abstractClass'].includes(element.type)
}

export const isAbstractElement = (element: UMLElement): boolean => {
  return (
    element.type === 'abstractClass' ||
    element.type === 'interface' ||
    (element.type === 'class' && (element as UMLClass).isAbstract === true)
  )
}
