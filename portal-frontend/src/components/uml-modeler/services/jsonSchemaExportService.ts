/**
 * JSON Schema Export Service
 *
 * Converts UML diagram data to a JSON Schema (draft 2020-12) document.
 *
 * Conventional UML -> JSON Schema mapping:
 * - Each UML class/interface/abstractClass becomes an object schema.
 * - Each attribute becomes a `properties` entry.
 * - Primitive UML types map to JSON Schema `type`/`format`.
 * - Geometry types map to a GeoJSON-style `$ref`.
 * - Multiplicity `*` / `n..*` becomes an `array` (with `minItems` when the
 *   lower bound is >= 1); required attributes (lower bound >= 1 or `isID`)
 *   are added to the class's `required` array.
 * - Enumerations map to the `enum` keyword.
 * - Inheritance maps to `allOf: [{ $ref }, { ...own }]`.
 * - Associations/aggregation/composition map to a `$ref` property (an array
 *   `$ref` for many multiplicities).
 *
 * Root selection (composition/aggregation hierarchy): the class that is not
 * contained by any other class via composition (filled diamond) or aggregation
 * is the root of the document; every other class is emitted under `$defs` and
 * linked via `$ref`. When ambiguous, the class matching the diagram name is
 * preferred, falling back to the first class node.
 *
 * Multiple disconnected roots: when the diagram contains more than one
 * unconnected component (i.e. multiple root classes), the export uses a
 * type-library layout — all classes are placed in `$defs` and the document
 * root carries only `$id`, `$schema`, and `title`. This avoids arbitrarily
 * promoting one class to the document root while silently orphaning the others.
 */

import type { UMLDiagram } from '../types/diagram'
import type { UMLAttribute, UMLClass, UMLElement, UMLEnumeration, UMLRelationship, UMLType } from '../types/uml'

const JSON_SCHEMA_DIALECT = 'https://json-schema.org/draft/2020-12/schema'
const BASE_MODEL_URI = 'http://civitas.org/model'

type JsonSchemaObject = Record<string, unknown>

/**
 * Maps UML primitive type names to JSON Schema type/format fragments.
 */
const PRIMITIVE_TYPE_MAP: Record<string, JsonSchemaObject> = {
  String: { type: 'string' },
  Character: { type: 'string' },
  Uuid: { type: 'string', format: 'uuid' },
  Integer: { type: 'integer' },
  Long: { type: 'integer' },
  Short: { type: 'integer' },
  Byte: { type: 'integer' },
  Float: { type: 'number' },
  Double: { type: 'number' },
  Boolean: { type: 'boolean' },
  Date: { type: 'string', format: 'date-time' },
  void: { type: 'null' },
}

const GEOMETRY_TYPES = new Set([
  'Point',
  'LineString',
  'Polygon',
  'MultiPoint',
  'MultiLineString',
  'MultiPolygon',
  'GeometryCollection',
])

/**
 * GeoJSON reference base for geometry types.
 */
const GEOJSON_REF_BASE = 'https://geojson.org/schema'

/**
 * Sanitizes a name for use in URIs and `$defs` keys.
 */
export const sanitizeName = (name: string): string => {
  return name
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
}

/**
 * Parses a multiplicity string into lower/upper numeric bounds.
 * `upper === Infinity` represents an unbounded (`*`) upper bound.
 */
const parseMultiplicity = (multiplicity?: string): { lower: number; upper: number } => {
  if (!multiplicity) return { lower: 1, upper: 1 }

  const trimmed = multiplicity.trim()
  if (trimmed === '*') return { lower: 0, upper: Infinity }

  const rangeMatch = trimmed.match(/^(\d+|\*)\.\.(\d+|\*)$/)
  if (rangeMatch) {
    const lower = rangeMatch[1] === '*' ? 0 : Number(rangeMatch[1])
    const upper = rangeMatch[2] === '*' ? Infinity : Number(rangeMatch[2])
    return { lower, upper }
  }

  const single = Number(trimmed)
  if (!Number.isNaN(single)) return { lower: single, upper: single }

  return { lower: 1, upper: 1 }
}

const isMany = (upper: number): boolean => upper === Infinity || upper > 1

/**
 * Converts a single UML type into a JSON Schema fragment.
 * Class references are resolved via `$ref` into `$defs`.
 * For geometry types, an optional `crs` string is emitted as a sibling
 * property next to `$ref` (custom annotation, ignored by JSON Schema validators).
 */
