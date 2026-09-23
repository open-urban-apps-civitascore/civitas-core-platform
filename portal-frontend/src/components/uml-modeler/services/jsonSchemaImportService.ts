/**
 * JSON Schema Import Service
 *
 * Builds a UML diagram from a JSON Schema document — the reverse of
 * {@link exportToJsonSchema}, and its mirror rule for rule.
 *
 * The export invents no mapping for a construct it does not know. The import holds the same line:
 * it reads exactly the shapes the export writes and rejects anything else with the construct named,
 * rather than producing a diagram that means something the document did not say.
 *
 * JSON Schema -> UML mapping:
 * - Each `$defs` member becomes a class; a member carrying `enum` becomes an enumeration.
 * - A scalar, geometry or external-`$ref` property becomes an attribute.
 * - A local `$ref` property becomes a composition, drawn from the part to the container (the
 *   container sits at the diamond end, which is the edge target).
 * - `array` gives the many multiplicity, `minItems`/`maxItems` its bounds, and membership in
 *   `required` the lower bound of a single value.
 * - `allOf: [{ $ref }, …, ownSchema]` gives one inheritance edge per parent reference.
 * - `x-core-primaryKey` gives `{id}`.
 * - The top-level `$ref` designates the root class.
 *
 * The document carries no layout: the export drops it, and a generated document never had one. The
 * import therefore places the classes itself, in rows by their distance from the root, so a chain
 * reads top to bottom. A caller merging into an open diagram passes an {@link ImportOptions.origin}
 * to keep the new classes clear of what is already there.
 */

import { umlNodeFor } from '../constants/elementTemplates'
import type { UMLDiagram, UMLEdge, UMLNode } from '../types/diagram'
import type {
  UMLAttribute,
  UMLClass,
  UMLElement,
  UMLEnumeration,
  UMLEnumLiteral,
  UMLRelationship,
  UMLType,
} from '../types/uml'
import { GEOJSON_REF_BASE } from './jsonSchemaExportService'

type JsonSchemaObject = Record<string, unknown>

const DEFS_REF_PREFIX = '#/$defs/'

/** The column width and row height a placed class occupies, generous enough to stay readable. */
const COLUMN_WIDTH = 320
const ROW_HEIGHT = 260
const COLUMNS_PER_ROW = 4

/**
 * The UML primitive a JSON Schema type and format stand for — the export's primitive map read
 * backwards. Null for a type the export never writes.
 */
const primitiveFor = (type: string, format: string | null): UMLType | null => {
  if (type === 'integer') return 'Integer'
  if (type === 'number') return 'Number'
  if (type === 'boolean') return 'Boolean'
  if (type !== 'string') return null
  if (format === 'uuid') return 'Uuid'
  if (format === 'date') return 'Date'
  if (format === 'date-time') return 'DateTime'
  return 'String'
}

const GEOJSON_REF = new RegExp(`^${GEOJSON_REF_BASE}/([A-Za-z]+)\\.json$`)

export interface ImportOptions {
  /** Where the placed classes start. Defaults to the canvas origin. */
  origin?: { x: number; y: number }
}

/** The document cannot be read as a diagram. Carries the construct that stopped the import. */
export class SchemaImportError extends Error {
  constructor(
    message: string,
    readonly construct: string,
  ) {
    super(message)
    this.name = 'SchemaImportError'
  }
}

/** The classes and relations a document describes, ready to be merged into a diagram. */
export interface ImportedSchema {
  nodes: UMLNode[]
  edges: UMLEdge[]
  /** The class the document designates as its root, or null when it designates none. */
  rootElementId: string | null
}

/**
 * Reads a JSON Schema document into classes and relations.
 *
 * @throws SchemaImportError when the document holds a construct the export never writes
 */
