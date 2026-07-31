import type { Connection } from '@xyflow/react'
import { describe, expect, it } from 'vitest'

import type { UMLDiagram, UMLNode } from '../types/diagram'
import type { UMLElement, UMLElementType } from '../types/uml'
import { diagramReducer, validateRelationshipConnection } from './diagramService'
import { classifyStructuralEdge } from './umlContainment'

const node = (id: string, name: string, isRoot?: boolean): UMLNode =>
  ({
    id,
    type: 'class',
    position: { x: 0, y: 0 },
    data: {
      element: { id: `elem-${id}`, name, type: 'class', attributes: [], operations: [], ...(isRoot ? { isRoot } : {}) },
      label: name,
    },
  }) as unknown as UMLNode

const diagram = (nodes: UMLNode[]): UMLDiagram =>
  ({ id: 'd1', name: 'D', nodes, edges: [], lastModified: new Date(0), isDirty: false }) as unknown as UMLDiagram

const rootFlags = (d: UMLDiagram): (boolean | undefined)[] => d.nodes.map(n => n.data.element.isRoot)

// A node whose id is its element's id, the way createUMLNode builds it: connections carry node ids
// and the containment rules read them as element ids, so the two must be the same value.
const typedNode = (id: string, type: UMLElementType): UMLNode =>
  ({
    id,
    type,
    position: { x: 0, y: 0 },
    data: {
      element:
        type === 'enumeration'
          ? { id, name: id, type, literals: [{ id: `${id}-lit`, name: 'GOOD', value: 'GOOD' }] }
          : { id, name: id, type, attributes: [], operations: [] },
      label: id,
    },
  }) as unknown as UMLNode

const connect = (source: string, target: string): Connection =>
  ({ source, target, sourceHandle: null, targetHandle: null }) as Connection

describe('diagramReducer SET_ROOT_NODE', () => {
  it('flags the target node and clears every other flag in one action', () => {
    const state = diagram([node('a', 'Alpha', true), node('b', 'Beta')])

    const next = diagramReducer(state, { type: 'SET_ROOT_NODE', payload: { id: 'b' } })

    expect(rootFlags(next)).toEqual([undefined, true])
    expect(next.isDirty).toBe(true)
  })

  it('clears the designation entirely for id null', () => {
    const state = diagram([node('a', 'Alpha', true), node('b', 'Beta')])

    const next = diagramReducer(state, { type: 'SET_ROOT_NODE', payload: { id: null } })

    expect(rootFlags(next)).toEqual([undefined, undefined])
  })

  it('keeps untouched nodes referentially identical', () => {
    const state = diagram([node('a', 'Alpha'), node('b', 'Beta')])

    const next = diagramReducer(state, { type: 'SET_ROOT_NODE', payload: { id: 'a' } })

    expect(next.nodes[1]).toBe(state.nodes[1])
    expect((next.nodes[0].data.element as UMLElement).isRoot).toBe(true)
  })
})

describe('validateRelationshipConnection composition/aggregation direction', () => {
  const state = diagram([
    typedNode('Whole', 'class'),
    typedNode('AbstractWhole', 'abstractClass'),
    typedNode('Quality', 'enumeration'),
    typedNode('Contract', 'interface'),
  ])

  it('accepts an enumeration as the part, drawn towards the owning class', () => {
    expect(validateRelationshipConnection(state, connect('Quality', 'Whole'), 'composition')).toBe(true)
    expect(validateRelationshipConnection(state, connect('Quality', 'AbstractWhole'), 'composition')).toBe(true)
  })

  it('rejects the reverse direction — an enumeration owns nothing', () => {
    expect(validateRelationshipConnection(state, connect('Whole', 'Quality'), 'composition')).toBe(false)
  })

  it('rejects an interface as the whole', () => {
    expect(validateRelationshipConnection(state, connect('Quality', 'Contract'), 'composition')).toBe(false)
  })

  it('applies the same direction to aggregation', () => {
    expect(validateRelationshipConnection(state, connect('Quality', 'Whole'), 'aggregation')).toBe(true)
    expect(validateRelationshipConnection(state, connect('Whole', 'Quality'), 'aggregation')).toBe(false)
  })

  // The regression this guards: the validation used to require the *source* to be the class, which
  // put the diamond — and therefore the container — on the enumeration, leaving the diagram without
  // a resolvable root and the persisted model null.
  it('agrees with umlContainment on which side is the container', () => {
    const accepted = connect('Quality', 'Whole')
    expect(validateRelationshipConnection(state, accepted, 'composition')).toBe(true)

    const containment = classifyStructuralEdge({
      id: 'r1',
      type: 'composition',
      source: accepted.source,
      target: accepted.target,
    })

    expect(containment).toEqual(expect.objectContaining({ containerId: 'Whole', partId: 'Quality' }))
  })
})
