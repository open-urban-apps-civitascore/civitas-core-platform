import type { PortType } from '@/components/node-editor/types'

import type { FieldNode, FieldType, GeometryType, SchemaTree } from '../_types'
import { GEOMETRY } from './adapter'

/**
 * Builds the mapping editor's field tree from a DataStructure version's persisted JSON-Schema
 * `model` — the same artifact the engine adapters interpret at deploy time.
 *
 * The resolution semantics mirror `DataStructureSchema` (config-adapter-api) so the editor shows
 * exactly the record shape the backend derives: a wrapper root (a root whose single property is a
 * bare local `$ref`) is resolved through to the referenced class, `$defs` shadows legacy
 * `definitions`, `allOf` branches and local `$ref` parents merge parents-first with own properties
 * overriding in place, and external (http/https) `$ref` parents are skipped while a relative `$ref`
 * is rejected. The runtime records are entity-shaped (the wrapper never appears in the data), so
 * all field paths stay anchored at the class: the resolved root class is the tree's single `$`
 * node, the data structure's name (the schema `title`) is the tree name.
 */

type SchemaNode = Record<string, unknown>

const GEOJSON_REF = /geojson\.org\/schema\/([A-Za-z]+)\.json$/

const asNode = (value: unknown): SchemaNode | null =>
  typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as SchemaNode) : null

const asString = (value: unknown): string | null => (typeof value === 'string' ? value : null)

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
      throw new Error(`Unsupported non-local $ref '${ref}' — only #/$defs/ and #/definitions/ refs are resolvable`)
    }
    if (!visitedRefs.has(parentName)) {
      visitedRefs.add(parentName)
      const parent = defs[parentName]
      if (parent) collectInto(parent, defs, into, visitedRefs)
    }
  }

  const properties = asNode(node.properties)
  if (properties) {
    for (const [name, property] of Object.entries(properties)) {
      const propertyNode = asNode(property)
      if (propertyNode) into.properties[name] = propertyNode
    }
  }
  for (const name of Array.isArray(node.required) ? node.required : []) {
    if (typeof name === 'string') into.required.add(name)
  }
}

/**
 * Wrapper-root detection, bit-compatible with `DataStructureSchema.wrappedRootDefinition`: no
 * `allOf`, exactly one property whose value is a bare `$ref` (no siblings) to a local definition.
 * A wrapper whose target is missing is a broken schema and rejected.
 */
const wrappedRootName = (root: SchemaNode, defs: Record<string, SchemaNode>): string | null => {
  if (Array.isArray(root.allOf) && root.allOf.length > 0) return null
  const properties = asNode(root.properties)
  if (!properties) return null
  const values = Object.values(properties)
  if (values.length !== 1) return null
  const property = asNode(values[0])
  if (!property || Object.keys(property).length !== 1) return null
  const name = localDefName(asString(property.$ref))
  if (!name) return null
  if (!defs[name] || Object.keys(defs[name]).length === 0) {
    throw new Error(`Wrapper root references missing definition '${name}'`)
  }
  return name
}

const hasOwnContent = (node: SchemaNode): boolean => {
  const properties = asNode(node.properties)
  return (properties !== null && Object.keys(properties).length > 0) || Array.isArray(node.allOf)
}

interface RootResolution {
  className: string
  definition: ResolvedDefinition
}