export const importFromJsonSchema = (document: JsonSchemaObject, options: ImportOptions = {}): ImportedSchema => {
  const { defs, rootKey: normalizedRoot } = normalize(document)
  const byUrn = new Map<string, string>()
  const elementByKey = new Map<string, UMLElement>()

  // Pass one: a class for every member, so a reference can resolve to it.
  for (const [key, raw] of Object.entries(defs)) {
    const member = asObject(raw)
    if (!member) throw new SchemaImportError(`the member '${key}' is not an object`, key)
    elementByKey.set(key, emptyElement(key, member))
    const id = asString(member.$id)
    // The canonical form addresses a member by its Element URN instead of a local pointer.
    if (id) byUrn.set(id, key)
  }

  // Pass two: the members' own content, now that every reference target exists.
  const edges: UMLEdge[] = []
  for (const [key, raw] of Object.entries(defs)) {
    fillElement(elementByKey.get(key)!, asObject(raw)!, key, { defs, byUrn, elementByKey, edges })
  }

  const rootKey = rootKeyOf(document, byUrn) ?? normalizedRoot
  const rootElement = rootKey ? elementByKey.get(rootKey) : undefined
  if (rootElement) rootElement.isRoot = true

  const order = [...elementByKey.keys()]
  const positions = layout(order, rootKey, edges, elementByKey, options.origin ?? { x: 0, y: 0 })
  const nodes = order.map(key => umlNodeFor(elementByKey.get(key)!, positions.get(key)!))

  return { nodes, edges, rootElementId: rootElement?.id ?? null }
}

/**
 * Reads a document into a whole diagram. Used where there is nothing to merge into — a structure
 * that carries a model but no diagram, and the round-trip check.
 */
export const diagramFromJsonSchema = (document: JsonSchemaObject, name?: string): UMLDiagram => {
  const { nodes, edges } = importFromJsonSchema(document)
  return {
    id: crypto.randomUUID(),
    name: name ?? asString(document.title) ?? 'Imported',
    nodes,
    edges,
    lastModified: new Date(),
    isDirty: false,
  }
}

interface ImportContext {
  defs: JsonSchemaObject
  byUrn: Map<string, string>
  elementByKey: Map<string, UMLElement>
  edges: UMLEdge[]
}

/** The shell of a member: its kind and its name, without content a reference could not resolve. */
const emptyElement = (key: string, member: JsonSchemaObject): UMLElement => {
  const name = asString(member.title) ?? key
  const id = crypto.randomUUID()
  if (Array.isArray(member.enum)) {
    return { id, name, type: 'enumeration', literals: [] } satisfies UMLEnumeration
  }
  return { id, name, type: 'class', attributes: [], operations: [] } satisfies UMLClass
}

const fillElement = (element: UMLElement, member: JsonSchemaObject, key: string, context: ImportContext): void => {
  if (element.type === 'enumeration') {
    element.literals = (member.enum as unknown[]).map(literalOf)
    return
  }

  // Inheritance and the member's own shape arrive together: allOf holds one reference per parent
  // and, last, the shape itself.
  const own = ownShapeOf(member, element, key, context)
  const documentation = asString(own.description)
  if (documentation) element.documentation = documentation

  const required = new Set((Array.isArray(own.required) ? own.required : []).filter(isString))
  for (const [propertyName, rawProperty] of Object.entries(asObject(own.properties) ?? {})) {
    const property = asObject(rawProperty)
    if (!property) throw new SchemaImportError(`the property '${propertyName}' is not an object`, propertyName)
    readProperty(element as UMLClass, propertyName, property, required.has(propertyName), context)
  }
}

/** The member's own object shape, adding an inheritance edge for every parent reference. */
const ownShapeOf = (
  member: JsonSchemaObject,
  element: UMLElement,
  key: string,
  context: ImportContext,
): JsonSchemaObject => {
  if (!Array.isArray(member.allOf)) return member

  let own: JsonSchemaObject | null = null
  for (const rawBranch of member.allOf) {
    const branch = asObject(rawBranch)
    if (!branch) throw new SchemaImportError(`a branch of '${key}' is not an object`, 'allOf')
    const parentKey = refKey(asString(branch.$ref), context.byUrn)
    if (parentKey) {
      const parent = context.elementByKey.get(parentKey)
      if (!parent) throw new SchemaImportError(`'${key}' inherits from the unknown '${parentKey}'`, '$ref')
      context.edges.push(edge('inheritance', element.id, parent.id))
      continue
    }
    if (branch.$ref) throw new SchemaImportError(`'${key}' inherits from outside the document`, '$ref')
    own = branch
  }
  return own ?? {}
}

