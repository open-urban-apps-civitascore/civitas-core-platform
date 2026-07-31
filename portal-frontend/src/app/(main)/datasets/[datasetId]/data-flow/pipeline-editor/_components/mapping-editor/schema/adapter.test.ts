import { describe, expect, it } from 'vitest'

import type { UMLDiagram } from '@/components/uml-modeler/types/diagram'

import { umlDiagramToSchemaTree } from './adapter'
import { requiredFieldPaths } from './fieldTree'

const treeOf = (diagram: UMLDiagram | null, fallbackName: string) => umlDiagramToSchemaTree(diagram, fallbackName).tree

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
    const tree = treeOf(diagram, 'thing')
    const required = (name: string) => tree.fields.find(f => f.name === name)?.required
    expect(required('Id')).toBe(true) // {id}
    expect(required('Name')).toBe(true) // 1
    expect(required('Nick')).toBeFalsy() // 0..1 optional — the factory omits the flag when false
    expect(required('Tags')).toBe(true) // 1..*
    expect(required('Note')).toBe(true) // unset = single required
  })

  it('requiredFieldPaths returns only the required field paths', () => {
    expect(requiredFieldPaths(treeOf(diagram, 'thing'))).toEqual(['$.Id', '$.Name', '$.Tags', '$.Note'])
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

    const tree = treeOf(diagram, 'thing')

    expect(tree.name).toBe('Thing')
    expect(tree.fields.map(f => f.name)).toEqual(['Name', 'Id', 'Properties'])
    const nested = tree.fields.find(f => f.name === 'Properties')
    expect(nested?.type).toBe('object')
    expect(nested?.children?.map(c => c.name)).toEqual(['Reference', 'Source'])
    expect(nested?.children?.map(c => c.path)).toEqual(['$.Properties.Reference', '$.Properties.Source'])
  })

  it('ignores an out-of-scope aggregation edge: the part is not nested', () => {
    // Part is reachable (composed under `owned`) so the diagram has a single root; the aggregation
    // edge with role `parts` is out of scope and must add no field of its own.
    const diagram = {
      nodes: [classNode('whole-id', 'Whole', [{ name: 'label' }]), classNode('part-id', 'Part', [{ name: 'value' }])],
      edges: [
        edge('composition', 'part-id', 'whole-id', { sourceRole: 'owned' }),
        edge('aggregation', 'part-id', 'whole-id', { sourceRole: 'parts' }),
      ],
    } as unknown as UMLDiagram

    const tree = treeOf(diagram, 'whole')

    expect(tree.name).toBe('Whole')
    expect(tree.fields.find(f => f.name === 'Parts')).toBeUndefined()
    expect(tree.fields.map(f => f.name)).toEqual(['Label', 'Owned'])
  })

  it('ignores an out-of-scope association edge: the target is not nested', () => {
    // Customer is reachable (composed under `owned`) so a single root resolves; the association
    // edge with role `customer` is out of scope and must add no field of its own.
    const diagram = {
      nodes: [
        classNode('order-id', 'Order', [{ name: 'orderNo' }]),
        classNode('cust-id', 'Customer', [{ name: 'email' }]),
      ],
      edges: [
        edge('composition', 'cust-id', 'order-id', { sourceRole: 'owned' }),
        edge('association', 'order-id', 'cust-id', { targetRole: 'customer' }),
      ],
    } as unknown as UMLDiagram

    const tree = treeOf(diagram, 'order')

    expect(tree.name).toBe('Order')
    expect(tree.fields.find(f => f.name === 'Customer')).toBeUndefined()
    expect(tree.fields.map(f => f.name)).toEqual(['OrderNo', 'Owned'])
  })

  it('reads the part multiplicity from the source end for composition (* -> array)', () => {
    const diagram = {
      nodes: [classNode('thing-id', 'Thing', [{ name: 'id' }]), classNode('r-id', 'Reading', [{ name: 'value' }])],
      edges: [edge('composition', 'r-id', 'thing-id', { sourceRole: 'readings', sourceMultiplicity: '*' })],
    } as unknown as UMLDiagram

    const nested = treeOf(diagram, 'thing').fields.find(f => f.name === 'Readings')
    expect(nested?.type).toBe('array')
  })

  it('ignores an out-of-scope association edge regardless of its multiplicity', () => {
    // Item is reachable (composed under `owned`) so a single root resolves; the many-valued
    // association with role `items` is out of scope and yields no array field.
    const diagram = {
      nodes: [classNode('order-id', 'Order', [{ name: 'orderNo' }]), classNode('item-id', 'Item', [{ name: 'sku' }])],
      edges: [
        edge('composition', 'item-id', 'order-id', { sourceRole: 'owned' }),
        edge('association', 'order-id', 'item-id', { targetRole: 'items', targetMultiplicity: '*' }),
      ],
    } as unknown as UMLDiagram

    expect(treeOf(diagram, 'order').fields.find(f => f.name === 'Items')).toBeUndefined()
  })

  it('never roots on an embedded part even when fallbackName matches it', () => {
    const diagram = {
      nodes: [
        classNode('thing-id', 'Thing', [{ name: 'name' }]),
        classNode('props-id', 'properties', [{ name: 'reference' }]),
      ],
      edges: [edge('composition', 'props-id', 'thing-id', { sourceRole: 'properties' })],
    } as unknown as UMLDiagram

    expect(treeOf(diagram, 'properties').name).toBe('Thing')
  })

  it('yields an empty tree for a diagram with several unconnected root classes', () => {
    // The same condition under which the schema export refuses to produce a model — the fallback
    // tree must not invent a record shape the engine would never see.
    const diagram = {
      nodes: [classNode('a', 'Alpha', [{ name: 'a1' }]), classNode('b', 'Beta', [{ name: 'b1' }])],
      edges: [],
    } as unknown as UMLDiagram

    expect(treeOf(diagram, 'MyStructure')).toEqual({ name: 'MyStructure', fields: [] })
  })

  it('returns an empty tree for a null diagram', () => {
    expect(treeOf(null, 'fallback')).toEqual({ name: 'fallback', fields: [] })
  })

  it('roots on the subclass and inlines inherited attributes (inherited first)', () => {
    const diagram = {
      nodes: [classNode('animal', 'Animal', [{ name: 'name' }]), classNode('dog', 'Dog', [{ name: 'breed' }])],
      edges: [inhEdge('dog', 'animal')],
    } as unknown as UMLDiagram

    const tree = treeOf(diagram, 'animal')
    expect(tree.name).toBe('Dog')
    expect(tree.fields.map(f => f.name)).toEqual(['Name', 'Breed'])
    expect(tree.fields.map(f => f.path)).toEqual(['$.Name', '$.Breed'])
  })

  it('ignores realization rather than inlining it like inheritance', () => {
    // Impl is reachable (composed under `impl`) so IFace is the single root. Realization is out of
    // scope, so Impl's `x` is NOT inlined into IFace the way inheritance would flatten a parent — it
    // stays nested under the composition, and IFace's own fields are just `y` plus the composed part.
    const diagram = {
      nodes: [classNode('iface', 'IFace', [{ name: 'y' }]), classNode('impl', 'Impl', [{ name: 'x' }])],
      edges: [edge('composition', 'impl', 'iface', { sourceRole: 'impl' }), inhEdge('impl', 'iface', 'realization')],
    } as unknown as UMLDiagram

    const tree = treeOf(diagram, 'iface')
    expect(tree.name).toBe('IFace')
    expect(tree.fields.map(f => f.name)).toEqual(['Y', 'Impl'])
    const nested = tree.fields.find(f => f.name === 'Impl')
    expect(nested?.type).toBe('object')
    expect(nested?.children?.map(c => c.name)).toEqual(['X'])
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

    const tree = treeOf(diagram, 'leaf')
    expect(tree.name).toBe('Leaf')
    expect(tree.fields.map(f => f.name)).toEqual(['A', 'B', 'C'])
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

    const tree = treeOf(diagram, 'leaf')
    expect(tree.name).toBe('Leaf')
    expect(tree.fields.map(f => f.name)).toEqual(['X', 'Y', 'Own'])
  })

  it('lets the subclass override an inherited attribute of the same name (keeping position)', () => {
    const diagram = {
      nodes: [
        classNode('parent', 'Parent', [{ name: 'value', type: 'String' }]),
        classNode('child', 'Child', [{ name: 'value', type: 'Integer' }]),
      ],
      edges: [inhEdge('child', 'parent')],
    } as unknown as UMLDiagram

    const tree = treeOf(diagram, 'child')
    expect(tree.fields.map(f => f.name)).toEqual(['Value'])
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

    const tree = treeOf(diagram, 'leaf')
    expect(tree.fields.map(f => f.name)).toEqual(['Code', 'Own'])
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

    const tree = treeOf(diagram, 'car')
    expect(tree.name).toBe('Car')
    expect(tree.fields.map(f => f.name)).toEqual(['Vin', 'Doors', 'Engine'])
    expect(tree.fields.find(f => f.name === 'Engine')?.children?.map(c => c.name)).toEqual(['Power'])
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

    const tree = treeOf(diagram, 'car')
    expect(tree.name).toBe('Car')
    expect(tree.fields.map(f => f.name)).toEqual(['Vin', 'Doors', 'Engine'])
    expect(tree.fields.find(f => f.name === 'Engine')?.children?.map(c => c.name)).toEqual(['Power'])
  })

  it('yields an empty tree for a cyclic inheritance without a derivable top', () => {
    const diagram = {
      nodes: [classNode('a', 'A', [{ name: 'a1' }]), classNode('b', 'B', [{ name: 'b1' }])],
      edges: [inhEdge('a', 'b'), inhEdge('b', 'a')],
    } as unknown as UMLDiagram

    expect(treeOf(diagram, 'a')).toEqual({ name: 'a', fields: [] })
  })

  it('never roots on the inheritance parent even when fallbackName matches it', () => {
    const diagram = {
      nodes: [classNode('animal', 'Animal', [{ name: 'name' }]), classNode('dog', 'Dog', [{ name: 'breed' }])],
      edges: [inhEdge('dog', 'animal')],
    } as unknown as UMLDiagram

    expect(treeOf(diagram, 'animal').name).toBe('Dog')
  })

  it('does not collapse an ambiguous diagram onto a name-matching class', () => {
    const diagram = {
      name: 'Beta',
      nodes: [classNode('a', 'Alpha', [{ name: 'a1' }]), classNode('b', 'Beta', [{ name: 'b1' }])],
      edges: [],
    } as unknown as UMLDiagram

    // The name match must not silently pick a root; the invalid diagram yields no mappable tree.
    expect(treeOf(diagram, 'alpha')).toEqual({ name: 'alpha', fields: [] })
  })

  it('renders a single-enumeration diagram as a tree without record fields', () => {
    const enumOnly = {
      nodes: [
        {
          id: 'node-e1',
          type: 'enumeration',
          position: { x: 0, y: 0 },
          data: {
            element: { id: 'e1', name: 'Status', type: 'enumeration', literals: [{ id: 'l1', name: 'ON' }] },
            label: 'Status',
          },
        },
      ],
      edges: [],
    } as unknown as UMLDiagram

    // An enumeration is a scalar value at runtime — there is no record to map fields against.
    expect(treeOf(enumOnly, 'MyStructure')).toEqual({ name: 'Status', fields: [] })
  })
})
