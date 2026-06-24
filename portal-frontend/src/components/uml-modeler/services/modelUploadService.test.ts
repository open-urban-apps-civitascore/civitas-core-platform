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
      type: 'interface',
      position: { x: 400, y: 100 },
      data: {
        element: {
          id: 'elem-2',
          name: 'MyInterface',
          type: 'interface',
          operations: [],
        },
        label: 'MyInterface',
      },
    },
  ],
  edges: [
    {
      id: 'edge-1',
      type: 'realization',
      source: 'node-1',
      target: 'node-2',
      data: {
        relationship: {
          id: 'rel-1',
          type: 'realization',
          source: 'elem-1',
          target: 'elem-2',
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
    expect(payload.model.type).toBe('object')
    // Title is the root class name (Option A), not the diagram name.
    expect(payload.model.title).toBe('MyClass')
  })

  it('should handle an empty diagram', () => {
    const diagram = createTestDiagram({ nodes: [], edges: [] })
    const payload = buildUMLModelPayload(diagram)

    expect(payload.name).toBe('Test Diagram')
    expect(payload.styles.nodePositions).toEqual({})
    expect(payload.model.type).toBe('object')
    expect(payload.model.properties).toEqual({})
  })

  it('should pass modelUri through to the JSON Schema $id', () => {
    const diagram = createTestDiagram()
    const payload = buildUMLModelPayload(diagram, 'http://example.org/model')

    expect(payload.model.$id).toBe('http://example.org/model')
  })
})
