import { describe, expect, it } from 'vitest'

import type { UMLDiagram, UMLNode } from '../types/diagram'
import type { UMLElement } from '../types/uml'
import { exportToJsonSchema } from './jsonSchemaExportService'
import { mergeStructureIntoDiagram } from './structureMergeService'

const node = (element: UMLElement, position = { x: 0, y: 0 }): UMLNode =>
  ({ id: `node-${element.id}`, type: element.type, position, data: { element, label: element.name } }) as UMLNode

const diagram = (elements: UMLElement[]): UMLDiagram => ({
  id: 'diagram-1',
  name: 'Open',
  nodes: elements.map((element, index) => node(element, { x: index * 100, y: index * 100 })),
  edges: [],
  lastModified: new Date('2026-01-01'),
  isDirty: false,
})

const clazz = (id: string, name: string, isRoot = false): UMLElement => ({
  id,
  name,
  type: 'class',
  attributes: [{ id: `${id}-a`, name: 'name', type: 'String', multiplicity: '1..1' }],
  operations: [],
  ...(isRoot ? { isRoot: true } : {}),
})

/** What the menu knows about the structure it loads: its name and the version it pins. */
const source = (name: string, version = '1.0.0') => ({
  urn: `urn:core:platform:civitas:datastructure:frost:${name}:0123456789:${version}`,
  name,
})

/** A published structure: a wrapper root over one class, as the generator writes it. */
const published = (rootName: string, extra: string[] = []) => ({
  $id: `urn:core:platform:civitas:datastructure:frost:${rootName}:0123456789`,
  title: rootName,
  type: 'object',
  properties: { root: { $ref: `#/$defs/${rootName}` } },
  $defs: {
    [rootName]: {
      type: 'object',
      title: rootName,
      properties: {
        name: { type: 'string' },
        ...Object.fromEntries(extra.map(name => [name.toLowerCase(), { $ref: `#/$defs/${name}` }])),
      },
      required: ['name'],
    },
    ...Object.fromEntries(
      extra.map(name => [name, { type: 'object', title: name, properties: { value: { type: 'string' } } }]),
    ),
  },
})

describe('mergeStructureIntoDiagram', () => {
  it('takes the loaded root as the root of an empty diagram', () => {
    const result = mergeStructureIntoDiagram(diagram([]), published('Thing'), source('Thing'))

    const root = result.nodes.find(candidate => candidate.data.element.id === result.rootElementId)
    expect(root?.data.element.name).toBe('Thing')
    expect(root?.data.element.isRoot).toBe(true)
  })

  it('keeps the existing root and hangs the loaded structure under it', () => {
    const existing = diagram([clazz('e1', 'Dataset', true)])

    const result = mergeStructureIntoDiagram(existing, published('Thing'), source('Thing'))

    // The flag does not move, so the caller has nothing to set.
    expect(result.rootElementId).toBeNull()
    const loadedRoot = result.nodes.find(candidate => candidate.data.element.name === 'Thing')!
    expect(loadedRoot.data.element.isRoot).toBeUndefined()
    // A composition from the loaded root to the existing one: the export demands that every
    // element is reachable from the root.
    const attachment = result.edges.find(edge => edge.source === loadedRoot.data.element.id)
    expect(attachment?.target).toBe('e1')
    expect(attachment?.data.relationship.sourceMultiplicity).toBe('0..1')
  })

  it('renames a loaded class whose name is taken, instead of overwriting it', () => {
    const existing = diagram([clazz('e1', 'Dataset', true), clazz('e2', 'Thing')])

    const result = mergeStructureIntoDiagram(existing, published('Thing'), source('Thing'))

    expect(result.renamed).toEqual([{ from: 'Thing', to: 'Thing_1' }])
    expect(result.nodes.map(candidate => candidate.data.element.name)).toContain('Thing_1')
    // The class that was there keeps its name and its content.
    expect(existing.nodes[1].data.element.name).toBe('Thing')
  })

  it('renames a class that only normalizes to a taken name', () => {
    // Both forms derive the same Element URN, so the registry would fold them into one member.
    const existing = diagram([clazz('e1', 'Dataset', true), clazz('e2', 'Thing Tree')])

    const result = mergeStructureIntoDiagram(existing, published('ThingTree'), source('ThingTree'))

    expect(result.renamed).toEqual([{ from: 'ThingTree', to: 'ThingTree_1' }])
  })

  it('places the loaded classes clear of what is already drawn', () => {
    const existing = diagram([clazz('e1', 'Dataset', true), clazz('e2', 'Other')])
    const lowest = Math.max(...existing.nodes.map(candidate => candidate.position.y))

    const result = mergeStructureIntoDiagram(existing, published('Thing'), source('Thing'))

    for (const placed of result.nodes) {
      expect(placed.position.y).toBeGreaterThan(lowest)
    }
  })

  it('pins the version it loaded, and does not pin the same one twice', () => {
    const existing = diagram([clazz('e1', 'Dataset', true)])

    const first = mergeStructureIntoDiagram(existing, published('Thing'), source('Thing'))
    const withPin: UMLDiagram = { ...existing, importedStructures: first.importedStructures }
    const again = mergeStructureIntoDiagram(withPin, published('Thing'), source('Thing'))
    const newer = mergeStructureIntoDiagram(withPin, published('Thing'), source('Thing', '1.1.0'))

    expect(first.importedStructures).toEqual([source('Thing')])
    // The same version brings nothing new; a later one is a second fact about the diagram.
    expect(again.importedStructures).toEqual([source('Thing')])
    expect(newer.importedStructures).toEqual([source('Thing'), source('Thing', '1.1.0')])
  })

  it('gives a diagram that exports', () => {
    const existing = diagram([clazz('e1', 'Dataset', true)])

    const result = mergeStructureIntoDiagram(existing, published('Thing', ['Detail']), source('Thing'))
    const merged: UMLDiagram = {
      ...existing,
      nodes: [...existing.nodes, ...result.nodes],
      edges: [...existing.edges, ...result.edges],
    }

    // The point of the subordination: an element the root cannot reach fails the export.
    const schema = exportToJsonSchema(merged)
    expect(schema.$ref).toBe('#/$defs/Dataset')
    expect(Object.keys(schema.$defs as object)).toEqual(['Dataset', 'Thing', 'Detail'])
  })
})
