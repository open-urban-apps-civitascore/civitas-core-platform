import { describe, expect, it } from 'vitest'

import { PROPERTY_CARDINALITY_VALUES, type PropertyCardinality } from '../constants/umlTypes'
import type { UMLDiagram } from '../types/diagram'
import { canMultiplicityBePrimaryKey, exportToJsonSchema, sanitizeName } from './jsonSchemaExportService'

const baseDiagram = (overrides?: Partial<UMLDiagram>): UMLDiagram => ({
  id: 'diagram-1',
  name: 'TrafficSensor',
  nodes: [
    {
      id: 'node-1',
      type: 'class',
      position: { x: 0, y: 0 },
      data: {
        element: {
          id: 'elem-1',
          name: 'TrafficSensor',
          type: 'class',
          documentation: 'A traffic sensor reading',
          attributes: [
            {
              id: 'a1',
              name: 'stationId',
              type: 'String',
              visibility: 'public',
              isId: true,
            },
            {
              id: 'a2',
              name: 'temperature',
              type: 'Double',
              visibility: 'public',
              multiplicity: '0..1',
            },
            {
              id: 'a3',
              name: 'tags',
              type: 'String',
              visibility: 'public',
              multiplicity: '*',
            },
          ],
          operations: [],
        },
        label: 'TrafficSensor',
      },
    },
  ],
  edges: [],
  lastModified: new Date('2026-01-01'),
  isDirty: false,
  ...overrides,
})

describe('sanitizeName', () => {
  it('lowercases and dashes non-alphanumerics', () => {
    expect(sanitizeName('Traffic Sensor!!')).toBe('traffic-sensor')
  })
})

