/**
 * JSON Schema Export Service
 *
 * Converts UML diagram data to a JSON Schema (draft 2020-12) document.
 *
 * Only relationship types in the supported release scope contribute to the
 * schema; any other type (including legacy edges in older models) carries no
 * semantics and is silently ignored — no mapping is invented for it. The scope
 * is deliberately small and grows over time — see umlContainment.
 *
 * UML -> JSON Schema mapping:
 * - Each UML class becomes an object schema.
 * - Each attribute becomes a `properties` entry.
 * - Primitive UML types map to JSON Schema `type`/`format`.
 * - Geometry types map to a GeoJSON-style `$ref`.
 * - Multiplicity `*` / `n..*` becomes an `array` (with `minItems` when the
 *   lower bound is >= 1); required attributes (lower bound >= 1 or `isID`)
 *   are added to the class's `required` array.
 * - Enumerations map to the `enum` keyword.
 * - Inheritance maps to `allOf: [{ $ref }, { ...own }]`.
 * - Composition maps to a `$ref` property on the container (an array `$ref`
 *   for many multiplicities).
 *
 * Root: the document root is the data structure itself, titled after the
 * diagram. Every class is emitted under `$defs`; the single root class — the
 * `isRoot`-designated element or, absent a designation, the one class not
 * embedded by any structural or inheritance edge — is referenced from the
 * document root via a `$ref` property, so the structure's name — not an
 * arbitrary class — is always the top level. Every other element must be
 * reachable from the root; otherwise (or when no unique root exists) the
 * export throws {@link SchemaExportError} instead of guessing. An empty
 * diagram exports an empty object schema; a diagram consisting of a single
 * enumeration keeps its `enum` at the document root instead.
 */

import { transliterate } from '@/utils/urn'

import type { UMLDiagram } from '../types/diagram'
import type { UMLAttribute, UMLElement, UMLEnumeration, UMLRelationship, UMLType } from '../types/uml'
import { hasAttributes } from '../types/uml'
import type { RootResolutionFailure } from './umlContainment'
import { classifyStructuralEdge, collectParentIds, parseMultiplicity, resolveRootElement } from './umlContainment'

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
export const GEOJSON_REF_BASE = 'https://geojson.org/schema'

/** Upper-cases the first character, leaving the rest untouched. */
const upperFirst = (value: string): string => value.charAt(0).toUpperCase() + value.slice(1)

/** Runs of characters a property key may not contain; they separate words rather than survive. */
const PROPERTY_KEY_SEPARATORS = /[^A-Za-z0-9_]+/

/** Used when a name sanitizes to nothing at all (e.g. an attribute named `___`). */
export const PROPERTY_KEY_FALLBACK = 'Field'

/** The same, for an unnamed or fully unsanitizable element in `$defs`. */
const DEF_KEY_FALLBACK = 'Type'

/**
 * Normalizes a UML name into a JSON Schema property key: letters, digits and underscore survive,
 * German umlauts are transliterated, and every other character separates words, which are then
 * PascalCase-joined — `air quality` becomes `AirQuality`. The key must start with an uppercase
 * letter, so a leading underscore is dropped and a leading digit is prefixed with `N`. The digit is
 * prefixed rather than stripped because stripping would collapse `1Value` and `2Value` onto one key.
 *
 * Returns an empty string when nothing usable remains; callers substitute their own fallback.
 */
export const sanitizePropertyKey = (name: string): string => {
  const pascalCased = transliterate(name).split(PROPERTY_KEY_SEPARATORS).filter(Boolean).map(upperFirst).join('')
  const withoutLeadingUnderscores = pascalCased.replace(/^_+/, '')
  if (!withoutLeadingUnderscores) return ''
  return upperFirst(/^\d/.test(withoutLeadingUnderscores) ? `N${withoutLeadingUnderscores}` : withoutLeadingUnderscores)
}

/**
 * Reserves a unique key within one class's `properties`. Sanitization is lossy — `mein feld` and
 * `meinFeld` both yield `MeinFeld` — so without this the second property would silently overwrite
 * the first. Collisions are `_n`-suffixed, mirroring {@link assignDefKeys}.
 */
