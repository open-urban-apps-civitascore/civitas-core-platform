import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

import { UMLDiagramSchema } from './diagramSchema'

const getFixturePath = () => {
  const pathFromFrontend = resolve(
    process.cwd(),
    '../config-adapter/docs/examples/datastructure-version-jsonschema-nested.json',
  )
  if (existsSync(pathFromFrontend)) return pathFromFrontend
  return resolve(
    process.cwd(),
    'config-adapter/docs/examples/datastructure-version-jsonschema-nested.json',
  )
}

describe('diagramSchema', () => {
  it('validates the fixture styles from nested datastructure example as valid', () => {
    const fixtureContent = JSON.parse(readFileSync(getFixturePath(), 'utf-8'))
    const styles = fixtureContent.styles

    const result = UMLDiagramSchema.safeParse(styles)
    expect(result.success).toBe(true)
    if (!result.success) return

    expect(result.data.nodes).toHaveLength(2)
    expect(result.data.edges).toHaveLength(1)
  })

  it('validates a minimal valid diagram', () => {
    const minimalDiagram = {
      id: 'diagram-1',
      name: 'Minimal',
      nodes: [
        {
          id: 'node-1',
          type: 'class',
          position: { x: 10, y: 20 },
          data: {
            element: {
              id: 'node-1',
              name: 'MyClass',
              type: 'class',
              attributes: [],
              operations: [],
            },
            label: 'MyClass',
          },
        },
      ],
      edges: [],
    }

    const result = UMLDiagramSchema.safeParse(minimalDiagram)
    expect(result.success).toBe(true)
  })

  it('strips extra and transient react-flow fields from nodes and edges', () => {
    const diagramWithTransientFields = {
      id: 'diagram-1',
      name: 'WithTransients',
      nodes: [
        {
          id: 'node-1',
          type: 'class',
          position: { x: 10, y: 20 },
          data: {
            element: {
              id: 'node-1',
              name: 'MyClass',
              type: 'class',
              attributes: [],
              operations: [],
            },
            label: 'MyClass',
            isSelected: true,
            isDirty: true,
          },
          measured: { width: 100, height: 80 },
          selected: true,
          dragging: false,
          style: { background: '#fff' },
          dragHandle: '.handle',
        },
      ],
      edges: [],
    }

    const result = UMLDiagramSchema.safeParse(diagramWithTransientFields)
    expect(result.success).toBe(true)
    if (!result.success) return

    const parsedNode = result.data.nodes[0] as Record<string, unknown>
    expect(parsedNode.measured).toBeUndefined()
    expect(parsedNode.selected).toBeUndefined()
    expect(parsedNode.dragging).toBeUndefined()
    expect(parsedNode.style).toBeUndefined()
    expect(parsedNode.dragHandle).toBeUndefined()

    const parsedData = parsedNode.data as Record<string, unknown>
    expect(parsedData.isSelected).toBeUndefined()
    expect(parsedData.isDirty).toBeUndefined()
  })

  it('rejects an edge with a non-existent target node', () => {
    const invalidDiagram = {
      nodes: [
        {
          id: 'node-1',
          type: 'class',
          position: { x: 0, y: 0 },
          data: {
            element: { id: 'node-1', name: 'A', type: 'class', attributes: [], operations: [] },
            label: 'A',
          },
        },
      ],
      edges: [
        {
          id: 'edge-1',
          type: 'association',
          source: 'node-1',
          target: 'missing-node',
        },
      ],
    }

    const result = UMLDiagramSchema.safeParse(invalidDiagram)
    expect(result.success).toBe(false)
  })

  it('rejects an edge with a non-existent source node', () => {
    const invalidDiagram = {
      nodes: [
        {
          id: 'node-1',
          type: 'class',
          position: { x: 0, y: 0 },
          data: {
            element: { id: 'node-1', name: 'A', type: 'class', attributes: [], operations: [] },
            label: 'A',
          },
        },
      ],
      edges: [
        {
          id: 'edge-1',
          type: 'association',
          source: 'missing-node',
          target: 'node-1',
        },
      ],
    }

    const result = UMLDiagramSchema.safeParse(invalidDiagram)
    expect(result.success).toBe(false)
  })

  it('rejects an edge when relationship refers to non-existent nodes', () => {
    const invalidDiagram = {
      nodes: [
        {
          id: 'node-1',
          type: 'class',
          position: { x: 0, y: 0 },
          data: {
            element: { id: 'node-1', name: 'A', type: 'class', attributes: [], operations: [] },
            label: 'A',
          },
        },
      ],
      edges: [
        {
          id: 'edge-1',
          type: 'association',
          source: 'node-1',
          target: 'node-1',
          data: {
            relationship: {
              id: 'rel-1',
              type: 'association',
              source: 'node-1',
              target: 'ghost-node',
            },
          },
        },
      ],
    }

    const result = UMLDiagramSchema.safeParse(invalidDiagram)
    expect(result.success).toBe(false)
  })

  it('rejects a node when data.element.id does not match node.id', () => {
    const invalidDiagram = {
      nodes: [
        {
          id: 'node-1',
          type: 'class',
          position: { x: 0, y: 0 },
          data: {
            element: { id: 'node-different', name: 'A', type: 'class', attributes: [], operations: [] },
            label: 'A',
          },
        },
      ],
      edges: [],
    }

    const result = UMLDiagramSchema.safeParse(invalidDiagram)
    expect(result.success).toBe(false)
  })

  it('rejects a node when data.element.type does not match node.type', () => {
    const invalidDiagram = {
      nodes: [
        {
          id: 'node-1',
          type: 'class',
          position: { x: 0, y: 0 },
          data: {
            element: { id: 'node-1', name: 'A', type: 'interface', operations: [] },
            label: 'A',
          },
        },
      ],
      edges: [],
    }

    const result = UMLDiagramSchema.safeParse(invalidDiagram)
    expect(result.success).toBe(false)
  })

  it('rejects a diagram with more than one root element', () => {
    const invalidDiagram = {
      nodes: [
        {
          id: 'node-1',
          type: 'class',
          position: { x: 0, y: 0 },
          data: {
            element: { id: 'node-1', name: 'A', type: 'class', isRoot: true, attributes: [], operations: [] },
            label: 'A',
          },
        },
        {
          id: 'node-2',
          type: 'class',
          position: { x: 100, y: 0 },
          data: {
            element: { id: 'node-2', name: 'B', type: 'class', isRoot: true, attributes: [], operations: [] },
            label: 'B',
          },
        },
      ],
      edges: [],
    }

    const result = UMLDiagramSchema.safeParse(invalidDiagram)
    expect(result.success).toBe(false)
  })

  it('rejects an invalid element type', () => {
    const invalidDiagram = {
      nodes: [
        {
          id: 'node-1',
          type: 'unknownType',
          position: { x: 0, y: 0 },
          data: {
            element: { id: 'node-1', name: 'A', type: 'unknownType' },
            label: 'A',
          },
        },
      ],
      edges: [],
    }

    const result = UMLDiagramSchema.safeParse(invalidDiagram)
    expect(result.success).toBe(false)
  })

  it('rejects non-object inputs', () => {
    expect(UMLDiagramSchema.safeParse(null).success).toBe(false)
    expect(UMLDiagramSchema.safeParse(undefined).success).toBe(false)
    expect(UMLDiagramSchema.safeParse('string').success).toBe(false)
    expect(UMLDiagramSchema.safeParse(123).success).toBe(false)
    expect(UMLDiagramSchema.safeParse([]).success).toBe(false)
  })
})
