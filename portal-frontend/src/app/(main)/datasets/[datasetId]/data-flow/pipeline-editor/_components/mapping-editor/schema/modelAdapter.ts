import type { FieldNode, FieldType, GeometryType, SchemaTree } from '../_types'
import { field, isGeometryType } from '../_types'

/**
 * Builds the mapping editor's field tree from a DataStructure version's persisted JSON-Schema
 * `model` — the same artifact the engine adapters interpret at deploy time.
 *
 * The resolution semantics mirror `DataStructureSchema` (config-adapter-api) so the editor shows
 * exactly the record shape the backend derives: a wrapper root (a root whose single property is a
 * bare local `$ref`) is resolved through to the referenced class, `$defs` shadows legacy
 * `definitions`, `allOf` branches and local `$ref` parents merge parents-first with own properties
 * overriding in place, and external (http/https) `$ref` parents are skipped while any other
 * unresolvable parent is rejected. GeoJSON geometry `$ref`s become typed geometry ports; other
 * non-local `$ref`s on ordinary properties render as opaque object leaves — the engine's
 * JSONB-column reading of the same spec. The runtime records are
 * entity-shaped (the wrapper never appears in the data), so all field paths stay anchored at the
 * record: a resolved root class becomes the tree's single `$` node, while a document-root record
 * (legacy flat root) exposes its properties directly; the data structure's name (the schema
 * `title`) is the tree name.
 */

/**
 * The model is structurally unresolvable (broken wrapper target, dangling or unresolvable `$ref`,
 * no selectable definition). Callers fall back to the diagram-based tree for exactly this class —
 * anything else escaping the walker is a bug and must not be swallowed.
 */
export class ModelResolutionError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'ModelResolutionError'
  }
}

/**
 * The JSON-Schema keywords the walker dispatches on. Values stay `unknown` — persisted models
 * drift historically and keywords combine freely, so shape-narrowing happens per read — but the
 * closed key set turns keyword typos into compile errors.
 */
interface SchemaNode {
  readonly $ref?: unknown
  readonly properties?: unknown
  readonly allOf?: unknown
  readonly required?: unknown
  readonly type?: unknown
  readonly format?: unknown
  readonly items?: unknown
  readonly enum?: unknown
  readonly title?: unknown
  readonly $defs?: unknown
  readonly definitions?: unknown
}

const GEOJSON_REF = /geojson\.org\/schema\/([A-Za-z]+)\.json$/

const asNode = (value: unknown): SchemaNode | null =>
  typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as SchemaNode) : null

const asString = (value: unknown): string | null => (typeof value === 'string' ? value : null)

const propertiesOf = (node: SchemaNode): Record<string, SchemaNode> => {
  const properties: Record<string, SchemaNode> = {}
  const section = asNode(node.properties)
  if (!section) return properties
  for (const [name, property] of Object.entries(section)) {
    const propertyNode = asNode(property)
    if (propertyNode) properties[name] = propertyNode
  }
  return properties
}

/** `$defs` merged over legacy `definitions` (`$defs` wins on key collision). */
const definitionsOf = (root: SchemaNode): Record<string, SchemaNode> => {
  const merged: Record<string, SchemaNode> = {}
  for (const key of ['definitions', '$defs'] as const) {
    const section = asNode(root[key])
    if (!section) continue
    for (const [name, def] of Object.entries(section)) {
      const defNode = asNode(def)
      if (defNode) merged[name] = defNode
    }
  }
  return merged
}

/** The local definition name of a `#/$defs/...` or `#/definitions/...` ref, else null. */
const localDefName = (ref: string | null): string | null => {
  if (!ref) return null
  for (const prefix of ['#/$defs/', '#/definitions/']) {
    if (ref.startsWith(prefix)) return ref.slice(prefix.length)
  }
  return null
}

const isExternalSchemaUri = (ref: string): boolean => ref.startsWith('http://') || ref.startsWith('https://')

interface ResolvedDefinition {
  properties: Record<string, SchemaNode>
  required: Set<string>
}

/**
 * Merges a definition with its `allOf` branches and local `$ref` parents, parents first so
 * inherited properties keep their first-seen position while own properties override the value in
 * place (insertion-order objects give LinkedHashMap semantics for free).
 */
const mergeDefinition = (node: SchemaNode, defs: Record<string, SchemaNode>): ResolvedDefinition => {
  const resolved: ResolvedDefinition = { properties: {}, required: new Set() }
  collectInto(node, defs, resolved, new Set())
  return resolved
}