const typeToSchema = (type: UMLType, classDefKeyById: Map<string, string>, crs?: string): JsonSchemaObject => {
  // Class reference (UMLTypeReference)
  if (typeof type !== 'string') {
    const defKey = classDefKeyById.get(type.id)
    if (defKey) return { $ref: `#/$defs/${defKey}` }
    // External reference without a known class def: keep its href if present
    return type.href ? { $ref: type.href } : { type: 'object' }
  }

  if (PRIMITIVE_TYPE_MAP[type]) return { ...PRIMITIVE_TYPE_MAP[type] }

  if (GEOMETRY_TYPES.has(type)) {
    const schema: JsonSchemaObject = { $ref: `${GEOJSON_REF_BASE}/${type}.json` }
    if (crs) schema.crs = crs
    return schema
  }

  // Fallback for unknown named types
  return { type: 'string' }
}

/**
 * Converts a UML attribute into a JSON Schema property fragment, applying
 * multiplicity (arrays) and default values.
 * For geometry attributes, the CRS string from `attr.meta.gisInfo.crs` is
 * forwarded to `typeToSchema` and emitted as a sibling `crs` property.
 */
const attributeToSchema = (attr: UMLAttribute, classDefKeyById: Map<string, string>): JsonSchemaObject => {
  const crs = attr.meta?.gisInfo?.crs
  const baseSchema = typeToSchema(attr.type, classDefKeyById, crs)
  const { lower, upper } = parseMultiplicity(attr.multiplicity)

  let schema: JsonSchemaObject
  if (isMany(upper)) {
    schema = { type: 'array', items: baseSchema }
    if (lower >= 1) schema.minItems = lower
    if (upper !== Infinity) schema.maxItems = upper
  } else {
    schema = baseSchema
  }

  if (attr.defaultValue !== undefined && attr.defaultValue !== '') {
    schema.default = attr.defaultValue
  }

  return schema
}

/**
 * Determines whether an attribute is required.
 */
const isAttributeRequired = (attr: UMLAttribute): boolean => {
  if (attr.isId) return true
  const { lower } = parseMultiplicity(attr.multiplicity)
  return lower >= 1
}

const hasAttributes = (element: UMLElement): element is UMLClass =>
  'attributes' in element && Array.isArray((element as UMLClass).attributes)

/**
 * Builds the object schema (properties/required) for a class-like element,
 * including associations originating from it and inheritance via allOf.
 */
const buildClassSchema = (
  element: UMLElement,
  diagram: UMLDiagram,
  classDefKeyById: Map<string, string>,
): JsonSchemaObject => {
  // Enumerations map to the `enum` keyword.
  if (element.type === 'enumeration') {
    const enumeration = element as UMLEnumeration
    return {
      title: element.name,
      enum: enumeration.literals.map(literal => literal.value ?? literal.name),
    }
  }

  const properties: JsonSchemaObject = {}
  const required: string[] = []

  if (hasAttributes(element)) {
    for (const attr of element.attributes) {
      properties[attr.name] = attributeToSchema(attr, classDefKeyById)
      if (isAttributeRequired(attr)) required.push(attr.name)
    }
  }

  // Associations / aggregation / composition originating from this element
  for (const edge of diagram.edges) {
    const rel = edge.data.relationship
    if (rel.source !== element.id) continue
    if (!['association', 'aggregation', 'composition'].includes(rel.type)) continue

    const targetDefKey = classDefKeyById.get(rel.target)
    if (!targetDefKey) continue

    const propName = rel.targetRole || sanitizeName(targetDefKey) || targetDefKey
    const { lower, upper } = parseMultiplicity(rel.targetMultiplicity)
    const ref: JsonSchemaObject = { $ref: `#/$defs/${targetDefKey}` }

    if (isMany(upper)) {
      const arraySchema: JsonSchemaObject = { type: 'array', items: ref }
      if (lower >= 1) arraySchema.minItems = lower
      properties[propName] = arraySchema
    } else {
      properties[propName] = ref
    }

    if (lower >= 1) required.push(propName)
  }

  const ownSchema: JsonSchemaObject = {
    type: 'object',
    title: element.name,
    properties,
  }
  if (element.documentation) ownSchema.description = element.documentation
  if (required.length > 0) ownSchema.required = required

  // Inheritance: subclass uses allOf [parent $ref, own schema]
  const parentRefs: JsonSchemaObject[] = []
  for (const edge of diagram.edges) {
    const rel = edge.data.relationship
    if (rel.source !== element.id) continue
    if (rel.type !== 'inheritance' && rel.type !== 'realization') continue
    const parentDefKey = classDefKeyById.get(rel.target)
    if (parentDefKey) parentRefs.push({ $ref: `#/$defs/${parentDefKey}` })
  }

  if (parentRefs.length > 0) {
    return { allOf: [...parentRefs, ownSchema] }
  }

  return ownSchema
}

