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
 * Output shape: a DataStructure — a JSON-Schema `$defs` library of its member
 * Elements with NO inline root shape. Every class is emitted under `$defs` with
 * local `#/$defs/<Name>` references between classes (no per-member `$id`, so those
 * local refs resolve during validation); Model Forge splits each `$defs` member
 * into its own Element, mints the Element URNs, and rewrites the local refs to
 * them. The single root class — the `isRoot`-designated element or, absent a
 * designation, the one class not embedded by any structural or inheritance edge —
 * is designated by a top-level `$ref` (a local `#/$defs/<Name>` pointer Model
 * Forge rewrites to the root member's URN), so a root-shaped schema can be derived
 * by clients that need one. Every other element must be reachable from the root;
 * otherwise (or when no unique root exists) the export throws
 * {@link SchemaExportError} instead of guessing. An empty diagram exports a
 * library with no members and no root.
 */

import { elementModelUrnForMember } from '@/utils/urn'

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
 * Maps every element id to a stable, unique `$defs` key (name-based, `_n`-suffixed on collision).
 * The keys are embedded verbatim in `#/$defs/<key>` refs, which the platform resolvers match by
 * prefix-stripping rather than JSON-Pointer evaluation — so instead of `~0`/`~1`-escaping the refs
 * (which those resolvers would not unescape), pointer-special characters are kept out of the keys
 * themselves, making every emitted ref a valid JSON Pointer for standard tooling too.
 */
export const assignDefKeys = (elements: UMLElement[]): Map<string, string> => {
  const defKeyById = new Map<string, string>()
  const usedKeys = new Set<string>()
  for (const element of elements) {
    const key = (element.name || 'Type').replace(/[~/]/g, '-')
    let candidate = key
    let suffix = 1
    while (usedKeys.has(candidate)) {
      candidate = `${key}_${suffix++}`
    }
    usedKeys.add(candidate)
    defKeyById.set(element.id, candidate)
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

  // A DataStructure is a JSON-Schema $defs library of member Elements — no inline root shape. Every
  // class is a $defs member (stamped with its member Element URN so Model Forge splits it into a
  // separate Element under a stable, name-based URN). The root class, when the diagram has one, is
  // designated by a top-level $ref (a local pointer Model Forge rewrites to the member's URN), so a
  // root-shaped schema can still be derived by clients that need one. An empty diagram yields a
  // library with no members and no root.
  const schema: JsonSchemaObject = {
    $id: id,
    $schema: JSON_SCHEMA_DIALECT,
    title: diagram.name,
  }

  // When exporting under a real DataStructure CORE URN (the save path), emit the canonical,
  // split-ready form: each $defs member carries its Element CORE URN as `$id` and cross-references
  // siblings by that URN (no local "#/$defs/" pointers survive, so the per-member `$id` re-bases
  // nothing). Model Forge then splits the members into stable, name-based Element artifacts without
  // minting, and the frontend can validate the document against the generated CORE schema. Without a
  // URN (e.g. a standalone download/preview) the local "#/$defs/<Name>" form is kept.
  const canonical = /^urn:core:[^:]+:[^:]+:datastructure:/.test(id)

  const defs = buildDefs(elements, diagram, classDefKeyById)
  if (Object.keys(defs).length > 0) schema.$defs = canonical ? canonicalizeDefs(defs, id) : defs

  if (resolution.kind !== 'empty') {
    const rootDefKey = classDefKeyById.get(resolution.root.id)
    if (rootDefKey) schema.$ref = canonical ? elementModelUrnForMember(id, rootDefKey) : `#/$defs/${rootDefKey}`
  }
  return schema
}

const DEFS_REF_PREFIX = '#/$defs/'

/**
 * Turns the local-ref `$defs` library into the canonical split-ready form: stamps each member with
 * its Element CORE URN as `$id` and rewrites every local `#/$defs/<Name>` reference (in properties,
 * `allOf`, arrays, …) to that member's Element URN. A cross-ref and the member it points at both
 * resolve through {@link elementModelUrnForMember} with the same `$defs` key, so they match.
 */
const canonicalizeDefs = (defs: JsonSchemaObject, dataStructureUrn: string): JsonSchemaObject => {
  const out: JsonSchemaObject = {}
  for (const [key, member] of Object.entries(defs)) {
    const rewritten = rewriteLocalRefsToUrns(member, dataStructureUrn) as JsonSchemaObject
    out[key] = { $id: elementModelUrnForMember(dataStructureUrn, key), ...rewritten }
  }
  return out
}

/** Deep-copies {@code node}, replacing every local `#/$defs/<Name>` `$ref` with the member Element URN. */
const rewriteLocalRefsToUrns = (node: unknown, dataStructureUrn: string): unknown => {
  if (Array.isArray(node)) return node.map(child => rewriteLocalRefsToUrns(child, dataStructureUrn))
  if (node && typeof node === 'object') {
    const out: JsonSchemaObject = {}
    for (const [k, v] of Object.entries(node as JsonSchemaObject)) {
      out[k] =
        k === '$ref' && typeof v === 'string' && v.startsWith(DEFS_REF_PREFIX)
          ? elementModelUrnForMember(dataStructureUrn, v.slice(DEFS_REF_PREFIX.length))
          : rewriteLocalRefsToUrns(v, dataStructureUrn)
    }
    return out
  }
  return node
}

const buildDefs = (
  elements: UMLElement[],
  diagram: UMLDiagram,
  classDefKeyById: Map<string, string>,
): JsonSchemaObject => {
  const defs: JsonSchemaObject = {}
  // Members are emitted WITHOUT a $id: a $id would re-base the subschema so a sibling's local
  // "#/$defs/<Name>" cross-reference no longer resolves during validation. Model Forge splits each
  // member into its own Element (minting the URN) and rewrites the local refs to those URNs.
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
