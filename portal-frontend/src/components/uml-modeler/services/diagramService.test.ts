import { describe, expect, it } from 'vitest'

import type { UMLDiagram, UMLNode } from '../types/diagram'
import type { UMLElement } from '../types/uml'
import { diagramReducer } from './diagramService'

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