/**
 * Returns all root elements of the diagram's class hierarchy.
 * A root is a non-enumeration class that is not the target of any composition,
 * aggregation, inheritance, or realization edge. When every non-enumeration
 * class is contained (a fully circular hierarchy), all non-enumeration classes
 * are treated as roots.
 *
 * Returning more than one root means the diagram has multiple disconnected
 * components and should be exported as a type-library schema.
 */
const findRootElements = (diagram: UMLDiagram, elements: UMLElement[]): UMLElement[] => {
  if (elements.length === 0) return []

  // An element is "contained" if it is the target of a composition/aggregation.
  const containedIds = new Set<string>()
  for (const edge of diagram.edges) {
    const rel = edge.data.relationship
    if (
      rel.type === 'composition' ||
      rel.type === 'aggregation' ||
      rel.type === 'inheritance' ||
      rel.type === 'realization'
    ) {
      containedIds.add(rel.target)
    }
  }

  const candidates = elements.filter(element => element.type !== 'enumeration' && !containedIds.has(element.id))
  const pool = candidates.length > 0 ? candidates : elements.filter(e => e.type !== 'enumeration')
  if (pool.length === 0) return elements.slice(0, 1)

  return pool
}

/**
 * Main export function - converts a UMLDiagram into a JSON Schema document.
 */
export const exportToJsonSchema = (diagram: UMLDiagram, modelUri?: string): JsonSchemaObject => {
  const elements = diagram.nodes.map(node => node.data.element)

  // Map every class element id to a stable `$defs` key.
  const classDefKeyById = new Map<string, string>()
  const usedKeys = new Set<string>()
  for (const element of elements) {
    let key = element.name || 'Type'
    let candidate = key
    let suffix = 1
    while (usedKeys.has(candidate)) {
      candidate = `${key}_${suffix++}`
    }
    key = candidate
    usedKeys.add(key)
    classDefKeyById.set(element.id, key)
  }

  const sanitizedName = sanitizeName(diagram.name) || 'untitled'
  const id = modelUri || `${BASE_MODEL_URI}/${sanitizedName}`

  const rootElements = findRootElements(diagram, elements)

  const schema: JsonSchemaObject = {
    $id: id,
    $schema: JSON_SCHEMA_DIALECT,
    title: diagram.name,
    type: 'object',
  }

  if (rootElements.length === 0) {
    schema.properties = {}
    return schema
  }

  // Multiple disconnected roots: use a type-library layout. All classes go into
  // $defs and the document root carries only the envelope fields ($id, $schema,
  // title). This prevents arbitrarily promoting one class to the document root
  // while silently orphaning the others as unreferenced $defs entries.
  if (rootElements.length > 1) {
    delete schema.type
    const defs: JsonSchemaObject = {}
    for (const element of elements) {
      const defKey = classDefKeyById.get(element.id)
      if (!defKey) continue
      defs[defKey] = buildClassSchema(element, diagram, classDefKeyById)
    }
    schema.$defs = defs
    return schema
  }

  // Single root: merge the root class schema into the document root.
  // The root class keeps its own title rather than being overwritten by the
  // diagram name.
  const rootElement = rootElements[0]
  const rootSchema = buildClassSchema(rootElement, diagram, classDefKeyById)
  Object.assign(schema, rootSchema)
  // buildClassSchema does not emit `type` for enumerations, so the
  // pre-initialized `type: 'object'` must be removed explicitly.
  if (rootElement.type === 'enumeration') {
    delete schema.type
  }
  schema.$id = id
  schema.$schema = JSON_SCHEMA_DIALECT
  schema.title = rootElement.name || diagram.name

  // Emit every non-root element into $defs.
  const defs: JsonSchemaObject = {}
  for (const element of elements) {
    if (element.id === rootElement.id) continue
    const defKey = classDefKeyById.get(element.id)
    if (!defKey) continue
    defs[defKey] = buildClassSchema(element, diagram, classDefKeyById)
  }
  if (Object.keys(defs).length > 0) {
    schema.$defs = defs
  }

  return schema
}

/**
 * Triggers a download of the JSON Schema content as a file.
 */
export const downloadJsonSchema = (diagram: UMLDiagram, filename?: string): void => {
  const schema = exportToJsonSchema(diagram)
  const content = JSON.stringify(schema, null, 2)
  const blob = new Blob([content], { type: 'application/schema+json' })
  const url = URL.createObjectURL(blob)

  const link = document.createElement('a')
  link.href = url
  link.download = filename || `${diagram.name.replace(/[^a-zA-Z0-9]/g, '_')}.schema.json`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)

  URL.revokeObjectURL(url)
}

// Avoid unused-variable lint for UMLRelationship import used only in JSDoc-typed contexts.
export type { UMLRelationship }