/** Mirrors `DataStructureSchema.resolveDefinition`'s precedence: wrapper → root itself → named definition. */
const resolveRoot = (root: SchemaNode, defs: Record<string, SchemaNode>, fallbackName: string): RootResolution => {
  const wrapperName = wrappedRootName(root, defs)
  if (wrapperName) {
    return { className: wrapperName, definition: mergeDefinition(defs[wrapperName], defs) }
  }

  if (hasOwnContent(root)) {
    const merged = mergeDefinition(root, defs)
    if (Object.keys(merged.properties).length > 0) {
      return { className: asString(root.title) ?? fallbackName, definition: merged }
    }
  }

  const names = Object.keys(defs)
  const title = asString(root.title)
  const selected =
    (names.length === 1 ? names[0] : null) ??
    (title && defs[title] ? title : null) ??
    (names.filter(name => hasOwnContent(defs[name])).length === 1
      ? names.find(name => hasOwnContent(defs[name]))
      : null)
  if (!selected) {
    throw new Error(
      names.length === 0
        ? 'Model has no resolvable definition'
        : `Model has ${names.length} definitions and none matches the title`,
    )
  }
  return { className: selected, definition: mergeDefinition(defs[selected], defs) }
}

const portTypeFor = (type: FieldType): PortType =>
  type === 'array'
    ? 'array'
    : type === 'object'
      ? 'object'
      : (GEOMETRY as Set<string>).has(type)
        ? 'geometry'
        : 'scalar'

const scalarTypeOf = (node: SchemaNode): FieldType => {
  const type = asString(node.type)
  if (type === 'integer') return 'int'
  if (type === 'number') return 'float'
  if (type === 'boolean') return 'bool'
  if (type === 'string') {
    const format = asString(node.format)
    return format === 'date-time' || format === 'date' ? 'date' : 'str'
  }
  return 'str'
}

const geometryTypeOf = (ref: string): GeometryType | null => {
  const match = GEOJSON_REF.exec(ref)
  return match && (GEOMETRY as Set<string>).has(match[1]) ? (match[1] as GeometryType) : null
}

const field = (path: string, name: string, type: FieldType, required: boolean, children?: FieldNode[]): FieldNode => ({
  path,
  name,
  type,
  portType: portTypeFor(type),
  ...(required ? { required } : {}),
  ...(children && children.length > 0 ? { children } : {}),
})

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

/** Child fields of a `$ref`/inline-object node; enums and cycles yield no children. */
const childrenOf = (
  node: SchemaNode,
  defs: Record<string, SchemaNode>,
  base: string,
  visited: Set<string>,
): FieldNode[] | undefined => {
  const refName = localDefName(asString(node.$ref))
  if (refName) {
    if (visited.has(refName)) return undefined
    const target = defs[refName]
    if (!target || 'enum' in target) return undefined
    return buildFields(mergeDefinition(target, defs), defs, base, new Set([...visited, refName]))
  }
  if (asString(node.$ref)) return undefined
  if ('enum' in node) return undefined
  if (!asNode(node.properties)) return undefined
  return buildFields(mergeDefinition(node, defs), defs, base, visited)
}

const hasRequiredDescendant = (fields: FieldNode[]): boolean =>
  fields.some(node => node.required || (node.children ? hasRequiredDescendant(node.children) : false))

/**
 * Converts a DataStructure version's JSON-Schema `model` into the editor's field tree: the tree is
 * named after the data structure (schema `title`), its single `$` node is the resolved root class
 * (a normal mappable object port representing the whole record), and the class's fields nest
 * beneath it. Throws on structurally broken models (missing wrapper target, non-local `$ref`,
 * unresolvable definitions) — callers fall back to the diagram-based tree.
 */
export const modelToSchemaTree = (
  model: Record<string, unknown> | null | undefined,
  fallbackName: string,
): SchemaTree => {
  const root = asNode(model)
  if (!root) return { name: fallbackName, fields: [] }

  const name = asString(root.title) ?? fallbackName
  if ('enum' in root) return { name, fields: [] }

  const defs = definitionsOf(root)
  const { className, definition } = resolveRoot(root, defs, fallbackName)
  const children = buildFields(definition, defs, '$', new Set([className]))

  // The record node is marked required only when it has required descendants: requiredFieldPaths
  // treats a required container without required children as itself mandatory, which would demand
  // a whole-record mapping for structures made of optional fields.
  return { name, fields: [field('$', className, 'object', hasRequiredDescendant(children), children)] }
}