const collectInto = (
  node: SchemaNode,
  defs: Record<string, SchemaNode>,
  into: ResolvedDefinition,
  visitedRefs: Set<string>,
): void => {
  for (const branch of Array.isArray(node.allOf) ? node.allOf : []) {
    const branchNode = asNode(branch)
    if (branchNode) collectInto(branchNode, defs, into, visitedRefs)
  }

  const ref = asString(node.$ref)
  if (ref && !isExternalSchemaUri(ref)) {
    const parentName = localDefName(ref)
    if (!parentName) {
      throw new ModelResolutionError(
        `Unsupported non-local parent $ref '${ref}' — only #/$defs/ and #/definitions/ refs are resolvable`,
      )
    }
    if (!visitedRefs.has(parentName)) {
      visitedRefs.add(parentName)
      const parent = defs[parentName]
      // Skipping a missing parent would render a truncated tree for a model the engine rejects.
      if (!parent) {
        throw new ModelResolutionError(`Parent $ref '${ref}' resolves to no definition`)
      }
      collectInto(parent, defs, into, visitedRefs)
    }
  }

  for (const [name, property] of Object.entries(propertiesOf(node))) {
    into.properties[name] = property
  }
  for (const name of Array.isArray(node.required) ? node.required : []) {
    if (typeof name === 'string') into.required.add(name)
  }
}

/**
 * Wrapper-root detection matching `DataStructureSchema.wrappedRootDefinition`: no `allOf` array
 * (even an empty one disqualifies), exactly one property whose value is a bare `$ref` (no
 * siblings) to a local definition. A wrapper whose target is missing is a broken schema and
 * rejected.
 */
const wrappedRootName = (root: SchemaNode, defs: Record<string, SchemaNode>): string | null => {
  if (Array.isArray(root.allOf)) return null
  const properties = asNode(root.properties)
  if (!properties) return null
  const values = Object.values(properties)
  if (values.length !== 1) return null
  const property = asNode(values[0])
  if (!property || Object.keys(property).length !== 1) return null
  const name = localDefName(asString(property.$ref))
  if (!name) return null
  const target = defs[name]
  if (!target || Object.keys(target).length === 0) {
    throw new ModelResolutionError(`Wrapper root references missing definition '${name}'`)
  }
  return name
}

interface RootResolution {
  /** The resolved root class, or null when the record is the document root itself. */
  className: string | null
  definition: ResolvedDefinition
}

/** Mirrors `DataStructureSchema.resolveDefinition`'s precedence: wrapper → root itself → named definition. */
const resolveRoot = (root: SchemaNode, defs: Record<string, SchemaNode>): RootResolution => {
  const wrapperName = wrappedRootName(root, defs)
  if (wrapperName) {
    return { className: wrapperName, definition: mergeDefinition(defs[wrapperName], defs) }
  }

  if (Object.keys(propertiesOf(root)).length > 0 || Array.isArray(root.allOf)) {
    const merged = mergeDefinition(root, defs)
    if (Object.keys(merged.properties).length > 0) {
      return { className: null, definition: merged }
    }
  }

  const names = Object.keys(defs)
  const title = asString(root.title)
  // Last tier mirrors Java's singlePropertyDefinition: only definitions that carry their own
  // properties qualify — an allOf-only definition cannot stand in as the record.
  const withProperties = names.filter(name => Object.keys(propertiesOf(defs[name])).length > 0)
  const selected =
    (names.length === 1 ? names[0] : null) ??
    (title && defs[title] ? title : null) ??
    (withProperties.length === 1 ? withProperties[0] : null)
  if (!selected) {
    throw new ModelResolutionError(
      names.length === 0
        ? 'Model has no resolvable definition'
        : `Model has ${names.length} definitions and none matches the title`,
    )
  }
  return { className: selected, definition: mergeDefinition(defs[selected], defs) }
}

const scalarTypeOf = (node: SchemaNode): FieldType => {
  const type = asString(node.type)
  if (type === 'integer') return 'int'
  if (type === 'number') return 'float'
  if (type === 'boolean') return 'bool'
  if (type === 'string') {
    const format = asString(node.format)
    return format === 'date-time' || format === 'date' ? 'date' : 'str'
  }
  // Deliberately permissive: exotic-but-valid schema constructs (type arrays, const/oneOf, "null")
  // must not block the editor, so anything unrecognized degrades to a plain string port.
  return 'str'
}