/** One property: a composition when it references a member of the document, an attribute otherwise. */
const readProperty = (
  element: UMLClass,
  propertyName: string,
  property: JsonSchemaObject,
  required: boolean,
  context: ImportContext,
): void => {
  const { items, isMany, lower, upper } = collectionOf(property, required, propertyName)
  const partKey = refKey(asString(items.$ref), context.byUrn)
  if (partKey) {
    const part = context.elementByKey.get(partKey)
    if (!part) throw new SchemaImportError(`'${propertyName}' references the unknown '${partKey}'`, '$ref')
    context.edges.push(composition(part, element, propertyName, multiplicity(lower, upper)))
    return
  }
  element.attributes.push(attribute(propertyName, items, multiplicity(lower, upper), isMany, property))
}

/** Unwraps an array property into its item schema and its bounds. */
const collectionOf = (
  property: JsonSchemaObject,
  required: boolean,
  propertyName: string,
): { items: JsonSchemaObject; isMany: boolean; lower: number; upper: number } => {
  if (asString(property.type) !== 'array') {
    return { items: property, isMany: false, lower: required ? 1 : 0, upper: 1 }
  }
  const items = asObject(property.items)
  if (!items) throw new SchemaImportError(`the array '${propertyName}' declares no items`, 'items')
  const lower = asNumber(property.minItems) ?? 0
  const upper = asNumber(property.maxItems) ?? Infinity
  return { items, isMany: true, lower, upper }
}

const attribute = (
  name: string,
  items: JsonSchemaObject,
  multiplicityText: string,
  isMany: boolean,
  property: JsonSchemaObject,
): UMLAttribute => {
  const attr: UMLAttribute = {
    id: crypto.randomUUID(),
    name,
    type: typeOf(items, name),
    multiplicity: multiplicityText,
  }
  const crs = asString(items.crs)
  if (crs) attr.meta = { gisInfo: { crs } }
  const defaultValue = property.default
  if (defaultValue !== undefined) attr.defaultValue = String(defaultValue)
  // Only a mandatory single value can back a key, which is the same rule the export applies.
  if (items['x-core-primaryKey'] === true && !isMany) attr.isId = true
  return attr
}

const typeOf = (schema: JsonSchemaObject, propertyName: string): UMLType => {
  const ref = asString(schema.$ref)
  if (ref) {
    const geometry = GEOJSON_REF.exec(ref)
    if (geometry) return geometry[1] as UMLType
    // A reference the document does not resolve is an external type; the export keeps its href.
    return { id: crypto.randomUUID(), name: lastSegment(ref), isExternal: true, href: ref }
  }

  const type = asString(schema.type)
  if (!type) {
    // An empty schema says "any value". UML has no such type, so it reads as text — the same
    // degradation the mapping editor applies to it.
    return 'String'
  }
  if (type === 'object') {
    // An object that declares properties was lifted into the library before the walk, so anything
    // still inline here is a free JSON value — a geometry, a measurement's quality, an entity's
    // own bag. The export writes Json as exactly this, so a re-export gives the document it came
    // from.
    return 'Json'
  }
  const primitive = primitiveFor(type, asString(schema.format))
  if (!primitive) {
    throw new SchemaImportError(`the property '${propertyName}' has the unsupported type '${type}'`, type)
  }
  return primitive
}

const literalOf = (value: unknown): UMLEnumLiteral => ({
  id: crypto.randomUUID(),
  name: String(value),
  value: value as string | number,
})

/**
 * The composition edge for a part inside a container. It runs from the part to the container,
 * because the container sits at the diamond end. The role is set only where the property name is
 * not what the export would derive from the part's name, so a diagram stays free of roles that
 * repeat the class.
 */
