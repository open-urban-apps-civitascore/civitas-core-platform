import { describe, expect, it } from 'vitest'

import type { UMLDiagram } from '../types/diagram'
import { buildUMLModelPayload } from './modelUploadService'

const createTestDiagram = (overrides?: Partial<UMLDiagram>): UMLDiagram => ({
  id: 'diagram-1',
  name: 'Test Diagram',
  nodes: [
    {
      id: 'node-1',
      type: 'class',
      position: { x: 100, y: 200 },
      data: {
        element: {
          id: 'elem-1',
          name: 'MyClass',
          type: 'class',
          attributes: [],
          operations: [],
        },
        label: 'MyClass',
      },
    },
    {
      id: 'node-2',
      type: 'class',
      position: { x: 400, y: 100 },
      data: {
        element: {
          id: 'elem-2',
          name: 'MyPart',
          type: 'class',
          attributes: [],
          operations: [],
        },
        label: 'MyPart',
      },
    },
  ],
  edges: [
    // Composition with MyClass as the container (edge target) — MyClass stays the root.
    {
      id: 'edge-1',
      type: 'composition',
      source: 'node-2',
      target: 'node-1',
      data: {
        relationship: {
          id: 'rel-1',
          type: 'composition',
          source: 'elem-2',
          target: 'elem-1',
        },
        label: '',
        isSelected: false,
        isDirty: false,
      },
    },
  ],
  lastModified: new Date('2026-01-01'),
  isDirty: false,
  ...overrides,
})

describe('buildUMLModelPayload', () => {
  it('should include the diagram name', () => {
    const diagram = createTestDiagram()
    const payload = buildUMLModelPayload(diagram)

    expect(payload.name).toBe('Test Diagram')
  })

  it('should extract node positions into styles', () => {
    const diagram = createTestDiagram()
    const payload = buildUMLModelPayload(diagram)

    expect(payload.styles.nodePositions).toEqual({
      'node-1': { x: 100, y: 200 },
      'node-2': { x: 400, y: 100 },
    })
  })

  it('should include viewport in styles when present', () => {
    const diagram = createTestDiagram({ viewport: { x: 10, y: 20, zoom: 1.5 } })
    const payload = buildUMLModelPayload(diagram)

    expect(payload.styles.viewport).toEqual({ x: 10, y: 20, zoom: 1.5 })
  })

  it('should have undefined viewport when not set', () => {
    const diagram = createTestDiagram()
    const payload = buildUMLModelPayload(diagram)

    expect(payload.styles.viewport).toBeUndefined()
  })

  it('should produce a JSON Schema object in the model field', () => {
    const diagram = createTestDiagram()
    const payload = buildUMLModelPayload(diagram)

    expect(typeof payload.model).toBe('object')
    expect(payload.model.$schema).toBe('https://json-schema.org/draft/2020-12/schema')
    // A DataStructure is a $defs library (no inline root shape): every class is a $defs member, and
    // the root class MyClass is designated by a top-level $ref (a local #/$defs/ pointer without a
    // model URN). Model Forge splits the members into Elements on ingest.
    expect(payload.model.title).toBe('Test Diagram')
    expect(payload.model.type).toBeUndefined()
    expect(payload.model.$ref).toBe('#/$defs/MyClass')
    expect((payload.model.$defs as Record<string, unknown>).MyClass).toBeDefined()
  })

  it('should handle an empty diagram', () => {
    const diagram = createTestDiagram({ nodes: [], edges: [] })
    const payload = buildUMLModelPayload(diagram)

    expect(payload.name).toBe('Test Diagram')
    expect(payload.styles.nodePositions).toEqual({})
    // An empty diagram yields a library with no members and no root.
    expect(payload.model.type).toBeUndefined()
    expect(payload.model.$defs).toBeUndefined()
    expect(payload.model.$ref).toBeUndefined()
  })

  it('should pass modelUri through to the JSON Schema $id', () => {
    const diagram = createTestDiagram()
    const payload = buildUMLModelPayload(diagram, 'http://example.org/model')

    expect(payload.model.$id).toBe('http://example.org/model')
  })
})
