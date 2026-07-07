import {
  assignDefKeys,
  assignRootPropertyNames,
  isAttributeRequired,
} from '@/components/uml-modeler/services/jsonSchemaExportService'
import {
  classifyStructuralEdge,
  collectContainedIds,
  INHERITANCE_RELATIONS,
  isManyMultiplicity,
  selectRootElements,
} from '@/components/uml-modeler/services/umlContainment'
import type { UMLDiagram } from '@/components/uml-modeler/types/diagram'
import type { UMLAttribute, UMLElement, UMLType } from '@/components/uml-modeler/types/uml'
import { hasAttributes } from '@/components/uml-modeler/types/uml'

import type { FieldNode, FieldType, GeometryType, SchemaTree } from '../_types'
import { field, GEOMETRY, portTypeFor } from '../_types'

export { GEOMETRY }

export const PRIMITIVE: Record<string, FieldType> = {
  String: 'str',
  Character: 'str',
  Uuid: 'str',
  Integer: 'int',
  Long: 'int',
  Short: 'int',
  Byte: 'int',
  Float: 'float',
  Double: 'float',
  Boolean: 'bool',
  Date: 'date',
}

/** Type guard: is the given UML type name one of the concrete geometry types? */
const isGeometry = (type: string): type is GeometryType => (GEOMETRY as Set<string>).has(type)

const lowerFirst = (value: string): string => value.charAt(0).toLowerCase() + value.slice(1)

// Geometries map to their concrete type name (Point, Polygon, …) so Point vs Polygon
// mismatches are caught by exact-type matching; other primitives map via PRIMITIVE.
const scalarType = (type: UMLType): FieldType => {
  if (typeof type === 'string') return isGeometry(type) ? type : (PRIMITIVE[type] ?? 'str')
  return 'str'
}

interface DiagramIndex {
  byKey: Map<string, UMLElement>
  byName: Map<string, UMLElement>
  outgoing: Map<string, { target: UMLElement; name: string; many: boolean }[]>
  /** elementId → inheritance/realization parent elements, in edge declaration order. */
  parents: Map<string, UMLElement[]>
}

const indexDiagram = (diagram: UMLDiagram): DiagramIndex => {
  const byKey = new Map<string, UMLElement>()
  const byName = new Map<string, UMLElement>()
  for (const node of diagram.nodes ?? []) {
    const el = node.data?.element
    if (!el) continue
    byKey.set(node.id, el)
    byKey.set(el.id, el)
    byName.set(el.name, el)
  }

  const outgoing = new Map<string, { target: UMLElement; name: string; many: boolean }[]>()
  const parents = new Map<string, UMLElement[]>()
  for (const edge of diagram.edges ?? []) {
    const rel = edge.data?.relationship
    if (!rel) continue

    const containment = classifyStructuralEdge(rel)
    if (containment) {
      const container = byKey.get(containment.containerId)
      const part = byKey.get(containment.partId)
      if (!container || !part) continue

      const name = containment.role || rel.name || lowerFirst(part.name)
      const list = outgoing.get(container.id) ?? []
      list.push({ target: part, name, many: containment.isMany })
      outgoing.set(container.id, list)
      continue
    }

    if (INHERITANCE_RELATIONS.has(rel.type)) {
      const sourceEl = byKey.get(rel.source)
      const targetEl = byKey.get(rel.target)
      if (sourceEl && targetEl) {
        const list = parents.get(sourceEl.id) ?? []
        list.push(targetEl)
        parents.set(sourceEl.id, list)
      }
    }
  }

  return { byKey, byName, outgoing, parents }
}

const resolveRef = (type: UMLType, index: DiagramIndex): UMLElement | null => {
  if (typeof type === 'string') return null
  return index.byKey.get(type.id) ?? index.byName.get(type.name) ?? null
}

type StructuralChild = { target: UMLElement; name: string; many: boolean }

/**
 * Merges values inherited transitively from an element's inheritance/realization parents with the
 * element's own values, keyed by `name`. Insertion order fixes each name's slot: parents contribute
 * first (so a name keeps its inherited position) and the first parent wins among siblings, while the
 * element's own value is set last and overrides any inherited one in place. The visited guard breaks
 * inheritance cycles.
 *
 * Parent members are flattened directly into the subclass's field tree.
 */
