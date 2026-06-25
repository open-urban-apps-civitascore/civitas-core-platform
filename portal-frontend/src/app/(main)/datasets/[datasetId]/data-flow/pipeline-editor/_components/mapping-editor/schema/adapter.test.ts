import { describe, expect, it } from 'vitest'

import type { UMLDiagram } from '@/components/uml-modeler/types/diagram'

import { umlDiagramToSchemaTree } from './adapter'

/** Minimal class node for the diagram fixtures below. */
const classNode = (id: string, name: string, attributes: { name: string; type?: string; isId?: boolean }[]) => ({
  id,
  data: { element: { id, name, type: 'class', attributes: attributes.map(a => ({ type: 'String', ...a })) } },
})

/** Structural edge. Composition/aggregation carry the part's role on the source end; association on the target end. */
const edge = (
  type: 'composition' | 'aggregation' | 'association',
  source: string,
  target: string,
  roles: { sourceRole?: string; targetRole?: string } = {},
) => ({
  source,
  target,
  data: { relationship: { type, source, target, ...roles } },
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
