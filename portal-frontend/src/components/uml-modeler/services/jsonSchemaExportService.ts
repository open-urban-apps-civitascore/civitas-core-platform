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
 * Root selection: the diagram is always exported as a single JSON Schema
 * document (one root class plus `$defs`). The root is the class that is not
 * contained by any other class via composition, aggregation, inheritance, or
 * realization; every other class is emitted under `$defs` and linked via
 * `$ref`. When ambiguous, the class matching the diagram name is preferred,
 * falling back to the first class node.
 */

import type { UMLDiagram } from '../types/diagram'
import type { UMLAttribute, UMLElement, UMLEnumeration, UMLRelationship, UMLType } from '../types/uml'
import { hasAttributes } from '../types/uml'
import {
  classifyStructuralEdge,
  collectContainedIds,
  collectParentIds,
  parseMultiplicity,
  selectRootElement,
} from './umlContainment'

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

/** Lower-cases the first character, leaving the rest untouched. */
const lowerFirst = (value: string): string => value.charAt(0).toLowerCase() + value.slice(1)

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
 * `x-core-primaryKey` marks the primary key, only on a mandatory single-valued
 * attribute (an array or optional value cannot back one).
 */
const attributeToSchema = (attr: UMLAttribute, classDefKeyById: Map<string, string>): JsonSchemaObject => {
  const crs = attr.meta?.gisInfo?.crs
  const baseSchema = typeToSchema(attr.type, classDefKeyById, crs)
  const { lower, upper } = parseMultiplicity(attr.multiplicity)

  let schema: JsonSchemaObject
  const isMultivalued = upper > 1
  if (isMultivalued) {
    schema = { type: 'array', items: baseSchema }
    if (lower >= 1) schema.minItems = lower
    if (upper !== Infinity) schema.maxItems = upper
  } else {
    schema = baseSchema
  }

  if (attr.defaultValue !== undefined && attr.defaultValue !== '') {
    schema.default = attr.defaultValue
  }

  // Conceptual identity marker (engine-neutral): the UML "{id}" attribute is the entity's primary
  // key. Adapters interpret it technically (PostGIS PRIMARY KEY + UPSERT, FROST reference key, …);
  // the editor stays unaware of any concrete implementation. JSON Schema has no native PK keyword,
  // so the platform extension keyword 'x-core-primaryKey' carries it. Only a mandatory single value
  // can back a key, so an array- or optional-valued isId (e.g. an imported/edge-authored 0..1) is
  // not marked.
  if (attr.isId && canMultiplicityBePrimaryKey(attr.multiplicity)) {
    schema['x-core-primaryKey'] = true
  }

  return schema
}

/**
 * Determines whether an attribute is required. A valid primary-key {@code isId} is always required;
 * otherwise requiredness follows the multiplicity lower bound (so an optional single-valued isId,
 * which cannot be a key, stays optional).
 */
export const isAttributeRequired = (attr: UMLAttribute): boolean => {
  if (attr.isId && canMultiplicityBePrimaryKey(attr.multiplicity)) return true
  return parseMultiplicity(attr.multiplicity).lower >= 1
}

/**
 * A primary key must be exactly one mandatory value: a many multiplicity is an array, and an
 * optional one (`0..1`) is nullable — neither can be (part of) a primary key.
 */
export const canMultiplicityBePrimaryKey = (multiplicity?: string): boolean => {
  const { lower, upper } = parseMultiplicity(multiplicity)
  return lower >= 1 && upper === 1
}

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

  for (const edge of diagram.edges ?? []) {
    const rel = edge.data?.relationship
    if (!rel) continue
    const containment = classifyStructuralEdge(rel)
    if (!containment || containment.containerId !== element.id) continue

    const partDefKey = classDefKeyById.get(containment.partId)
    if (!partDefKey) continue

    const partElement = (diagram.nodes ?? []).find(node => node.data?.element?.id === containment.partId)?.data?.element
    const propName =
      containment.role ||
      rel.name ||
      (partElement ? lowerFirst(partElement.name) : '') ||
      sanitizeName(partDefKey) ||
      partDefKey
    const { lower, upper } = parseMultiplicity(containment.multiplicity)
    const ref: JsonSchemaObject = { $ref: `#/$defs/${partDefKey}` }

    if (upper > 1) {
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

  const parentRefs: JsonSchemaObject[] = []
  for (const parentId of collectParentIds(diagram, element.id)) {
    const parentDefKey = classDefKeyById.get(parentId)
    if (parentDefKey) parentRefs.push({ $ref: `#/$defs/${parentDefKey}` })
  }

  if (parentRefs.length > 0) {
    return { allOf: [...parentRefs, ownSchema] }
  }

  return ownSchema
}

/**
 * Main export function - converts a UMLDiagram into a JSON Schema document.
 */
export const exportToJsonSchema = (diagram: UMLDiagram, modelUri?: string): JsonSchemaObject => {
  const elements = (diagram.nodes ?? []).map(node => node.data?.element).filter((e): e is UMLElement => !!e)

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

  const rootElement = selectRootElement(elements, collectContainedIds(diagram), diagram.name)

  const schema: JsonSchemaObject = {
    $id: id,
    $schema: JSON_SCHEMA_DIALECT,
    title: diagram.name,
    type: 'object',
  }

  if (!rootElement) {
    schema.properties = {}
    return schema
  }

  // Merge the root class schema into the document root. The root class keeps its
  // own title rather than being overwritten by the diagram name.
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
