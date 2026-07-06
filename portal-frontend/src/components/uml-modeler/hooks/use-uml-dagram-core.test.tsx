import { act, renderHook } from '@testing-library/react'
import type { Connection } from '@xyflow/react'
import { describe, expect, it } from 'vitest'

import type { UMLDiagram } from '../types/diagram'
import { useUMLDiagramCore } from './use-uml-dagram-core'

/** Two class nodes so a composition connection between them is structurally valid. */
const twoClassDiagram = (): UMLDiagram =>
  ({
    id: 'diagram-1',
    name: 'Test',
    nodes: [
      {
        id: 'node-1',
        type: 'class',
        position: { x: 0, y: 0 },
        data: { element: { id: 'elem-1', name: 'A', type: 'class', attributes: [], operations: [] }, label: 'A' },
      },
      {
        id: 'node-2',
        type: 'class',
        position: { x: 200, y: 0 },
        data: { element: { id: 'elem-2', name: 'B', type: 'class', attributes: [], operations: [] }, label: 'B' },
      },
    ],
    edges: [],
    lastModified: new Date('2026-01-01'),
    isDirty: false,
  }) as unknown as UMLDiagram

const connection: Connection = { source: 'node-1', target: 'node-2', sourceHandle: null, targetHandle: null }

describe('useUMLDiagramCore — relationship tool selection', () => {
  it('has no relationship tool selected by default', () => {
    const { result } = renderHook(() => useUMLDiagramCore(twoClassDiagram()))
    expect(result.current.activeRelationshipType).toBeNull()
  })

  it('refuses to validate or add an edge while no tool is selected', () => {
    const { result } = renderHook(() => useUMLDiagramCore(twoClassDiagram()))

    expect(result.current.validateConnection(connection)).toBe(false)

    act(() => result.current.addEdge(connection))
    expect(result.current.diagram.edges).toHaveLength(0)
  })

  it('adds an edge of the selected type once a tool is picked', () => {
    const { result } = renderHook(() => useUMLDiagramCore(twoClassDiagram()))

    act(() => result.current.setActiveRelationshipType('composition'))
    expect(result.current.validateConnection(connection)).toBe(true)

    act(() => result.current.addEdge(connection))
    expect(result.current.diagram.edges).toHaveLength(1)
    expect(result.current.diagram.edges[0].data.relationship.type).toBe('composition')
  })

  it('returns to the neutral state when the tool is cleared', () => {
    const { result } = renderHook(() => useUMLDiagramCore(twoClassDiagram()))

    act(() => result.current.setActiveRelationshipType('composition'))
    act(() => result.current.setActiveRelationshipType(null))

    expect(result.current.activeRelationshipType).toBeNull()
    act(() => result.current.addEdge(connection))
    expect(result.current.diagram.edges).toHaveLength(0)
  })
})
