import { isAttributeRequired } from '@/components/uml-modeler/services/jsonSchemaExportService'
import type { RootResolutionFailure } from '@/components/uml-modeler/services/umlContainment'
import {
  classifyStructuralEdge,
  INHERITANCE_RELATIONS,
  isManyMultiplicity,
  resolveRootElement,
} from '@/components/uml-modeler/services/umlContainment'
import type { UMLDiagram } from '@/components/uml-modeler/types/diagram'
import type { UMLAttribute, UMLElement, UMLType } from '@/components/uml-modeler/types/uml'
import { hasAttributes } from '@/components/uml-modeler/types/uml'

import type { FieldNode, FieldType, SchemaTree } from '../_types'
import { GEOMETRY, isGeometryType, portTypeFor } from '../_types'

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

const lowerFirst = (value: string): string => value.charAt(0).toLowerCase() + value.slice(1)

// Geometries map to their concrete type name (Point, Polygon, …) so Point vs Polygon
// mismatches are caught by exact-type matching; other primitives map via PRIMITIVE.
const scalarType = (type: UMLType): FieldType => {
  if (typeof type === 'string') return isGeometryType(type) ? type : (PRIMITIVE[type] ?? 'str')
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

export interface DiagramSchemaTree {
  readonly tree: SchemaTree
  /**
   * Why the diagram yields no usable root — the same condition under which the schema export
   * refuses to produce a model. Callers must surface it: the empty tree alone reads as "nothing
   * to map" although the reason is precisely known. `null` for a resolvable (or empty/enum-only)
   * diagram.
   */
  readonly failure: RootResolutionFailure | null
}

/**
 * Converts a datastructure version's `styles` (UML diagram) into the editor's field tree: the
 * single root class anchors its fields directly at `$` (the runtime record is the class itself).
 * A diagram without a unique root yields an empty tree rather than a guessed one, carrying the
 * typed failure; an empty diagram or an enumeration root (a scalar value at runtime) also has no
 * mappable record fields but is not a failure.
 */
export const umlDiagramToSchemaTree = (
  diagram: UMLDiagram | null | undefined,
  fallbackName: string,
): DiagramSchemaTree => {
  if (!diagram) return { tree: { name: fallbackName, fields: [] }, failure: null }
  const resolution = resolveRootElement(diagram)
  if (resolution.kind === 'enum') {
    return { tree: { name: resolution.root.name || fallbackName, fields: [] }, failure: null }
  }
  if (resolution.kind === 'invalid') {
    return { tree: { name: fallbackName, fields: [] }, failure: resolution.failure }
  }
  if (resolution.kind === 'empty') return { tree: { name: fallbackName, fields: [] }, failure: null }
  const root = resolution.root
  const index = indexDiagram(diagram)
  return {
    tree: { name: root.name || fallbackName, fields: buildFields(root, '$', index, new Set([root.id])) },
    failure: null,
  }
}
