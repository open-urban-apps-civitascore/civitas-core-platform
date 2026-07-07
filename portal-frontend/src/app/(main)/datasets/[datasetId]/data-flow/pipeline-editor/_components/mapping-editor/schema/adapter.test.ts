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

/** Inheritance/realization edge: source is the subclass, target is the parent. */
const inhEdge = (source: string, target: string, type: 'inheritance' | 'realization' = 'inheritance') => ({
  source,
  target,
  data: { relationship: { type, source, target } },
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
    expect(requiredFieldPaths(umlDiagramToSchemaTree(diagram, 'thing'))).toEqual(['$.id', '$.name', '$.tags', '$.note'])
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

  it('ignores an out-of-scope aggregation edge: the part is not nested', () => {
    const diagram = {
      nodes: [classNode('whole-id', 'Whole', [{ name: 'label' }]), classNode('part-id', 'Part', [{ name: 'value' }])],
      edges: [edge('aggregation', 'part-id', 'whole-id', { sourceRole: 'parts' })],
    } as unknown as UMLDiagram

    const tree = umlDiagramToSchemaTree(diagram, 'whole')

    expect(tree.name).toBe('Whole')
    expect(tree.fields.find(f => f.name === 'parts')).toBeUndefined()
    expect(tree.fields.map(f => f.name)).toEqual(['label'])
  })

  it('ignores an out-of-scope association edge: the target is not nested', () => {
    const diagram = {
      nodes: [
        classNode('order-id', 'Order', [{ name: 'orderNo' }]),
        classNode('cust-id', 'Customer', [{ name: 'email' }]),
      ],
      edges: [edge('association', 'order-id', 'cust-id', { targetRole: 'customer' })],
    } as unknown as UMLDiagram

    const tree = umlDiagramToSchemaTree(diagram, 'order')

    expect(tree.name).toBe('Order')
    expect(tree.fields.find(f => f.name === 'customer')).toBeUndefined()
    expect(tree.fields.map(f => f.name)).toEqual(['orderNo'])
  })

  it('reads the part multiplicity from the source end for composition (* -> array)', () => {
    const diagram = {
      nodes: [classNode('thing-id', 'Thing', [{ name: 'id' }]), classNode('r-id', 'Reading', [{ name: 'value' }])],
      edges: [edge('composition', 'r-id', 'thing-id', { sourceRole: 'readings', sourceMultiplicity: '*' })],
    } as unknown as UMLDiagram

    const nested = umlDiagramToSchemaTree(diagram, 'thing').fields.find(f => f.name === 'readings')
    expect(nested?.type).toBe('array')
  })

  it('ignores an out-of-scope association edge regardless of its multiplicity', () => {
    const diagram = {
      nodes: [classNode('order-id', 'Order', [{ name: 'orderNo' }]), classNode('item-id', 'Item', [{ name: 'sku' }])],
      edges: [edge('association', 'order-id', 'item-id', { targetRole: 'items', targetMultiplicity: '*' })],
    } as unknown as UMLDiagram

    expect(umlDiagramToSchemaTree(diagram, 'order').fields.find(f => f.name === 'items')).toBeUndefined()
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

  it('exposes every unconnected class as its own root object node', () => {
    const diagram = {
      nodes: [classNode('a', 'Alpha', [{ name: 'a1' }]), classNode('b', 'Beta', [{ name: 'b1' }])],
      edges: [],
    } as unknown as UMLDiagram

    const tree = umlDiagramToSchemaTree(diagram, 'MyStructure')
    expect(tree.name).toBe('MyStructure')
    expect(tree.fields.map(f => ({ name: f.name, path: f.path, type: f.type }))).toEqual([
      { name: 'alpha', path: '$.alpha', type: 'object' },
      { name: 'beta', path: '$.beta', type: 'object' },
    ])
    expect(tree.fields[0].children?.map(f => f.path)).toEqual(['$.alpha.a1'])
    expect(tree.fields[1].children?.map(f => f.path)).toEqual(['$.beta.b1'])
  })

  it('returns an empty tree for a null diagram', () => {
    expect(umlDiagramToSchemaTree(null, 'fallback')).toEqual({ name: 'fallback', fields: [] })
  })

  it('roots on the subclass and inlines inherited attributes (inherited first)', () => {
    const diagram = {
      nodes: [classNode('animal', 'Animal', [{ name: 'name' }]), classNode('dog', 'Dog', [{ name: 'breed' }])],
      edges: [inhEdge('dog', 'animal')],
    } as unknown as UMLDiagram

    const tree = umlDiagramToSchemaTree(diagram, 'animal')
    expect(tree.name).toBe('Dog')
    expect(tree.fields.map(f => f.name)).toEqual(['name', 'breed'])
    expect(tree.fields.map(f => f.path)).toEqual(['$.name', '$.breed'])
  })

  it('ignores realization rather than inlining it like inheritance', () => {
    const diagram = {
      nodes: [classNode('iface', 'IFace', [{ name: 'y' }]), classNode('impl', 'Impl', [{ name: 'x' }])],
      edges: [inhEdge('impl', 'iface', 'realization')],
    } as unknown as UMLDiagram

    // The realization edge is dropped, so Impl is not embedded and inherits nothing; the
    // name-matching IFace stays the root with only its own attribute.
    const tree = umlDiagramToSchemaTree(diagram, 'iface')
    expect(tree.name).toBe('IFace')
    expect(tree.fields.map(f => f.name)).toEqual(['y'])
  })

  it('inlines multi-level inheritance (A → B → C) top-down', () => {
    const diagram = {
      nodes: [
        classNode('base', 'Base', [{ name: 'a' }]),
        classNode('mid', 'Mid', [{ name: 'b' }]),
        classNode('leaf', 'Leaf', [{ name: 'c' }]),
      ],
      edges: [inhEdge('mid', 'base'), inhEdge('leaf', 'mid')],
    } as unknown as UMLDiagram

    const tree = umlDiagramToSchemaTree(diagram, 'leaf')
    expect(tree.name).toBe('Leaf')
    expect(tree.fields.map(f => f.name)).toEqual(['a', 'b', 'c'])
  })

  it('inlines multiple inheritance in parent-edge order', () => {
    const diagram = {
      nodes: [
        classNode('p1', 'P1', [{ name: 'x' }]),
        classNode('p2', 'P2', [{ name: 'y' }]),
        classNode('leaf', 'Leaf', [{ name: 'own' }]),
      ],
      edges: [inhEdge('leaf', 'p1'), inhEdge('leaf', 'p2')],
    } as unknown as UMLDiagram

    const tree = umlDiagramToSchemaTree(diagram, 'leaf')
    expect(tree.name).toBe('Leaf')
    expect(tree.fields.map(f => f.name)).toEqual(['x', 'y', 'own'])
  })

  it('lets the subclass override an inherited attribute of the same name (keeping position)', () => {
    const diagram = {
      nodes: [
        classNode('parent', 'Parent', [{ name: 'value', type: 'String' }]),
        classNode('child', 'Child', [{ name: 'value', type: 'Integer' }]),
      ],
      edges: [inhEdge('child', 'parent')],
    } as unknown as UMLDiagram

    const tree = umlDiagramToSchemaTree(diagram, 'child')
    expect(tree.fields.map(f => f.name)).toEqual(['value'])
    expect(tree.fields[0].type).toBe('int')
  })

  it('lets the first parent win an attribute-name collision across parents', () => {
    const diagram = {
      nodes: [
        classNode('p1', 'P1', [{ name: 'code', type: 'String' }]),
        classNode('p2', 'P2', [{ name: 'code', type: 'Integer' }]),
        classNode('leaf', 'Leaf', [{ name: 'own' }]),
      ],
      edges: [inhEdge('leaf', 'p1'), inhEdge('leaf', 'p2')],
    } as unknown as UMLDiagram

    const tree = umlDiagramToSchemaTree(diagram, 'leaf')
    expect(tree.fields.map(f => f.name)).toEqual(['code', 'own'])
    expect(tree.fields[0].type).toBe('str')
  })

  it('combines inheritance (inlined) with composition (nested)', () => {
    const diagram = {
      nodes: [
        classNode('vehicle', 'Vehicle', [{ name: 'vin' }]),
        classNode('car', 'Car', [{ name: 'doors' }]),
        classNode('engine', 'Engine', [{ name: 'power' }]),
      ],
      edges: [inhEdge('car', 'vehicle'), edge('composition', 'engine', 'car', { sourceRole: 'engine' })],
    } as unknown as UMLDiagram

    const tree = umlDiagramToSchemaTree(diagram, 'car')
    expect(tree.name).toBe('Car')
    expect(tree.fields.map(f => f.name)).toEqual(['vin', 'doors', 'engine'])
    expect(tree.fields.find(f => f.name === 'engine')?.children?.map(c => c.name)).toEqual(['power'])
  })

  it('inlines a structural child that hangs off an inherited parent', () => {
    const diagram = {
      nodes: [
        classNode('vehicle', 'Vehicle', [{ name: 'vin' }]),
        classNode('car', 'Car', [{ name: 'doors' }]),
        classNode('engine', 'Engine', [{ name: 'power' }]),
      ],
      edges: [inhEdge('car', 'vehicle'), edge('composition', 'engine', 'vehicle', { sourceRole: 'engine' })],
    } as unknown as UMLDiagram

    const tree = umlDiagramToSchemaTree(diagram, 'car')
    expect(tree.name).toBe('Car')
    expect(tree.fields.map(f => f.name)).toEqual(['vin', 'doors', 'engine'])
    expect(tree.fields.find(f => f.name === 'engine')?.children?.map(c => c.name)).toEqual(['power'])
  })

  it('terminates on a cyclic inheritance and emits each attribute once per root', () => {
    // A cyclic inheritance leaves no derivable top, so every class counts as a root; each root's
    // subtree still inlines the cycle's attributes exactly once.
    const diagram = {
      nodes: [classNode('a', 'A', [{ name: 'a1' }]), classNode('b', 'B', [{ name: 'b1' }])],
      edges: [inhEdge('a', 'b'), inhEdge('b', 'a')],
    } as unknown as UMLDiagram

    const tree = umlDiagramToSchemaTree(diagram, 'a')
    expect(tree.fields.map(f => f.name)).toEqual(['a', 'b'])
    for (const root of tree.fields) {
      const names = root.children?.map(c => c.name) ?? []
      expect(new Set(names).size).toBe(names.length)
      expect(names).toContain('a1')
      expect(names).toContain('b1')
    }
  })

  it('never roots on the inheritance parent even when fallbackName matches it', () => {
    const diagram = {
      nodes: [classNode('animal', 'Animal', [{ name: 'name' }]), classNode('dog', 'Dog', [{ name: 'breed' }])],
      edges: [inhEdge('dog', 'animal')],
    } as unknown as UMLDiagram

    expect(umlDiagramToSchemaTree(diagram, 'animal').name).toBe('Dog')
  })

  it('does not collapse a multi-root diagram onto a name-matching class', () => {
    const diagram = {
      name: 'Beta',
      nodes: [classNode('a', 'Alpha', [{ name: 'a1' }]), classNode('b', 'Beta', [{ name: 'b1' }])],
      edges: [],
    } as unknown as UMLDiagram

    const tree = umlDiagramToSchemaTree(diagram, 'alpha')
    expect(tree.name).toBe('alpha')
    expect(tree.fields.map(f => f.name)).toEqual(['alpha', 'beta'])
  })
})