const geometryTypeOf = (ref: string): GeometryType | null => {
  const match = GEOJSON_REF.exec(ref)
  return match && isGeometryType(match[1]) ? match[1] : null
}

const buildFields = (
  definition: ResolvedDefinition,
  defs: Record<string, SchemaNode>,
  base: string,
  visited: Set<string>,
): FieldNode[] => {
  const fields: FieldNode[] = []
  for (const [name, property] of Object.entries(definition.properties)) {
    fields.push(fieldFor(name, property, defs, `${base}.${name}`, definition.required.has(name), visited))
  }
  return fields
}

const fieldFor = (
  name: string,
  node: SchemaNode,
  defs: Record<string, SchemaNode>,
  path: string,
  required: boolean,
  visited: Set<string>,
): FieldNode => {
  if (asString(node.type) === 'array') {
    const items = asNode(node.items)
    const children = items ? childrenOf(items, defs, `${path}[]`, visited) : undefined
    return field(path, name, 'array', required, children)
  }

  const ref = asString(node.$ref)
  if (ref) {
    const geometry = geometryTypeOf(ref)
    if (geometry) return field(path, name, geometry, required)
    const refName = localDefName(ref)
    // An enumeration is a scalar value at runtime, not a nested object.
    if (refName && defs[refName] && 'enum' in defs[refName]) return field(path, name, 'str', required)
    const children = childrenOf(node, defs, path, visited)
    return field(path, name, 'object', required, children)
  }

  if (asString(node.type) === 'object' || asNode(node.properties)) {
    const children = childrenOf(node, defs, path, visited)
    return field(path, name, 'object', required, children)
  }

  return field(path, name, scalarTypeOf(node), required)
}

/**
 * Child fields of a `$ref`/inline-object node. No children for: ref cycles (the class already
 * appears on this expansion path — `visited` holds exactly those def names), non-local refs (the
 * engine reads them as opaque JSONB values, so an unexpandable object leaf is the honest
 * rendering), and enums. A local ref to a missing definition throws: the record can never conform
 * to a dangling ref, and rendering a plausible empty object would hide the defect.
 */
const childrenOf = (
  node: SchemaNode,
  defs: Record<string, SchemaNode>,
  base: string,
  visited: Set<string>,
): FieldNode[] | undefined => {
  const ref = asString(node.$ref)
  const refName = localDefName(ref)
  if (refName) {
    if (visited.has(refName)) return undefined
    const target = defs[refName]
    if (!target) {
      throw new ModelResolutionError(`Property $ref '${ref}' resolves to no definition`)
    }
    if ('enum' in target) return undefined
    return buildFields(mergeDefinition(target, defs), defs, base, new Set([...visited, refName]))
  }
  if (ref) return undefined
  if ('enum' in node) return undefined
  if (!asNode(node.properties)) return undefined
  return buildFields(mergeDefinition(node, defs), defs, base, visited)
}

/**
 * Converts a DataStructure version's JSON-Schema `model` into the editor's field tree, named after
 * the data structure (schema `title`). When the resolution yields a root class (wrapper or named
 * definition), that class is the tree's single `$` node — a normal mappable object port
 * representing the whole record — with the class's fields nested beneath it. When the record is
 * the document root itself (legacy flat root), the properties sit directly in the tree: a `$`
 * node would just repeat the tree name without adding a level that exists in the data. Throws
 * {@link ModelResolutionError} on structurally broken models — callers fall back to the
 * diagram-based tree.
 */
export const modelToSchemaTree = (model: Record<string, unknown>, fallbackName: string): SchemaTree => {
  const root = model as SchemaNode
  const name = asString(root.title) ?? fallbackName
  // An enumeration-only structure carries no mappable record fields.
  if ('enum' in root) return { name, fields: [] }

  const defs = definitionsOf(root)
  const { className, definition } = resolveRoot(root, defs)
  if (className === null) {
    // Document-root properties carry only the schema-declared requiredness.
    return { name, fields: buildFields(definition, defs, '$', new Set()) }
  }

  const children = buildFields(definition, defs, '$', new Set([className]))
  // The record node is marked required only when a direct child is required: requiredFieldPaths
  // recurses only into required containers, so a deeper required field under an optional container
  // must not force a whole-record mapping, and a record of only optional fields demands nothing.
  const hasRequiredChild = children.some(child => child.required)
  return { name, fields: [field('$', className, 'object', hasRequiredChild, children)] }
}
