import { describe, expect, it } from 'vitest'

import type { UMLDiagram } from '../types/diagram'
import { exportToXmi, sanitizeName } from './xmiExportService'

const createTestDiagram = (overrides?: Partial<UMLDiagram>): UMLDiagram => ({
  id: 'diagram-1',
  name: 'Test Diagram',
  nodes: [
    {
      id: 'node-1',
      type: 'class',
      position: { x: 0, y: 0 },
      data: {
        element: {
          id: 'elem-1',
          name: 'Person',
          type: 'class',
          attributes: [
            { id: 'attr-1', name: 'name', type: 'String', visibility: 'private' },
            { id: 'attr-2', name: 'age', type: 'Integer', visibility: 'private' },
          ],
          operations: [{ id: 'op-1', name: 'getName', returnType: 'String', visibility: 'public', parameters: [] }],
        },
        label: 'Person',
      },
    },
    {
      id: 'node-2',
      type: 'interface',
      position: { x: 200, y: 0 },
      data: {
        element: {
          id: 'elem-2',
          name: 'Serializable',
          type: 'interface',
          operations: [{ id: 'op-2', name: 'serialize', returnType: 'String', visibility: 'public', parameters: [] }],
        },
        label: 'Serializable',
      },
    },
    {
      id: 'node-3',
      type: 'enumeration',
      position: { x: 400, y: 0 },
      data: {
        element: {
          id: 'elem-3',
          name: 'Color',
          type: 'enumeration',
          literals: [
            { id: 'lit-1', name: 'RED' },
            { id: 'lit-2', name: 'GREEN' },
            { id: 'lit-3', name: 'BLUE' },
          ],
        },
        label: 'Color',
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

describe('exportToXmi', () => {
  it('should produce valid XML with correct namespaces', () => {
    const xmi = exportToXmi(createTestDiagram())

    expect(xmi).toContain('<?xml version="1.0" encoding="UTF-8"?>')
    expect(xmi).toContain('xmlns:xmi="http://www.omg.org/spec/XMI/20131001"')
    expect(xmi).toContain('xmlns:uml="http://www.eclipse.org/uml2/5.0.0/UML"')
  })

  it('should export classes with attributes and operations', () => {
    const xmi = exportToXmi(createTestDiagram())

    expect(xmi).toContain('xmi:type="uml:Class"')
    expect(xmi).toContain('name="Person"')
    expect(xmi).toContain('name="name"')
    expect(xmi).toContain('name="age"')
    expect(xmi).toContain('name="getName"')
  })

  it('should export interfaces with operations', () => {
    const xmi = exportToXmi(createTestDiagram())

    expect(xmi).toContain('xmi:type="uml:Interface"')
    expect(xmi).toContain('name="Serializable"')
    expect(xmi).toContain('name="serialize"')
  })

  it('should export enumerations with literals', () => {
    const xmi = exportToXmi(createTestDiagram())

    expect(xmi).toContain('xmi:type="uml:Enumeration"')
    expect(xmi).toContain('name="Color"')
    expect(xmi).toContain('name="RED"')
    expect(xmi).toContain('name="GREEN"')
    expect(xmi).toContain('name="BLUE"')
  })

  it('should export realization relationships', () => {
    const xmi = exportToXmi(createTestDiagram())

    expect(xmi).toContain('xmi:type="uml:InterfaceRealization"')
    expect(xmi).toContain('implementingClassifier="elem-1"')
    expect(xmi).toContain('contract="elem-2"')
  })

  it('should export association relationships with aggregation kinds', () => {
    const diagram = createTestDiagram({
      edges: [
        {
          id: 'edge-comp',
          type: 'composition',
          source: 'node-1',
          target: 'node-3',
          data: {
            relationship: {
              id: 'rel-comp',
              type: 'composition',
              source: 'elem-1',
              target: 'elem-3',
              sourceMultiplicity: '1',
              targetMultiplicity: '0..*',
            },
            label: '',
            isSelected: false,
            isDirty: false,
          },
        },
      ],
    })
    const xmi = exportToXmi(diagram)

    expect(xmi).toContain('xmi:type="uml:Association"')
    expect(xmi).toContain('aggregation="composite"')
  })

  it('should include modelUri when provided', () => {
    const xmi = exportToXmi(createTestDiagram(), 'http://example.org/model')

    expect(xmi).toContain('URI="http://example.org/model"')
  })

  it('should handle an empty diagram', () => {
    const diagram = createTestDiagram({ name: 'Empty', nodes: [], edges: [] })
    const xmi = exportToXmi(diagram)

    expect(xmi).toContain('uml:Model')
    expect(xmi).toContain('name="Empty"')
    expect(xmi).not.toContain('packagedElement xmi:type="uml:Class"')
  })

  it('should escape XML special characters in names', () => {
    const diagram = createTestDiagram({
      nodes: [
        {
          id: 'node-xml',
          type: 'class',
          position: { x: 0, y: 0 },
          data: {
            element: {
              id: 'elem-xml',
              name: 'Foo<Bar>&"Baz"',
              type: 'class',
              attributes: [],
              operations: [],
            },
            label: 'test',
          },
        },
      ],
      edges: [],
    })
    const xmi = exportToXmi(diagram)

    expect(xmi).toContain('Foo&lt;Bar&gt;&amp;&quot;Baz&quot;')
    expect(xmi).not.toContain('Foo<Bar>')
  })
})

describe('sanitizeName', () => {
  it('should lowercase and replace non-alphanumeric chars with hyphens', () => {
    expect(sanitizeName('My Test Diagram')).toBe('my-test-diagram')
  })

  it('should collapse consecutive hyphens', () => {
    expect(sanitizeName('foo--bar___baz')).toBe('foo-bar-baz')
  })

  it('should strip leading and trailing hyphens', () => {
    expect(sanitizeName('--hello--')).toBe('hello')
  })
})