const composition = (
  part: UMLElement,
  container: UMLClass,
  propertyName: string,
  multiplicityText: string,
): UMLEdge => {
  const relationship: UMLRelationship = {
    id: crypto.randomUUID(),
    type: 'composition',
    source: part.id,
    target: container.id,
    sourceMultiplicity: multiplicityText,
  }
  if (propertyName !== lowerFirst(part.name)) relationship.sourceRole = propertyName
  return {
    id: relationship.id,
    source: part.id,
    target: container.id,
    type: 'composition',
    data: { relationship },
  }
}

const edge = (type: 'inheritance', source: string, target: string): UMLEdge => {
  const relationship: UMLRelationship = { id: crypto.randomUUID(), type, source, target }
  return { id: relationship.id, source, target, type, data: { relationship } }
}

const multiplicity = (lower: number, upper: number): string => {
  if (upper === Infinity) return lower >= 1 ? `${lower}..*` : '*'
  return `${lower}..${upper}`
}

/**
 * Places the classes in rows by their distance from the root, so a chain reads top to bottom. A
 * class the root does not reach lands in the rows below, in the order the document lists it.
 */
const layout = (
  order: string[],
  rootKey: string | null,
  edges: UMLEdge[],
  elementByKey: Map<string, UMLElement>,
  origin: { x: number; y: number },
): Map<string, { x: number; y: number }> => {
  const keyById = new Map([...elementByKey].map(([key, element]) => [element.id, key]))
  const parts = new Map<string, string[]>()
  for (const candidate of edges) {
    // A composition runs from the part to its container, so the container's row comes first.
    const containerKey = keyById.get(candidate.target)
    const partKey = keyById.get(candidate.source)
    if (candidate.type !== 'composition' || !containerKey || !partKey) continue
    parts.set(containerKey, [...(parts.get(containerKey) ?? []), partKey])
  }

  const rows: string[][] = []
  const placed = new Set<string>()
  let current = rootKey && elementByKey.has(rootKey) ? [rootKey] : []
  while (current.length > 0) {
    rows.push(current)
    current.forEach(key => placed.add(key))
    current = current
      .flatMap(key => parts.get(key) ?? [])
      .filter(key => !placed.has(key))
      .filter((key, index, all) => all.indexOf(key) === index)
  }
  const rest = order.filter(key => !placed.has(key))
  for (let index = 0; index < rest.length; index += COLUMNS_PER_ROW) {
    rows.push(rest.slice(index, index + COLUMNS_PER_ROW))
  }

  const positions = new Map<string, { x: number; y: number }>()
  rows.forEach((row, rowIndex) => {
    row.forEach((key, columnIndex) => {
      positions.set(key, {
        x: origin.x + columnIndex * COLUMN_WIDTH,
        y: origin.y + rowIndex * ROW_HEIGHT,
      })
    })
  })
  return positions
}

/**
 * The document's classes, in the one shape the reader works on: a `$defs` library.
 *
 * The editor writes that shape, but it is not the only one stored. A model that came in over the
 * API, or that a Data source generated, often carries its record at the document root and its
 * nested classes inline. Both say the same thing, so the inline ones are lifted into the library
 * under `<Parent><Property>` and replaced by a reference — the form the editor writes back on the
 * next save.
 */
const normalize = (document: JsonSchemaObject): { defs: JsonSchemaObject; rootKey: string | null } => {
  const defs: JsonSchemaObject = { ...(asObject(document.$defs) ?? {}) }

  // A document that designates its record — by a top-level `$ref` or a wrapper — already has its
  // classes in the library.
  // The wrapper may name its member by the member's URN, so the URNs must be known first.
  const designated = rootKeyOf(document, memberUrns(defs))
  let rootKey = designated

  if (!designated && asObject(document.properties)) {
    rootKey = uniqueKey(defs, asString(document.title) ?? 'Record')
    defs[rootKey] = {
      type: 'object',
      title: asString(document.title) ?? rootKey,
      ...(asString(document.description) ? { description: document.description } : {}),
      properties: document.properties,
      ...(Array.isArray(document.required) ? { required: document.required } : {}),
    }
  }

  for (const key of Object.keys(defs)) {
    const member = asObject(defs[key])
    if (member) defs[key] = liftInlineObjects(member, key, defs)
  }
  return { defs, rootKey }
}

