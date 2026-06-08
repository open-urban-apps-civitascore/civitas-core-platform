import type { PortType } from '@/components/node-editor/types'
import type { UMLDiagram } from '@/components/uml-modeler/types/diagram'
import type { UMLClass, UMLElement, UMLRelationship, UMLType } from '@/components/uml-modeler/types/uml'

import type { FieldNode, FieldType, SchemaTree } from '../_types'

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

export const GEOMETRY = new Set([
  'Point',
  'LineString',
  'Polygon',
  'MultiPoint',
  'MultiLineString',
  'MultiPolygon',
  'GeometryCollection',
])

const STRUCTURAL = new Set<UMLRelationship['type']>(['association', 'aggregation', 'composition'])

const portTypeFor = (type: FieldType): PortType =>
  type === 'array' ? 'array' : type === 'object' || type === 'geo' ? 'object' : 'scalar'

const isMany = (multiplicity?: string): boolean => !!multiplicity && multiplicity.includes('*')

const hasAttributes = (el: UMLElement): el is UMLClass => el.type === 'class' || el.type === 'abstractClass'

const lowerFirst = (value: string): string => value.charAt(0).toLowerCase() + value.slice(1)

const scalarType = (type: UMLType): FieldType => {
  if (typeof type === 'string') return GEOMETRY.has(type) ? 'geo' : (PRIMITIVE[type] ?? 'str')
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
    const name = rel?.targetRole || rel?.name || lowerFirst(targetEl.name)
    const list = outgoing.get(sourceEl.id) ?? []
    list.push({ target: targetEl, name, many: isMany(rel?.targetMultiplicity) })
    outgoing.set(sourceEl.id, list)
    incoming.add(targetEl.id)
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
      if (ref && hasAttributes(ref) && !visited.has(ref.id)) {
        const type: FieldType = isMany(attr.multiplicity) ? 'array' : 'object'
        fields.push({
          path,
          name: attr.name,
          type,
          portType: portTypeFor(type),
          children: buildFields(ref, path + (type === 'array' ? '[]' : ''), index, new Set([...visited, el.id])),
        })
      } else {
        const type = scalarType(attr.type)
        fields.push({ path, name: attr.name, type, portType: portTypeFor(type) })
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
      children: buildFields(rel.target, path + (type === 'array' ? '[]' : ''), index, new Set([...visited, el.id])),
    })
  }

  return fields
}

const pickRoot = (diagram: UMLDiagram, index: DiagramIndex, fallbackName: string): UMLElement | null => {
  const elements = (diagram.nodes ?? []).map(n => n.data?.element).filter((e): e is UMLElement => !!e)
  if (elements.length === 0) return null
  const byNameMatch = elements.find(e => e.name.toLowerCase() === fallbackName.toLowerCase())
  if (byNameMatch) return byNameMatch
  return elements.find(e => !index.incoming.has(e.id)) ?? elements[0]
}

/** Converts a datastructure version's `styles` (UML diagram) into the editor's field tree. */
export const umlDiagramToSchemaTree = (diagram: UMLDiagram | null | undefined, fallbackName: string): SchemaTree => {
  if (!diagram) return { name: fallbackName, fields: [] }
  const index = indexDiagram(diagram)
  const root = pickRoot(diagram, index, fallbackName)
  if (!root) return { name: fallbackName, fields: [] }
  return { name: root.name || fallbackName, fields: buildFields(root, '$', index, new Set([root.id])) }
}
