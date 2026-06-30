import type { PortType } from '@/components/node-editor/types'
import type { UMLDiagram } from '@/components/uml-modeler/types/diagram'
import type { UMLClass, UMLElement, UMLRelationship, UMLType } from '@/components/uml-modeler/types/uml'

import type { FieldNode, FieldType, GeometryType, SchemaTree } from '../_types'

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

export const GEOMETRY = new Set<GeometryType>([
  'Point',
  'LineString',
  'Polygon',
  'MultiPoint',
  'MultiLineString',
  'MultiPolygon',
  'GeometryCollection',
])

/** Type guard: is the given UML type name one of the concrete geometry types? */
const isGeometry = (type: string): type is GeometryType => (GEOMETRY as Set<string>).has(type)

const STRUCTURAL = new Set<UMLRelationship['type']>(['association', 'aggregation', 'composition'])

const portTypeFor = (type: FieldType): PortType =>
  type === 'array' ? 'array' : type === 'object' ? 'object' : isGeometry(type) ? 'geometry' : 'scalar'

const isMany = (multiplicity?: string): boolean => !!multiplicity && multiplicity.includes('*')

/**
 * Whether a multiplicity makes the field required (lower bound >= 1). Mirrors the JSON-Schema export:
 * an unset multiplicity is a single required value; {@code 0..1}/{@code 0..*}/{@code *} are optional.
 */
const requiredMultiplicity = (multiplicity?: string): boolean => {
  if (!multiplicity) return true
  const trimmed = multiplicity.trim()
  if (trimmed === '*') return false
  const lower = trimmed.includes('..') ? trimmed.split('..')[0].trim() : trimmed
  return lower !== '0' && lower !== '*'
}

const hasAttributes = (el: UMLElement): el is UMLClass => el.type === 'class' || el.type === 'abstractClass'

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
  incoming: Set<string>
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
  const incoming = new Set<string>()
  for (const edge of diagram.edges ?? []) {
    const rel = edge.data?.relationship
    if (rel && !STRUCTURAL.has(rel.type)) continue
    const sourceEl = byKey.get(rel?.source ?? edge.source)
    const targetEl = byKey.get(rel?.target ?? edge.target)
    if (!sourceEl || !targetEl) continue

    // The composition/aggregation diamond (= the container) is drawn at the edge target, so the
    // target contains the source. Association has no diamond and keeps its drawn direction.
    const isContainerAtTarget = rel?.type === 'composition' || rel?.type === 'aggregation'
    const container = isContainerAtTarget ? targetEl : sourceEl
    const part = isContainerAtTarget ? sourceEl : targetEl
    const role = isContainerAtTarget ? rel?.sourceRole : rel?.targetRole
    const multiplicity = isContainerAtTarget ? rel?.sourceMultiplicity : rel?.targetMultiplicity

    const name = role || rel?.name || lowerFirst(part.name)
    const list = outgoing.get(container.id) ?? []
    list.push({ target: part, name, many: isMany(multiplicity) })
    outgoing.set(container.id, list)
    incoming.add(part.id)
  }

  return { byKey, byName, outgoing, incoming }
}

const resolveRef = (type: UMLType, index: DiagramIndex): UMLElement | null => {
  if (typeof type === 'string') return null
  return index.byKey.get(type.id) ?? index.byName.get(type.name) ?? null
}

const buildFields = (el: UMLElement, base: string, index: DiagramIndex, visited: Set<string>): FieldNode[] => {
  const fields: FieldNode[] = []

  if (hasAttributes(el)) {
    for (const attr of el.attributes) {
      const path = `${base}.${attr.name}`
      const ref = resolveRef(attr.type, index)
      const required = !!attr.isId || requiredMultiplicity(attr.multiplicity)
      if (ref && hasAttributes(ref) && !visited.has(ref.id)) {
        const type: FieldType = isMany(attr.multiplicity) ? 'array' : 'object'
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
  }

  for (const rel of index.outgoing.get(el.id) ?? []) {
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

const pickRoot = (diagram: UMLDiagram, index: DiagramIndex, fallbackName: string): UMLElement | null => {
  const elements = (diagram.nodes ?? []).map(n => n.data?.element).filter((e): e is UMLElement => !!e)
  if (elements.length === 0) return null
  const roots = elements.filter(e => !index.incoming.has(e.id))
  const pool = roots.length > 0 ? roots : elements
  return pool.find(e => e.name.toLowerCase() === fallbackName.toLowerCase()) ?? pool[0]
}

/** Converts a datastructure version's `styles` (UML diagram) into the editor's field tree. */
export const umlDiagramToSchemaTree = (diagram: UMLDiagram | null | undefined, fallbackName: string): SchemaTree => {
  if (!diagram) return { name: fallbackName, fields: [] }
  const index = indexDiagram(diagram)
  const root = pickRoot(diagram, index, fallbackName)
  if (!root) return { name: fallbackName, fields: [] }
  return { name: root.name || fallbackName, fields: buildFields(root, '$', index, new Set([root.id])) }
}