const reservePropertyKey = (candidate: string, usedKeys: Set<string>): string => {
  let key = candidate
  let suffix = 1
  while (usedKeys.has(key)) {
    key = `${candidate}_${suffix++}`
  }
  usedKeys.add(key)
  return key
}

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
  // Attributes and containment properties share one `properties` map, so they must draw their keys
  // from one reservation set.
  const usedPropertyKeys = new Set<string>()

  if (hasAttributes(element)) {
    for (const attr of element.attributes) {
      const key = reservePropertyKey(sanitizePropertyKey(attr.name) || PROPERTY_KEY_FALLBACK, usedPropertyKeys)
      properties[key] = attributeToSchema(attr, classDefKeyById)
      if (isAttributeRequired(attr)) required.push(key)
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
    // The role names the part as seen from its container, which is exactly what the property needs
    // to be called. The relationship's own name is deliberately not consulted: it labels the edge
    // ("works for"), carries no per-property meaning, is not unique among a class's edges, and is
    // free text that would land verbatim in a JSON key.
    const propName = reservePropertyKey(
      sanitizePropertyKey(containment.role || partElement?.name || partDefKey) || PROPERTY_KEY_FALLBACK,
      usedPropertyKeys,
    )
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
 * Maps every element id to a stable, unique `$defs` key (name-based, `_n`-suffixed on collision).
 * The keys go through the same normalization as property keys, so a `#/$defs/<key>` ref stays ASCII
 * and free of JSON-Pointer-special characters. That matters twice over: the platform resolvers match
 * these refs by prefix-stripping rather than JSON-Pointer evaluation and would not unescape a
 * `~0`/`~1` sequence, and standard tooling still sees a valid pointer.
 */
export const assignDefKeys = (elements: UMLElement[]): Map<string, string> => {
  const defKeyById = new Map<string, string>()
  const usedKeys = new Set<string>()
  for (const element of elements) {
    const key = sanitizePropertyKey(element.name) || DEF_KEY_FALLBACK
    defKeyById.set(element.id, reservePropertyKey(key, usedKeys))
  }
  return defKeyById
}

/**
 * The diagram cannot be exported as a schema: it has no unique root class or leaves elements
 * unreachable from it. Carries the typed {@link RootResolutionFailure} so callers can render a
 * precise, actionable message.
 */
export class SchemaExportError extends Error {
  constructor(readonly failure: RootResolutionFailure) {
    super(`diagram has no exportable root: ${failure.code}`)
    this.name = 'SchemaExportError'
  }
}

/**
 * Main export function - converts a UMLDiagram into a JSON Schema document.
 *
 * @throws SchemaExportError when the diagram has no unique root class or elements are unreachable
 *   from it — callers surface this as a validation message instead of persisting a guessed schema
 */
export const exportToJsonSchema = (diagram: UMLDiagram, modelUri?: string): JsonSchemaObject => {
  const elements = (diagram.nodes ?? []).map(node => node.data?.element).filter((e): e is UMLElement => !!e)

  const classDefKeyById = assignDefKeys(elements)

  const sanitizedName = sanitizeName(diagram.name) || 'untitled'
  const id = modelUri || `${BASE_MODEL_URI}/${sanitizedName}`

  const resolution = resolveRootElement(diagram)
  if (resolution.kind === 'invalid') throw new SchemaExportError(resolution.failure)

  const schema: JsonSchemaObject = {
    $id: id,
    $schema: JSON_SCHEMA_DIALECT,
    title: diagram.name,
    type: 'object',
  }

  if (resolution.kind === 'empty') {
    schema.properties = {}
    return schema
  }

  const rootElement = resolution.root
  if (resolution.kind === 'enum') {
    const rootSchema = buildClassSchema(rootElement, diagram, classDefKeyById)
    Object.assign(schema, rootSchema)
    // buildClassSchema omits `type` for enumerations, so drop the pre-initialized `type: 'object'`.
    delete schema.type
    schema.$id = id
    schema.$schema = JSON_SCHEMA_DIALECT
    schema.title = rootElement.name || diagram.name

    const defs = buildDefs(
      elements.filter(e => e.id !== rootElement.id),
      diagram,
      classDefKeyById,
    )
    if (Object.keys(defs).length > 0) {
      schema.$defs = defs
    }
    return schema
  }

  const rootDefKey = classDefKeyById.get(rootElement.id) as string
  schema.properties = {
    [sanitizePropertyKey(rootElement.name) || rootDefKey]: { $ref: `#/$defs/${rootDefKey}` },
  }
  schema.$defs = buildDefs(elements, diagram, classDefKeyById)
  return schema
}

const buildDefs = (
  elements: UMLElement[],
  diagram: UMLDiagram,
  classDefKeyById: Map<string, string>,
): JsonSchemaObject => {
  const defs: JsonSchemaObject = {}
  for (const element of elements) {
    const defKey = classDefKeyById.get(element.id)
    if (!defKey) continue
    defs[defKey] = buildClassSchema(element, diagram, classDefKeyById)
  }
  return defs
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
