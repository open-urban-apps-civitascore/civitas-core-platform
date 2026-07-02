import { describe, expect, it } from 'vitest'

import { exportToJsonSchema } from '@/components/uml-modeler/services/jsonSchemaExportService'
import type { UMLDiagram } from '@/components/uml-modeler/types/diagram'

import { umlDiagramToSchemaTree } from './adapter'

const cls = (id: string, name: string, attrs: { id: string; name: string; type?: string }[]) => ({
  id: `node-${id}`,
  type: 'class' as const,
  position: { x: 0, y: 0 },
  data: {
    element: {
      id,
      name,
      type: 'class' as const,
      attributes: attrs.map(a => ({ visibility: 'public' as const, type: 'String', ...a })),
      operations: [],
    },
    label: name,
  },
})

const rel = (id: string, type: string, source: string, target: string, extra: Record<string, unknown> = {}) => ({
  id: `edge-${id}`,
  type,
  source: `node-${source}`,
  target: `node-${target}`,
  data: {
    relationship: { id: `rel-${id}`, type, source, target, ...extra },
    label: '',
    isSelected: false,
    isDirty: false,
  },
})

const diagram = (name: string, nodes: unknown[], edges: unknown[]): UMLDiagram =>
  ({ id: 'd', name, nodes, edges, lastModified: new Date('2026-01-01'), isDirty: false }) as unknown as UMLDiagram

const fixtures: { label: string; diagram: UMLDiagram }[] = [
  {
    label: 'pure inheritance (diagram name = parent)',
    diagram: diagram(
      'Animal',
      [cls('animal', 'Animal', [{ id: 'a1', name: 'name' }]), cls('dog', 'Dog', [{ id: 'a2', name: 'breed' }])],
      [rel('1', 'inheritance', 'dog', 'animal')],
    ),
  },
  {
    label: 'realization (diagram name = interface)',
    diagram: diagram(
      'IFace',
      [cls('iface', 'IFace', [{ id: 'a1', name: 'y' }]), cls('impl', 'Impl', [{ id: 'a2', name: 'x' }])],
      [rel('1', 'realization', 'impl', 'iface')],
    ),
  },
  {
    label: 'multi-level inheritance',
    diagram: diagram(
      'Base',
      [
        cls('base', 'Base', [{ id: 'a1', name: 'a' }]),
        cls('mid', 'Mid', [{ id: 'a2', name: 'b' }]),
        cls('leaf', 'Leaf', [{ id: 'a3', name: 'c' }]),
      ],
      [rel('1', 'inheritance', 'mid', 'base'), rel('2', 'inheritance', 'leaf', 'mid')],
    ),
  },
  {
    label: 'multiple inheritance',
    diagram: diagram(
      'P1',
      [
        cls('p1', 'P1', [{ id: 'a1', name: 'x' }]),
        cls('p2', 'P2', [{ id: 'a2', name: 'y' }]),
        cls('leaf', 'Leaf', [{ id: 'a3', name: 'own' }]),
      ],
      [rel('1', 'inheritance', 'leaf', 'p1'), rel('2', 'inheritance', 'leaf', 'p2')],
    ),
  },
  {
    label: 'composition',
    diagram: diagram(
      'Thing',
      [cls('thing', 'Thing', [{ id: 'a1', name: 'id' }]), cls('reading', 'Reading', [{ id: 'a2', name: 'value' }])],
      [rel('1', 'composition', 'reading', 'thing', { sourceRole: 'readings', sourceMultiplicity: '*' })],
    ),
  },
  {
    label: 'composition without role but with relationship name',
    diagram: diagram(
      'Sensor',
      [cls('sensor', 'Sensor', [{ id: 'a1', name: 'id' }]), cls('reading', 'Reading', [{ id: 'a2', name: 'value' }])],
      [rel('1', 'composition', 'reading', 'sensor', { name: 'measurements', sourceMultiplicity: '*' })],
    ),
  },
  {
    label: 'inheritance + composition on the subclass',
    diagram: diagram(
      'Vehicle',
      [
        cls('vehicle', 'Vehicle', [{ id: 'a1', name: 'vin' }]),
        cls('car', 'Car', [{ id: 'a2', name: 'doors' }]),
        cls('engine', 'Engine', [{ id: 'a3', name: 'power' }]),
      ],
      [rel('1', 'inheritance', 'car', 'vehicle'), rel('2', 'composition', 'engine', 'car', { sourceRole: 'engine' })],
    ),
  },
  {
    label: 'inheritance + composition on the parent',
    diagram: diagram(
      'Vehicle',
      [
        cls('vehicle', 'Vehicle', [{ id: 'a1', name: 'vin' }]),
        cls('car', 'Car', [{ id: 'a2', name: 'doors' }]),
        cls('engine', 'Engine', [{ id: 'a3', name: 'power' }]),
      ],
      [
        rel('1', 'inheritance', 'car', 'vehicle'),
        rel('2', 'composition', 'engine', 'vehicle', { sourceRole: 'engine' }),
      ],
    ),
  },
]

type JsonSchema = {
  title?: string
  properties?: Record<string, unknown>
  allOf?: JsonSchema[]
  $ref?: string
  $defs?: Record<string, JsonSchema>
}

/** Top-level property names of a generated schema node, following allOf branches and local $refs. */
const resolveTopLevelProperties = (node: JsonSchema | undefined, defs: Record<string, JsonSchema>): string[] => {
  if (!node) return []
  const names: string[] = []
  const push = (name: string) => {
    if (!names.includes(name)) names.push(name)
  }
  for (const branch of node.allOf ?? []) resolveTopLevelProperties(branch, defs).forEach(push)
  if (node.$ref?.startsWith('#/$defs/')) {
    resolveTopLevelProperties(defs[node.$ref.slice('#/$defs/'.length)], defs).forEach(push)
  }
  Object.keys(node.properties ?? {}).forEach(push)
  return names
}

/** The single root class the virtual document root references, i.e. the `$defs` key of its one $ref property. */
const rootClassOf = (schema: JsonSchema): string | undefined => {
  const ref = Object.values(schema.properties ?? {})
    .map(value => (value as JsonSchema).$ref)
    .find((r): r is string => !!r?.startsWith('#/$defs/'))
  return ref?.slice('#/$defs/'.length)
}

describe('root-selection consistency (adapter vs generator)', () => {
  // The generator's document root is the (virtual) data structure, titled after the diagram; it is
  // not itself a class. The editor adapter still roots on the single root class, which the generator
  // references via its one $ref property — so the adapter root equals that referenced class.
  it.each(fixtures)('agrees on the root class for $label', ({ diagram }) => {
    const adapterRoot = umlDiagramToSchemaTree(diagram, diagram.name).name
    const generatorRootClass = rootClassOf(exportToJsonSchema(diagram) as JsonSchema)
    expect(adapterRoot).toBe(generatorRootClass)
  })

  it.each(fixtures)('agrees on the root field set for $label', ({ diagram }) => {
    const adapterFields = umlDiagramToSchemaTree(diagram, diagram.name).fields.map(f => f.name)
    const schema = exportToJsonSchema(diagram) as JsonSchema
    const defs = schema.$defs ?? {}
    const rootClass = rootClassOf(schema)
    // Compare against the referenced root class's fields, not the virtual root (which only holds the
    // single $ref property).
    const generatorFields = resolveTopLevelProperties(rootClass ? defs[rootClass] : undefined, defs)
    expect([...adapterFields].sort()).toEqual([...generatorFields].sort())
  })
})