const mergeInherited = <T>(
  el: UMLElement,
  index: DiagramIndex,
  visited: Set<string>,
  ownEntries: (el: UMLElement) => [string, T][],
): Map<string, T> => {
  const byName = new Map<string, T>()
  for (const parent of index.parents.get(el.id) ?? []) {
    if (visited.has(parent.id)) continue
    const parentVisited = new Set([...visited, el.id, parent.id])
    for (const [name, value] of mergeInherited(parent, index, parentVisited, ownEntries)) {
      if (!byName.has(name)) byName.set(name, value)
    }
  }
  for (const [name, value] of ownEntries(el)) byName.set(name, value)
  return byName
}

const attributeEntries = (el: UMLElement): [string, UMLAttribute][] =>
  hasAttributes(el) ? el.attributes.map(attr => [attr.name, attr]) : []

const buildFields = (el: UMLElement, base: string, index: DiagramIndex, visited: Set<string>): FieldNode[] => {
  const fields: FieldNode[] = []
  const childEntries = (e: UMLElement): [string, StructuralChild][] =>
    (index.outgoing.get(e.id) ?? []).map(child => [child.name, child])

  for (const attr of mergeInherited(el, index, visited, attributeEntries).values()) {
    const path = `${base}.${attr.name}`
    const ref = resolveRef(attr.type, index)
    const required = isAttributeRequired(attr)
    if (ref && hasAttributes(ref) && !visited.has(ref.id)) {
      const type: FieldType = isManyMultiplicity(attr.multiplicity) ? 'array' : 'object'
      fields.push({
        path,
        name: attr.name,
        type,
        portType: portTypeFor(type),
        required,
        children: buildFields(ref, path + (type === 'array' ? '[]' : ''), index, new Set([...visited, el.id])),
      })
    } else {
      const type = scalarType(attr.type)
      fields.push({ path, name: attr.name, type, portType: portTypeFor(type), required })
    }
  }

  for (const rel of mergeInherited(el, index, visited, childEntries).values()) {
    if (visited.has(rel.target.id)) continue
    const path = `${base}.${rel.name}`
    const type: FieldType = rel.many ? 'array' : 'object'
    fields.push({
      path,
      name: rel.name,
      type,
      portType: portTypeFor(type),
      // Relationship lower bound is not threaded here; treat nested relations as optional so they
      // don't force a mapping (scalar attribute requiredness is what matters in practice).
      required: false,
      children: buildFields(rel.target, path + (type === 'array' ? '[]' : ''), index, new Set([...visited, el.id])),
    })
  }

  return fields
}

/**
 * Converts a datastructure version's `styles` (UML diagram) into the editor's field tree. A
 * single-root diagram anchors the class's fields directly at `$` (the runtime record is the class
 * itself); a diagram with several unconnected trees mirrors the generated schema, whose document
 * root holds one property per root class — each root becomes a node at `$.<property name>`, named
 * by the same collision-safe assignment the schema export uses, so the fallback tree's paths match
 * the persisted model's properties. Enumeration roots are scalar values at runtime and render as
 * string leaves.
 */
export const umlDiagramToSchemaTree = (diagram: UMLDiagram | null | undefined, fallbackName: string): SchemaTree => {
  if (!diagram) return { name: fallbackName, fields: [] }
  const index = indexDiagram(diagram)
  const elements = (diagram.nodes ?? []).map(n => n.data?.element).filter((e): e is UMLElement => !!e)
  if (elements.length === 0) return { name: fallbackName, fields: [] }
  const roots = selectRootElements(elements, collectContainedIds(diagram))

  if (roots.length === 1) {
    const root = roots[0]
    return { name: root.name || fallbackName, fields: buildFields(root, '$', index, new Set([root.id])) }
  }

  const propertyNames = assignRootPropertyNames(roots, assignDefKeys(elements))
  const fields: FieldNode[] = roots.map(root => {
    const name = propertyNames.get(root.id) as string
    const path = `$.${name}`
    if (root.type === 'enumeration') return field(path, name, 'str', false)
    return field(path, name, 'object', false, buildFields(root, path, index, new Set([root.id])))
  })
  return { name: fallbackName, fields }
}