describe('exportToJsonSchema', () => {
  it('produces a draft 2020-12 object schema with title and $id', () => {
    const schema = exportToJsonSchema(baseDiagram())

    expect(schema.$schema).toBe('https://json-schema.org/draft/2020-12/schema')
    expect(schema.type).toBe('object')
    expect(schema.title).toBe('TrafficSensor')
    expect(schema.$id).toBe('http://civitas.org/model/trafficsensor')
  })

  it('uses the provided model URI as $id', () => {
    const schema = exportToJsonSchema(baseDiagram(), 'http://civitas.org/model/traffic-sensor/1.0.0')
    expect(schema.$id).toBe('http://civitas.org/model/traffic-sensor/1.0.0')
  })

  it('maps attributes to properties with correct types', () => {
    const schema = exportToJsonSchema(baseDiagram())
    const properties = schema.properties as Record<string, Record<string, unknown>>

    expect(properties.stationId).toEqual({ type: 'string', 'x-core-primaryKey': true })
    expect(properties.temperature).toEqual({ type: 'number' })
  })

  it('maps "*" multiplicity attributes to arrays', () => {
    const schema = exportToJsonSchema(baseDiagram())
    const properties = schema.properties as Record<string, Record<string, unknown>>

    expect(properties.tags).toEqual({ type: 'array', items: { type: 'string' } })
  })

  // Every cardinality offered by the property dropdown (issue #1707) and its
  // expected JSON Schema mapping. The Record is keyed off PropertyCardinality so a
  // new dropdown value without a mapping expectation fails to type-check here.
  const cardinalityExpectations: Record<
    PropertyCardinality,
    { expectedProp: Record<string, unknown>; required: boolean }
  > = {
    '0..1': { expectedProp: { type: 'string' }, required: false },
    '1': { expectedProp: { type: 'string' }, required: true },
    '0..*': { expectedProp: { type: 'array', items: { type: 'string' } }, required: false },
    '1..*': { expectedProp: { type: 'array', items: { type: 'string' }, minItems: 1 }, required: true },
  }
  const cardinalityCases = PROPERTY_CARDINALITY_VALUES.map(multiplicity => ({
    multiplicity,
    ...cardinalityExpectations[multiplicity],
  }))

  it.each(cardinalityCases)(
    'maps cardinality "$multiplicity" to the expected property and required flag',
    ({ multiplicity, expectedProp, required }) => {
      const diagram = baseDiagram({
        nodes: [
          {
            id: 'node-1',
            type: 'class',
            position: { x: 0, y: 0 },
            data: {
              element: {
                id: 'elem-1',
                name: 'TrafficSensor',
                type: 'class',
                attributes: [{ id: 'a1', name: 'tags', type: 'String', visibility: 'public', multiplicity }],
                operations: [],
              },
              label: 'TrafficSensor',
            },
          },
        ],
      })

      const schema = exportToJsonSchema(diagram)
      const properties = schema.properties as Record<string, Record<string, unknown>>

      expect(properties.tags).toEqual(expectedProp)
      if (required) {
        expect(schema.required ?? []).toContain('tags')
      } else {
        expect(schema.required ?? []).not.toContain('tags')
      }
    },
  )

  it('treats an unset multiplicity as a single required scalar (backwards compatible)', () => {
    const schema = exportToJsonSchema(baseDiagram())
    const properties = schema.properties as Record<string, Record<string, unknown>>

    // stationId has no multiplicity set -> plain scalar, and required.
    expect(properties.stationId).toEqual({ type: 'string', 'x-core-primaryKey': true })
    expect(schema.required).toContain('stationId')
  })

  it('adds id attributes to required', () => {
    const schema = exportToJsonSchema(baseDiagram())
    expect(schema.required).toEqual(['stationId'])
  })

  it('marks the {id} attribute with x-core-primaryKey, others not', () => {
    const schema = exportToJsonSchema(baseDiagram())
    const properties = schema.properties as Record<string, Record<string, unknown>>
    // conceptual identity marker for adapters (PostGIS UPSERT, FROST reference, …)
    expect(properties.stationId['x-core-primaryKey']).toBe(true)
    expect(properties.temperature).not.toHaveProperty('x-core-primaryKey')
    expect(properties.tags).not.toHaveProperty('x-core-primaryKey')
  })

  it('does not emit x-core-primaryKey for an array-valued id attribute', () => {
    const diagram = baseDiagram({
      nodes: [
        {
          id: 'node-1',
          type: 'class',
          position: { x: 0, y: 0 },
          data: {
            element: {
              id: 'elem-1',
              name: 'TrafficSensor',
              type: 'class',
              attributes: [
                { id: 'a1', name: 'ids', type: 'String', visibility: 'public', isId: true, multiplicity: '*' },
              ],
              operations: [],
            },
            label: 'TrafficSensor',
          },
        },
      ],
    })

    const schema = exportToJsonSchema(diagram)
    const properties = schema.properties as Record<string, Record<string, unknown>>

    expect(properties.ids).toEqual({ type: 'array', items: { type: 'string' } })
    expect(properties.ids).not.toHaveProperty('x-core-primaryKey')
    // A many-valued isId cannot be a primary key, so it must not force required either.
    expect(schema.required ?? []).not.toContain('ids')
  })

  it('does not emit x-core-primaryKey or required for an optional (0..1) id attribute', () => {
    // A 0..1 isId is nullable, so it cannot back a primary key. The editor normally clears isId on
    // such an attribute, but an imported/edge-authored model may still carry it — the export must
    // gate on the multiplicity, not just on isId.
    const schema = exportToJsonSchema(
      baseDiagram({
        nodes: [
          {
            id: 'node-1',
            type: 'class',
            position: { x: 0, y: 0 },
            data: {
              element: {
                id: 'elem-1',
                name: 'TrafficSensor',
                type: 'class',
                attributes: [
                  {
                    id: 'a1',
                    name: 'optId',
                    type: 'String',
                    visibility: 'public',
                    isId: true,
                    multiplicity: '0..1',
                  },
                ],
                operations: [],
              },
              label: 'TrafficSensor',
            },
          },
        ],
      }),
    )
    const properties = schema.properties as Record<string, Record<string, unknown>>
    expect(properties.optId).not.toHaveProperty('x-core-primaryKey')
    expect(schema.required ?? []).not.toContain('optId')
  })

  it('marks every {id} attribute with x-core-primaryKey for a composite key', () => {
    const schema = exportToJsonSchema(
      baseDiagram({
        nodes: [
          {
            id: 'node-1',
            type: 'class',
            position: { x: 0, y: 0 },
            data: {
              element: {
                id: 'elem-1',
                name: 'Composite',
                type: 'class',
                attributes: [
                  { id: 'a1', name: 'tenant', type: 'String', visibility: 'public', isId: true },
                  { id: 'a2', name: 'id', type: 'String', visibility: 'public', isId: true },
                  {
                    id: 'a3',
                    name: 'value',
                    type: 'String',
                    visibility: 'public',
                    multiplicity: '0..1',
                  },
                ],
                operations: [],
              },
              label: 'Composite',
            },
          },
        ],
      }),
    )
    const properties = schema.properties as Record<string, Record<string, unknown>>
    expect(properties.tenant['x-core-primaryKey']).toBe(true)
    expect(properties.id['x-core-primaryKey']).toBe(true)
    expect(properties.value).not.toHaveProperty('x-core-primaryKey')
    expect(schema.required).toEqual(['tenant', 'id'])
  })

  it('carries documentation into description', () => {
    const schema = exportToJsonSchema(baseDiagram())
    expect(schema.description).toBe('A traffic sensor reading')
  })

  it('returns an empty object schema for an empty diagram', () => {
    const schema = exportToJsonSchema(baseDiagram({ nodes: [], edges: [] }))
    expect(schema.type).toBe('object')
    expect(schema.properties).toEqual({})
  })

  it('emits enumerations using the enum keyword', () => {
    const diagram = baseDiagram({
      nodes: [
        ...baseDiagram().nodes,
        {
          id: 'node-2',
          type: 'enumeration',
          position: { x: 200, y: 0 },
          data: {
            element: {
              id: 'elem-2',
              name: 'Status',
              type: 'enumeration',
              literals: [
                { id: 'l1', name: 'ACTIVE' },
                { id: 'l2', name: 'INACTIVE' },
              ],
            },
            label: 'Status',
          },
        },
      ],
    })

    const schema = exportToJsonSchema(diagram)
    const defs = schema.$defs as Record<string, Record<string, unknown>>
    expect(defs.Status.enum).toEqual(['ACTIVE', 'INACTIVE'])
  })

  it('emits $ref with crs sibling for a geometry attribute that has meta.gisInfo.crs', () => {
    const diagram = baseDiagram({
      nodes: [
        {
          id: 'node-1',
          type: 'class',
          position: { x: 0, y: 0 },
          data: {
            element: {
              id: 'elem-1',
              name: 'TrafficSensor',
              type: 'class',
              attributes: [
                {
                  id: 'a1',
                  name: 'location',
                  type: 'Point',
                  visibility: 'public',
                  meta: { gisInfo: { crs: 'EPSG:25832' } },
                },
              ],
              operations: [],
            },
            label: 'TrafficSensor',
          },
        },
      ],
    })

    const schema = exportToJsonSchema(diagram)
    const properties = schema.properties as Record<string, Record<string, unknown>>

    expect(properties.location).toEqual({
      $ref: 'https://geojson.org/schema/Point.json',
      crs: 'EPSG:25832',
    })
  })

  it('emits $ref without crs for a geometry attribute that has no meta.gisInfo', () => {
    const diagram = baseDiagram({
      nodes: [
        {
          id: 'node-1',
          type: 'class',
          position: { x: 0, y: 0 },
          data: {
            element: {
              id: 'elem-1',
              name: 'TrafficSensor',
              type: 'class',
              attributes: [
                {
                  id: 'a1',
                  name: 'location',
                  type: 'Point',
                  visibility: 'public',
                },
              ],
              operations: [],
            },
            label: 'TrafficSensor',
          },
        },
      ],
    })

    const schema = exportToJsonSchema(diagram)
    const properties = schema.properties as Record<string, Record<string, unknown>>

    expect(properties.location).toEqual({ $ref: 'https://geojson.org/schema/Point.json' })
    expect(properties.location.crs).toBeUndefined()
  })

  it('emits only enum keyword (no type) when the diagram contains only an enumeration', () => {
    const diagram = baseDiagram({
      nodes: [
        {
          id: 'node-1',
          type: 'enumeration',
          position: { x: 0, y: 0 },
          data: {
            element: {
              id: 'elem-1',
              name: 'Status',
              type: 'enumeration',
              literals: [
                { id: 'l1', name: 'ACTIVE' },
                { id: 'l2', name: 'INACTIVE' },
              ],
            },
            label: 'Status',
          },
        },
      ],
      edges: [],
    })

    const schema = exportToJsonSchema(diagram)

    expect(schema.enum).toEqual(['ACTIVE', 'INACTIVE'])
    expect(schema.type).toBeUndefined()
    expect(schema.properties).toBeUndefined()
    expect(schema.title).toBe('Status')
  })

  it('keeps a single-root layout when the diagram has multiple unconnected classes', () => {
    const diagram = baseDiagram({
      nodes: [
        {
          id: 'node-1',
          type: 'class',
          position: { x: 0, y: 0 },
          data: {
            element: {
              id: 'elem-1',
              name: 'Building',
              type: 'class',
              attributes: [{ id: 'a1', name: 'name', type: 'String', visibility: 'public' }],
              operations: [],
            },
            label: 'Building',
          },
        },
        {
          id: 'node-2',
          type: 'class',
          position: { x: 300, y: 0 },
          data: {
            element: {
              id: 'elem-2',
              name: 'Street',
              type: 'class',
              attributes: [{ id: 'a2', name: 'streetName', type: 'String', visibility: 'public' }],
              operations: [],
            },
            label: 'Street',
          },
        },
      ],
      edges: [],
    })

    const schema = exportToJsonSchema(diagram)

    // First class becomes the document root; the other is emitted under $defs.
    expect(schema.type).toBe('object')
    expect(schema.title).toBe('Building')
    const defs = schema.$defs as Record<string, Record<string, unknown>>
    expect(defs.Street).toBeDefined()
    expect(defs.Building).toBeUndefined()
  })

  it('enumerations alone do not count as roots — single class with an enum stays in single-root layout', () => {
    const diagram = baseDiagram({
      nodes: [
        ...baseDiagram().nodes,
        {
          id: 'node-enum',
          type: 'enumeration',
          position: { x: 300, y: 0 },
          data: {
            element: {
              id: 'elem-enum',
              name: 'SensorType',
              type: 'enumeration',
              literals: [
                { id: 'l1', name: 'TRAFFIC' },
                { id: 'l2', name: 'WEATHER' },
              ],
            },
            label: 'SensorType',
          },
        },
      ],
    })

    const schema = exportToJsonSchema(diagram)

    // Single non-enumeration class → single-root layout, type is present at root.
    expect(schema.type).toBe('object')
    expect(schema.title).toBe('TrafficSensor')

    // Enumeration is still emitted in $defs.
    const defs = schema.$defs as Record<string, Record<string, unknown>>
    expect(defs.SensorType.enum).toEqual(['TRAFFIC', 'WEATHER'])
  })

  it('links contained classes via $ref through composition', () => {
    const diagram = baseDiagram({
      nodes: [
        ...baseDiagram().nodes,
        {
          id: 'node-2',
          type: 'class',
          position: { x: 200, y: 0 },
          data: {
            element: {
              id: 'elem-2',
              name: 'Reading',
              type: 'class',
              attributes: [{ id: 'a1', name: 'value', type: 'Double', visibility: 'public' }],
              operations: [],
            },
            label: 'Reading',
          },
        },
      ],
      // Diamond at the target (TrafficSensor = container), so the part's role/multiplicity sit on
      // the source end.
      edges: [
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
              sourceRole: 'readings',
              sourceMultiplicity: '*',
            },
            label: '',
            isSelected: false,
            isDirty: false,
          },
        },
      ],
    })

    const schema = exportToJsonSchema(diagram)
    const properties = schema.properties as Record<string, Record<string, unknown>>
    expect(properties.readings).toEqual({ type: 'array', items: { $ref: '#/$defs/Reading' } })

    const defs = schema.$defs as Record<string, Record<string, unknown>>
    expect(defs.Reading).toBeDefined()
  })

  /** Second class node (Reading) reused by the aggregation/association cases below. */
  const readingNode = {
    id: 'node-2',
    type: 'class' as const,
    position: { x: 200, y: 0 },
    data: {
      element: {
        id: 'elem-2',
        name: 'Reading',
        type: 'class' as const,
        attributes: [{ id: 'a1', name: 'value', type: 'Double', visibility: 'public' as const }],
        operations: [],
      },
      label: 'Reading',
    },
  }

  it('embeds the source part into the target container for aggregation', () => {
    const diagram = baseDiagram({
      nodes: [...baseDiagram().nodes, readingNode],
      edges: [
        {
          id: 'edge-1',
          type: 'aggregation',
          source: 'node-2',
          target: 'node-1',
          data: {
            relationship: {
              id: 'rel-1',
              type: 'aggregation',
              source: 'elem-2',
              target: 'elem-1',
              sourceRole: 'readings',
            },
            label: '',
            isSelected: false,
            isDirty: false,
          },
        },
      ],
    })

    const schema = exportToJsonSchema(diagram)
    const properties = schema.properties as Record<string, Record<string, unknown>>
    expect(properties.readings).toEqual({ $ref: '#/$defs/Reading' })
    expect((schema.$defs as Record<string, Record<string, unknown>>).Reading).toMatchObject({ title: 'Reading' })
    expect(schema.title).toBe('TrafficSensor')
  })

  it('keeps the drawn direction for association (source references target)', () => {
    const diagram = baseDiagram({
      nodes: [...baseDiagram().nodes, readingNode],
      edges: [
        {
          id: 'edge-1',
          type: 'association',
          source: 'node-1',
          target: 'node-2',
          data: {
            relationship: {
              id: 'rel-1',
              type: 'association',
              source: 'elem-1',
              target: 'elem-2',
              targetRole: 'reading',
            },
            label: '',
            isSelected: false,
            isDirty: false,
          },
        },
      ],
    })

    const schema = exportToJsonSchema(diagram)
    const properties = schema.properties as Record<string, Record<string, unknown>>
    // Direction not flipped: source stays root and references the target.
    expect(properties.reading).toEqual({ $ref: '#/$defs/Reading' })
    expect((schema.$defs as Record<string, Record<string, unknown>>).Reading).toMatchObject({ title: 'Reading' })
    expect(schema.title).toBe('TrafficSensor')
  })

  const cls = (id: string, name: string, attrs: { id: string; name: string; type?: string }[]) => ({
    id: `node-${id}`,
    type: 'class' as const,
    position: { x: 0, y: 0 },
    data: {
      element: {
        id,
        name,
        type: 'class' as const,
        attributes: attrs.map(a => ({ visibility: 'public' as const, type: 'String', ...a })),
        operations: [],
      },
      label: name,
    },
  })

  const inhEdge = (
    id: string,
    source: string,
    target: string,
    type: 'inheritance' | 'realization' = 'inheritance',
  ) => ({
    id: `edge-${id}`,
    type,
    source: `node-${source}`,
    target: `node-${target}`,
    data: { relationship: { id: `rel-${id}`, type, source, target }, label: '', isSelected: false, isDirty: false },
  })

  it('roots on the subclass and emits allOf referencing the parent for inheritance', () => {
    const diagram = baseDiagram({
      name: 'Animal',
      nodes: [
        cls('animal', 'Animal', [{ id: 'a1', name: 'name' }]),
        cls('dog', 'Dog', [{ id: 'a2', name: 'breed' }]),
      ] as unknown as UMLDiagram['nodes'],
      edges: [inhEdge('1', 'dog', 'animal')],
    } as Partial<UMLDiagram>)

    const schema = exportToJsonSchema(diagram)
    expect(schema.title).toBe('Dog')
    expect(schema.allOf).toEqual([
      { $ref: '#/$defs/Animal' },
      expect.objectContaining({ type: 'object', title: 'Dog' }),
    ])
    expect((schema.$defs as Record<string, unknown>).Animal).toBeDefined()
  })

  it('handles realization like inheritance', () => {
    const diagram = baseDiagram({
      name: 'IFace',
      nodes: [
        cls('iface', 'IFace', [{ id: 'a1', name: 'y' }]),
        cls('impl', 'Impl', [{ id: 'a2', name: 'x' }]),
      ] as unknown as UMLDiagram['nodes'],
      edges: [inhEdge('1', 'impl', 'iface', 'realization')],
    } as Partial<UMLDiagram>)

    const schema = exportToJsonSchema(diagram)
    expect(schema.title).toBe('Impl')
    expect(schema.allOf).toEqual([{ $ref: '#/$defs/IFace' }, expect.objectContaining({ title: 'Impl' })])
  })

  it('roots on the leaf for multi-level inheritance', () => {
    const diagram = baseDiagram({
      name: 'Base',
      nodes: [
        cls('base', 'Base', [{ id: 'a1', name: 'a' }]),
        cls('mid', 'Mid', [{ id: 'a2', name: 'b' }]),
        cls('leaf', 'Leaf', [{ id: 'a3', name: 'c' }]),
      ] as unknown as UMLDiagram['nodes'],
      edges: [inhEdge('1', 'mid', 'base'), inhEdge('2', 'leaf', 'mid')],
    } as Partial<UMLDiagram>)

    const schema = exportToJsonSchema(diagram)
    expect(schema.title).toBe('Leaf')
    expect(schema.allOf).toEqual([{ $ref: '#/$defs/Mid' }, expect.objectContaining({ title: 'Leaf' })])
    expect((schema.$defs as Record<string, Record<string, unknown>>).Mid.allOf).toEqual([
      { $ref: '#/$defs/Base' },
      expect.objectContaining({ title: 'Mid' }),
    ])
  })

  it('emits one allOf parent ref per parent for multiple inheritance', () => {
    const diagram = baseDiagram({
      name: 'Leaf',
      nodes: [
        cls('p1', 'P1', [{ id: 'a1', name: 'x' }]),
        cls('p2', 'P2', [{ id: 'a2', name: 'y' }]),
        cls('leaf', 'Leaf', [{ id: 'a3', name: 'own' }]),
      ] as unknown as UMLDiagram['nodes'],
      edges: [inhEdge('1', 'leaf', 'p1'), inhEdge('2', 'leaf', 'p2')],
    } as Partial<UMLDiagram>)

    const schema = exportToJsonSchema(diagram)
    expect(schema.title).toBe('Leaf')
    expect(schema.allOf).toEqual([
      { $ref: '#/$defs/P1' },
      { $ref: '#/$defs/P2' },
      expect.objectContaining({ title: 'Leaf' }),
    ])
  })

  it('matches the root by diagram name case-insensitively', () => {
    const diagram = baseDiagram({
      name: 'beta',
      nodes: [
        cls('a', 'Alpha', [{ id: 'a1', name: 'a1' }]),
        cls('b', 'Beta', [{ id: 'a2', name: 'b1' }]),
      ] as unknown as UMLDiagram['nodes'],
      edges: [],
    } as Partial<UMLDiagram>)

    expect(exportToJsonSchema(diagram).title).toBe('Beta')
  })
})

describe('canMultiplicityBePrimaryKey', () => {
  it('is true only for exactly-one (1 or unset)', () => {
    expect(canMultiplicityBePrimaryKey('1')).toBe(true)
    expect(canMultiplicityBePrimaryKey(undefined)).toBe(true)
  })

  it('is false for optional (0..1) — a primary key cannot be nullable', () => {
    expect(canMultiplicityBePrimaryKey('0..1')).toBe(false)
  })

  it('is false for many multiplicities (unbounded and bounded)', () => {
    expect(canMultiplicityBePrimaryKey('*')).toBe(false)
    expect(canMultiplicityBePrimaryKey('1..*')).toBe(false)
    expect(canMultiplicityBePrimaryKey('2')).toBe(false)
    expect(canMultiplicityBePrimaryKey('1..5')).toBe(false)
  })
})
