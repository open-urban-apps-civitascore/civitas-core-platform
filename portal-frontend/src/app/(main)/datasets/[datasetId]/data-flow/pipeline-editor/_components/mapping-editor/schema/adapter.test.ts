import { describe, expect, it } from 'vitest'

import type { UMLDiagram } from '@/components/uml-modeler/types/diagram'

import { umlDiagramToSchemaTree } from './adapter'
import { requiredFieldPaths } from './fieldTree'

/** Minimal class node for the diagram fixtures below. */
const classNode = (
  id: string,
  name: string,
  attributes: { name: string; type?: string; isId?: boolean; multiplicity?: string }[],
) => ({
  id,
  data: { element: { id, name, type: 'class', attributes: attributes.map(a => ({ type: 'String', ...a })) } },
})

/** Structural edge. Composition/aggregation carry the part's role/multiplicity on the source end; association on the target end. */
const edge = (
  type: 'composition' | 'aggregation' | 'association',
  source: string,
  target: string,
  roles: { sourceRole?: string; targetRole?: string; sourceMultiplicity?: string; targetMultiplicity?: string } = {},
) => ({
  source,
  target,
  data: { relationship: { type, source, target, ...roles } },
})

describe('umlDiagramToSchemaTree — required derivation', () => {
  const diagram = {
    nodes: [
      classNode('t', 'Thing', [
        { name: 'id', isId: true },
        { name: 'name', multiplicity: '1' },
        { name: 'nick', multiplicity: '0..1' },
        { name: 'tags', multiplicity: '1..*' },
        { name: 'note' },
      ]),
    ],
    edges: [],
  } as unknown as UMLDiagram

  it('marks fields required from {id} and multiplicity lower bound', () => {
    const tree = umlDiagramToSchemaTree(diagram, 'thing')
    const required = (name: string) => tree.fields.find(f => f.name === name)?.required
    expect(required('id')).toBe(true) // {id}
    expect(required('name')).toBe(true) // 1
    expect(required('nick')).toBe(false) // 0..1 optional
    expect(required('tags')).toBe(true) // 1..*
    expect(required('note')).toBe(true) // unset = single required
  })

  it('requiredFieldPaths returns only the required field paths', () => {
    expect(requiredFieldPaths(umlDiagramToSchemaTree(diagram, 'thing'))).toEqual([
      '$.id',
      '$.name',
      '$.tags',
      '$.note',
    ])
  })
})

describe('umlDiagramToSchemaTree', () => {
  it('roots at the composition container (diamond/target) and nests the part', () => {
    const diagram = {
      nodes: [
        classNode('thing-id', 'Thing', [{ name: 'name' }, { name: 'id', isId: true }]),
        classNode('props-id', 'properties', [{ name: 'reference' }, { name: 'source' }]),
      ],
      edges: [edge('composition', 'props-id', 'thing-id', { sourceRole: 'properties' })],
    } as unknown as UMLDiagram

    const tree = umlDiagramToSchemaTree(diagram, 'thing')

    expect(tree.name).toBe('Thing')
    expect(tree.fields.map(f => f.name)).toEqual(['name', 'id', 'properties'])
    const nested = tree.fields.find(f => f.name === 'properties')
    expect(nested?.type).toBe('object')
    expect(nested?.children?.map(c => c.name)).toEqual(['reference', 'source'])
    expect(nested?.children?.map(c => c.path)).toEqual(['$.properties.reference', '$.properties.source'])
  })

  it('roots at the aggregation container (diamond/target) and nests the part', () => {
    const diagram = {
      nodes: [classNode('whole-id', 'Whole', [{ name: 'label' }]), classNode('part-id', 'Part', [{ name: 'value' }])],
      edges: [edge('aggregation', 'part-id', 'whole-id', { sourceRole: 'parts' })],
    } as unknown as UMLDiagram

    const tree = umlDiagramToSchemaTree(diagram, 'whole')

    expect(tree.name).toBe('Whole')
    const nested = tree.fields.find(f => f.name === 'parts')
    expect(nested?.type).toBe('object')
    expect(nested?.children?.map(c => c.name)).toEqual(['value'])
  })

  it('keeps the drawn direction for association (source is root, target nested)', () => {
    const diagram = {
      nodes: [
        classNode('order-id', 'Order', [{ name: 'orderNo' }]),
        classNode('cust-id', 'Customer', [{ name: 'email' }]),
      ],
      edges: [edge('association', 'order-id', 'cust-id', { targetRole: 'customer' })],
    } as unknown as UMLDiagram

    const tree = umlDiagramToSchemaTree(diagram, 'order')

    expect(tree.name).toBe('Order')
    const nested = tree.fields.find(f => f.name === 'customer')
    expect(nested?.type).toBe('object')
    expect(nested?.children?.map(c => c.name)).toEqual(['email'])
  })

  it('reads the part multiplicity from the source end for composition (* -> array)', () => {
    const diagram = {
      nodes: [classNode('thing-id', 'Thing', [{ name: 'id' }]), classNode('r-id', 'Reading', [{ name: 'value' }])],
      edges: [edge('composition', 'r-id', 'thing-id', { sourceRole: 'readings', sourceMultiplicity: '*' })],
    } as unknown as UMLDiagram

    const nested = umlDiagramToSchemaTree(diagram, 'thing').fields.find(f => f.name === 'readings')
    expect(nested?.type).toBe('array')
  })

  it('reads the multiplicity from the target end for association (* -> array)', () => {
    const diagram = {
      nodes: [classNode('order-id', 'Order', [{ name: 'orderNo' }]), classNode('item-id', 'Item', [{ name: 'sku' }])],
      edges: [edge('association', 'order-id', 'item-id', { targetRole: 'items', targetMultiplicity: '*' })],
    } as unknown as UMLDiagram

    const nested = umlDiagramToSchemaTree(diagram, 'order').fields.find(f => f.name === 'items')
    expect(nested?.type).toBe('array')
  })

  it('never roots on an embedded part even when fallbackName matches it', () => {
    const diagram = {
      nodes: [
        classNode('thing-id', 'Thing', [{ name: 'name' }]),
        classNode('props-id', 'properties', [{ name: 'reference' }]),
      ],
      edges: [edge('composition', 'props-id', 'thing-id', { sourceRole: 'properties' })],
    } as unknown as UMLDiagram

    expect(umlDiagramToSchemaTree(diagram, 'properties').name).toBe('Thing')
  })

  it('prefers a name-matching element among structural roots', () => {
    const diagram = {
      nodes: [classNode('a', 'Alpha', [{ name: 'a1' }]), classNode('b', 'Beta', [{ name: 'b1' }])],
      edges: [],
    } as unknown as UMLDiagram

    expect(umlDiagramToSchemaTree(diagram, 'beta').name).toBe('Beta')
  })

  it('returns an empty tree for a null diagram', () => {
    expect(umlDiagramToSchemaTree(null, 'fallback')).toEqual({ name: 'fallback', fields: [] })
  })
})