/** The member key of every member that carries an Element URN. */
const memberUrns = (defs: JsonSchemaObject): Map<string, string> => {
  const byUrn = new Map<string, string>()
  for (const [key, raw] of Object.entries(defs)) {
    const id = asString(asObject(raw)?.$id)
    if (id) byUrn.set(id, key)
  }
  return byUrn
}

/** Replaces every inline object property of a class by a reference to a lifted class. */
const liftInlineObjects = (member: JsonSchemaObject, owner: string, defs: JsonSchemaObject): JsonSchemaObject => {
  if (Array.isArray(member.allOf)) {
    // A class that inherits keeps its own shape in a branch, and that branch is what the reader
    // reads. The parent references stay as they are.
    return {
      ...member,
      allOf: member.allOf.map(raw => {
        const branch = asObject(raw)
        return branch && !branch.$ref ? liftInlineObjects(branch, owner, defs) : raw
      }),
    }
  }
  const properties = asObject(member.properties)
  if (!properties) return member

  const lifted: JsonSchemaObject = {}
  for (const [name, raw] of Object.entries(properties)) {
    const property = asObject(raw)
    if (!property) {
      lifted[name] = raw
      continue
    }
    const items = asString(property.type) === 'array' ? asObject(property.items) : null
    const shape = items ?? property
    if (!asObject(shape.properties)) {
      lifted[name] = raw
      continue
    }

    const key = uniqueKey(defs, owner + pascal(name))
    defs[key] = liftInlineObjects({ title: key, ...shape }, key, defs)
    lifted[name] = items ? { ...property, items: { $ref: DEFS_REF_PREFIX + key } } : { $ref: DEFS_REF_PREFIX + key }
  }
  return { ...member, properties: lifted }
}

/** A key the library does not hold yet; the suffix follows the one the export uses. */
const uniqueKey = (defs: JsonSchemaObject, wanted: string): string => {
  let candidate = wanted
  let suffix = 1
  while (candidate in defs) {
    candidate = `${wanted}_${suffix++}`
  }
  return candidate
}

const pascal = (name: string): string =>
  name
    .split(/[^A-Za-z0-9]+/)
    .filter(Boolean)
    .map(part => part.charAt(0).toUpperCase() + part.slice(1))
    .join('')

/**
 * The member the document designates as its record.
 *
 * <p>Two forms say it, and both are in use: a bare top-level `$ref`, which {@link
 * exportToJsonSchema} writes, and a wrapper root — one property holding nothing but a reference —
 * which a generated structure carries. The platform's own readers resolve both, so the import
 * does too.
 */
const rootKeyOf = (document: JsonSchemaObject, byUrn: Map<string, string>): string | null => {
  const direct = refKey(asString(document.$ref), byUrn)
  if (direct) return direct

  const properties = asObject(document.properties)
  const entries = properties ? Object.values(properties) : []
  if (entries.length !== 1) return null
  const only = asObject(entries[0])
  // A bare reference and nothing beside it: a property with siblings is a field, not a wrapper.
  if (!only || Object.keys(only).length !== 1) return null
  return refKey(asString(only.$ref), byUrn)
}

/** The member key a reference points at: a local pointer, or an Element URN in the canonical form. */
const refKey = (ref: string | null, byUrn: Map<string, string>): string | null => {
  if (!ref) return null
  if (ref.startsWith(DEFS_REF_PREFIX)) return ref.slice(DEFS_REF_PREFIX.length)
  return byUrn.get(ref) ?? null
}

const lastSegment = (ref: string): string => ref.split(/[/:]/).pop() || ref

const lowerFirst = (value: string): string => value.charAt(0).toLowerCase() + value.slice(1)

const isString = (value: unknown): value is string => typeof value === 'string'

const asString = (value: unknown): string | null => (typeof value === 'string' ? value : null)

const asNumber = (value: unknown): number | null => (typeof value === 'number' ? value : null)

const asObject = (value: unknown): JsonSchemaObject | null =>
  typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as JsonSchemaObject) : null
