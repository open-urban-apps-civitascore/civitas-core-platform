import { describe, expect, it } from 'vitest'

import { PROPERTY_CARDINALITY_VALUES, type PropertyCardinality } from '../constants/umlTypes'
import type { UMLDiagram } from '../types/diagram'
import {
  canMultiplicityBePrimaryKey,
  exportToJsonSchema,
  sanitizeName,
  sanitizePropertyKey,
  SchemaExportError,
} from './jsonSchemaExportService'

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
              isId: true,
            },
            {
              id: 'a2',
              name: 'temperature',
              type: 'Double',
              multiplicity: '0..1',
            },
            {
              id: 'a3',
              name: 'tags',
              type: 'String',
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

describe('sanitizePropertyKey', () => {
  it.each([
    ['attribut', 'Attribut'],
    ['Composition Edge', 'CompositionEdge'],
    ['mein feld', 'MeinFeld'],
    ['NeueAufzählung', 'NeueAufzaehlung'],
    ['Größe in m²', 'GroesseInM'],
    ['Bänke & Stühle', 'BaenkeStuehle'],
    ['air-quality.level', 'AirQualityLevel'],
    ['straße', 'Strasse'],
  ])('normalizes %o to %o', (input, expected) => {
    expect(sanitizePropertyKey(input)).toBe(expected)
  })

  it('keeps underscores, which are legal inside a key', () => {
    expect(sanitizePropertyKey('air_quality')).toBe('Air_quality')
  })

  it('drops leading underscores, which are not', () => {
    expect(sanitizePropertyKey('_id')).toBe('Id')
    expect(sanitizePropertyKey('__internal_value')).toBe('Internal_value')
  })

  it('prefixes a leading digit instead of stripping it, so distinct names stay distinct', () => {
    expect(sanitizePropertyKey('1Value')).toBe('N1Value')
    expect(sanitizePropertyKey('2Value')).toBe('N2Value')
  })

  it('returns empty when nothing usable remains, leaving the fallback to the caller', () => {
    expect(sanitizePropertyKey('___')).toBe('')
    expect(sanitizePropertyKey('!!!')).toBe('')
    expect(sanitizePropertyKey('')).toBe('')
  })
})

// The document root is always the (virtual) data structure; a class's own schema lives under $defs.
// This helper returns the named class definition so field-level assertions read against the class,
// not the virtual root.
const classDef = (schema: Record<string, unknown>, name: string): Record<string, unknown> =>
  (schema.$defs as Record<string, Record<string, unknown>>)[name]

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
    const def = classDef(exportToJsonSchema(baseDiagram()), 'TrafficSensor')
    const properties = def.properties as Record<string, Record<string, unknown>>

    expect(properties.StationId).toEqual({ type: 'string', 'x-core-primaryKey': true })
    expect(properties.Temperature).toEqual({ type: 'number' })
  })

  it('keeps attributes apart whose names sanitize to the same key', () => {
    const rootNode = baseDiagram().nodes[0]
    const diagram = baseDiagram({
      nodes: [
        {
          ...rootNode,
          data: {
            ...rootNode.data,
            element: {
              ...rootNode.data.element,
              attributes: [
                { id: 'a1', name: 'mein feld', type: 'String' },
                { id: 'a2', name: 'MeinFeld', type: 'Integer', multiplicity: '0..1' },
              ],
            },
          },
        },
      ] as unknown as UMLDiagram['nodes'],
    })

    const properties = classDef(exportToJsonSchema(diagram), 'TrafficSensor').properties as Record<string, unknown>

    // Without the collision suffix the second attribute would silently overwrite the first.
    expect(properties.MeinFeld).toEqual({ type: 'string' })
    expect(properties.MeinFeld_1).toEqual({ type: 'integer' })
  })

  it('maps "*" multiplicity attributes to arrays', () => {
    const def = classDef(exportToJsonSchema(baseDiagram()), 'TrafficSensor')
    const properties = def.properties as Record<string, Record<string, unknown>>

    expect(properties.Tags).toEqual({ type: 'array', items: { type: 'string' } })
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
                attributes: [{ id: 'a1', name: 'tags', type: 'String', multiplicity }],
                operations: [],
              },
              label: 'TrafficSensor',
            },
          },
        ],
      })

      const def = classDef(exportToJsonSchema(diagram), 'TrafficSensor')
      const properties = def.properties as Record<string, Record<string, unknown>>

      expect(properties.Tags).toEqual(expectedProp)
      if (required) {
        expect((def.required as string[]) ?? []).toContain('Tags')
      } else {
        expect((def.required as string[]) ?? []).not.toContain('Tags')
      }
    },
  )

  it('treats an unset multiplicity as a single required scalar (backwards compatible)', () => {
    const def = classDef(exportToJsonSchema(baseDiagram()), 'TrafficSensor')
    const properties = def.properties as Record<string, Record<string, unknown>>

    // stationId has no multiplicity set -> plain scalar, and required.
    expect(properties.StationId).toEqual({ type: 'string', 'x-core-primaryKey': true })
    expect(def.required).toContain('StationId')
  })

  it('adds id attributes to required', () => {
    const def = classDef(exportToJsonSchema(baseDiagram()), 'TrafficSensor')
    expect(def.required).toEqual(['StationId'])
  })

  it('marks the {id} attribute with x-core-primaryKey, others not', () => {
    const def = classDef(exportToJsonSchema(baseDiagram()), 'TrafficSensor')
    const properties = def.properties as Record<string, Record<string, unknown>>
    // conceptual identity marker for adapters (PostGIS UPSERT, FROST reference, …)
    expect(properties.StationId['x-core-primaryKey']).toBe(true)
    expect(properties.Temperature).not.toHaveProperty('x-core-primaryKey')
    expect(properties.Tags).not.toHaveProperty('x-core-primaryKey')
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
              attributes: [{ id: 'a1', name: 'ids', type: 'String', isId: true, multiplicity: '*' }],
              operations: [],
            },
            label: 'TrafficSensor',
          },
        },
      ],
    })

    const def = classDef(exportToJsonSchema(diagram), 'TrafficSensor')
    const properties = def.properties as Record<string, Record<string, unknown>>

    expect(properties.Ids).toEqual({ type: 'array', items: { type: 'string' } })
    expect(properties.Ids).not.toHaveProperty('x-core-primaryKey')
    // A many-valued isId cannot be a primary key, so it must not force required either.
    expect((def.required as string[]) ?? []).not.toContain('ids')
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
    const def = classDef(schema, 'TrafficSensor')
    const properties = def.properties as Record<string, Record<string, unknown>>
    expect(properties.OptId).not.toHaveProperty('x-core-primaryKey')
    expect((def.required as string[]) ?? []).not.toContain('optId')
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
                  { id: 'a1', name: 'tenant', type: 'String', isId: true },
                  { id: 'a2', name: 'id', type: 'String', isId: true },
                  {
                    id: 'a3',
                    name: 'value',
                    type: 'String',
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
    const def = classDef(schema, 'Composite')
    const properties = def.properties as Record<string, Record<string, unknown>>
    expect(properties.Tenant['x-core-primaryKey']).toBe(true)
    expect(properties.Id['x-core-primaryKey']).toBe(true)
    expect(properties.Value).not.toHaveProperty('x-core-primaryKey')
    expect(def.required).toEqual(['Tenant', 'Id'])
  })

  it('carries documentation into description', () => {
    const def = classDef(exportToJsonSchema(baseDiagram()), 'TrafficSensor')
    expect(def.description).toBe('A traffic sensor reading')
  })

  it('returns an empty object schema for an empty diagram', () => {
    const schema = exportToJsonSchema(baseDiagram({ nodes: [], edges: [] }))
    expect(schema.type).toBe('object')
    expect(schema.properties).toEqual({})
  })

  it('emits enumerations using the enum keyword', () => {
    // The root reaches the enum through an attribute typed by it; association edges are out of scope
    // and would leave the enum unreachable.
    const rootNode = baseDiagram().nodes[0]
    const diagram = baseDiagram({
      nodes: [
        {
          ...rootNode,
          data: {
            ...rootNode.data,
            element: {
              ...rootNode.data.element,
              attributes: [
                ...rootNode.data.element.attributes,
                { id: 'a4', name: 'status', type: { id: 'elem-2' }, visibility: 'public' },
              ],
            },
          },
        },
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

    const def = classDef(exportToJsonSchema(diagram), 'TrafficSensor')
    const properties = def.properties as Record<string, Record<string, unknown>>

    expect(properties.Location).toEqual({
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
                },
              ],
              operations: [],
            },
            label: 'TrafficSensor',
          },
        },
      ],
    })

    const def = classDef(exportToJsonSchema(diagram), 'TrafficSensor')
    const properties = def.properties as Record<string, Record<string, unknown>>

    expect(properties.Location).toEqual({ $ref: 'https://geojson.org/schema/Point.json' })
    expect(properties.Location.crs).toBeUndefined()
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

  it('rejects a diagram with several unconnected root classes', () => {
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
              attributes: [{ id: 'a1', name: 'name', type: 'String' }],
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
              attributes: [{ id: 'a2', name: 'streetName', type: 'String' }],
              operations: [],
            },
            label: 'Street',
          },
        },
      ],
      edges: [],
    })

    // Several unconnected root classes leave no single record shape to derive — the export
    // refuses with the candidates named instead of persisting a guessed schema.
    let thrown: unknown
    try {
      exportToJsonSchema(diagram)
    } catch (error) {
      thrown = error
    }
    expect(thrown).toBeInstanceOf(SchemaExportError)
    expect((thrown as SchemaExportError).failure).toEqual({
      code: 'ambiguousRoot',
      candidateNames: ['Building', 'Street'],
    })
  })

  it('enumerations alone do not count as roots — single class stays the root class', () => {
    // The class reaches the enum through an attribute typed by it, keeping it reachable without an
    // out-of-scope association edge.
    const rootNode = baseDiagram().nodes[0]
    const diagram = baseDiagram({
      nodes: [
        {
          ...rootNode,
          data: {
            ...rootNode.data,
            element: {
              ...rootNode.data.element,
              attributes: [
                ...rootNode.data.element.attributes,
                { id: 'a4', name: 'sensorType', type: { id: 'elem-enum' }, visibility: 'public' },
              ],
            },
          },
        },
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

    // The single non-enumeration class is the root class, referenced from the data-structure root.
    expect(schema.type).toBe('object')
    expect(schema.title).toBe('TrafficSensor')
    expect(schema.properties).toEqual({ TrafficSensor: { $ref: '#/$defs/TrafficSensor' } })

    // Enumeration is still emitted in $defs.
    const defs = schema.$defs as Record<string, Record<string, unknown>>
    expect(defs.SensorType.enum).toEqual(['TRAFFIC', 'WEATHER'])
  })

  it('links an enumeration via $ref through composition', () => {
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
              name: 'Quality',
              type: 'enumeration',
              literals: [
                { id: 'l1', name: 'GOOD' },
                { id: 'l2', name: 'POOR' },
              ],
            },
            label: 'Quality',
          },
        },
      ],
      // Diamond at the target (TrafficSensor = container), the enumeration is the part.
      edges: [
        {
          id: 'edge-enum',
          type: 'composition',
          source: 'node-enum',
          target: 'node-1',
          data: {
            relationship: {
              id: 'rel-enum',
              type: 'composition',
              source: 'elem-enum',
              target: 'elem-1',
              sourceRole: 'level',
            },
            label: '',
            isSelected: false,
            isDirty: false,
          },
        },
      ],
    })

    const schema = exportToJsonSchema(diagram)
    const properties = classDef(schema, 'TrafficSensor').properties as Record<string, Record<string, unknown>>
    expect(properties.Level).toEqual({ $ref: '#/$defs/Quality' })

    const defs = schema.$defs as Record<string, Record<string, unknown>>
    expect(defs.Quality).toEqual({ title: 'Quality', enum: ['GOOD', 'POOR'] })
  })

  it('names a role-less composition property after the part, ignoring the relationship name', () => {
    const diagram = baseDiagram({
      nodes: [...baseDiagram().nodes, readingNode],
      edges: [
        {
          id: 'edge-1',
          type: 'composition',
          source: 'node-2',
          target: 'node-1',
          data: {
            // The edge label a user typed in the inspector; it must not become the JSON key.
            relationship: {
              id: 'rel-1',
              name: 'Composition Edge',
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
    })

    const properties = classDef(exportToJsonSchema(diagram), 'TrafficSensor').properties as Record<string, unknown>
    expect(properties).toHaveProperty('Reading')
    expect(properties).not.toHaveProperty('Composition Edge')
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
              attributes: [{ id: 'a1', name: 'value', type: 'Double' }],
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

    const def = classDef(exportToJsonSchema(diagram), 'TrafficSensor')
    const properties = def.properties as Record<string, Record<string, unknown>>
    expect(properties.Readings).toEqual({ type: 'array', items: { $ref: '#/$defs/Reading' } })

    const defs = exportToJsonSchema(diagram).$defs as Record<string, Record<string, unknown>>
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
        attributes: [{ id: 'a1', name: 'value', type: 'Double' as const }],
        operations: [],
      },
      label: 'Reading',
    },
  }

  const outOfScopeEdge = (type: 'association' | 'aggregation' | 'dependency') => ({
    id: 'edge-1',
    type,
    source: 'node-1',
    target: 'node-2',
    data: {
      relationship: { id: 'rel-1', type, source: 'elem-1', target: 'elem-2', targetRole: 'reading' },
      label: '',
      isSelected: false,
      isDirty: false,
    },
  })

  /**
   * Composition that embeds Reading into TrafficSensor under `sensorReading`. Strict single-root
   * export needs every class reachable and non-competing, so the second class can no longer float
   * disconnected — this in-scope edge anchors it while the out-of-scope edge under test still
   * contributes nothing.
   */
  const readingCompositionEdge = {
    id: 'edge-comp',
    type: 'composition' as const,
    source: 'node-2',
    target: 'node-1',
    data: {
      relationship: {
        id: 'rel-comp',
        type: 'composition' as const,
        source: 'elem-2',
        target: 'elem-1',
        sourceRole: 'sensorReading',
      },
      label: '',
      isSelected: false,
      isDirty: false,
    },
  }

  it.each(['association', 'aggregation', 'dependency'] as const)(
    'ignores out-of-scope relationship type %s: the edge contributes no property and export still succeeds',
    type => {
      const diagram = baseDiagram({
        nodes: [...baseDiagram().nodes, readingNode],
        edges: [readingCompositionEdge, outOfScopeEdge(type)],
      })

      const schema = exportToJsonSchema(diagram)
      // The out-of-scope edge is dropped, so its `reading` role adds no property; only the
      // composition's `sensorReading` reference to Reading survives. Both classes are emitted.
      const properties = classDef(schema, 'TrafficSensor').properties as Record<string, unknown>
      expect(Object.keys(properties)).not.toContain('reading')
      expect(properties.SensorReading).toEqual({ $ref: '#/$defs/Reading' })
      const defs = schema.$defs as Record<string, Record<string, unknown>>
      expect(defs.TrafficSensor).toBeDefined()
      expect(defs.Reading).toBeDefined()
    },
  )

  it('maps a supported composition while ignoring an out-of-scope association in the same diagram', () => {
    // Realistic legacy shape: TrafficSensor composes Reading (in scope) and also has a stray
    // association to a third class (out of scope). The composition maps; the association is dropped.
    const strayNode = {
      id: 'node-3',
      type: 'class' as const,
      position: { x: 400, y: 0 },
      data: {
        element: {
          id: 'elem-3',
          name: 'Owner',
          type: 'class' as const,
          attributes: [{ id: 'a1', name: 'orgName', type: 'String' as const }],
          operations: [],
        },
        label: 'Owner',
      },
    }
    // Owner is also composed into TrafficSensor (in scope, role `ownerRef`) so every class is
    // reachable and non-competing under strict rooting; the out-of-scope association is what must
    // contribute nothing.
    const diagram = baseDiagram({
      nodes: [...baseDiagram().nodes, readingNode, strayNode],
      edges: [
        // Composition: Reading (part/source) into TrafficSensor (container/target).
        {
          id: 'edge-comp',
          type: 'composition',
          source: 'node-2',
          target: 'node-1',
          data: {
            relationship: {
              id: 'rel-comp',
              type: 'composition',
              source: 'elem-2',
              target: 'elem-1',
              sourceRole: 'reading',
            },
            label: '',
            isSelected: false,
            isDirty: false,
          },
        },
        // Composition: Owner (part/source) into TrafficSensor (container/target).
        {
          id: 'edge-owner',
          type: 'composition',
          source: 'node-3',
          target: 'node-1',
          data: {
            relationship: {
              id: 'rel-owner',
              type: 'composition',
              source: 'elem-3',
              target: 'elem-1',
              sourceRole: 'ownerRef',
            },
            label: '',
            isSelected: false,
            isDirty: false,
          },
        },
        // Out-of-scope association from TrafficSensor to Owner.
        {
          id: 'edge-assoc',
          type: 'association',
          source: 'node-1',
          target: 'node-3',
          data: {
            relationship: {
              id: 'rel-assoc',
              type: 'association',
              source: 'elem-1',
              target: 'elem-3',
              targetRole: 'owner',
            },
            label: '',
            isSelected: false,
            isDirty: false,
          },
        },
      ],
    })

    const schema = exportToJsonSchema(diagram)
    const properties = classDef(schema, 'TrafficSensor').properties as Record<string, unknown>
    expect(properties.Reading).toEqual({ $ref: '#/$defs/Reading' })
    expect(Object.keys(properties)).not.toContain('owner')
    // All three classes still emitted; only the association contributes nothing.
    const defs = schema.$defs as Record<string, Record<string, unknown>>
    expect(defs.Reading).toBeDefined()
    expect(defs.Owner).toBeDefined()
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
        attributes: attrs.map(a => ({ type: 'String', ...a })),
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
    // Title is the data structure (diagram name); the subclass Dog is the root class referenced
    // from it, and its allOf lives in $defs.
    expect(schema.title).toBe('Animal')
    expect(schema.properties).toEqual({ Dog: { $ref: '#/$defs/Dog' } })
    expect(classDef(schema, 'Dog').allOf).toEqual([
      { $ref: '#/$defs/Animal' },
      expect.objectContaining({ type: 'object', title: 'Dog' }),
    ])
    expect((schema.$defs as Record<string, unknown>).Animal).toBeDefined()
  })

  it('ignores realization rather than mapping it like inheritance', () => {
    // IFace composes Impl (in scope) so a single root resolves and Impl is reachable. The
    // realization edge on top is out of scope: Impl gets no allOf parent and stays a plain object.
    const diagram = baseDiagram({
      name: 'IFace',
      nodes: [
        cls('iface', 'IFace', [{ id: 'a1', name: 'y' }]),
        cls('impl', 'Impl', [{ id: 'a2', name: 'x' }]),
      ] as unknown as UMLDiagram['nodes'],
      edges: [
        {
          id: 'edge-comp',
          type: 'composition',
          source: 'node-impl',
          target: 'node-iface',
          data: {
            relationship: { id: 'rel-comp', type: 'composition', source: 'impl', target: 'iface', sourceRole: 'impl' },
            label: '',
            isSelected: false,
            isDirty: false,
          },
        },
        inhEdge('1', 'impl', 'iface', 'realization'),
      ],
    } as Partial<UMLDiagram>)

    const schema = exportToJsonSchema(diagram)
    expect(classDef(schema, 'Impl').allOf).toBeUndefined()
    expect(classDef(schema, 'Impl').type).toBe('object')
    expect((schema.$defs as Record<string, unknown>).IFace).toBeDefined()
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
    expect(schema.title).toBe('Base')
    expect(schema.properties).toEqual({ Leaf: { $ref: '#/$defs/Leaf' } })
    expect(classDef(schema, 'Leaf').allOf).toEqual([
      { $ref: '#/$defs/Mid' },
      expect.objectContaining({ title: 'Leaf' }),
    ])
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
    // Diagram is named after the leaf, but the document root is still the (virtual) data structure;
    // the leaf is its root-class $ref property and carries the allOf in $defs.
    expect(schema.title).toBe('Leaf')
    expect(schema.properties).toEqual({ Leaf: { $ref: '#/$defs/Leaf' } })
    expect(classDef(schema, 'Leaf').allOf).toEqual([
      { $ref: '#/$defs/P1' },
      { $ref: '#/$defs/P2' },
      expect.objectContaining({ title: 'Leaf' }),
    ])
  })

  it('rejects a class the root cannot reach, naming root and stray', () => {
    const diagram = baseDiagram({
      name: 'S',
      nodes: [
        cls('a', 'Root', [{ id: 'a1', name: 'a1' }]),
        cls('b', 'Part', [{ id: 'a2', name: 'b1' }]),
        cls('c', 'Stray', [{ id: 'a3', name: 'c1' }]),
      ] as unknown as UMLDiagram['nodes'],
      edges: [
        {
          id: 'edge-1',
          type: 'composition',
          source: 'node-b',
          target: 'node-a',
          data: {
            relationship: { id: 'rel-1', type: 'composition', source: 'b', target: 'a', sourceRole: 'parts' },
            label: '',
            isSelected: false,
            isDirty: false,
          },
        },
        {
          id: 'edge-2',
          type: 'composition',
          source: 'node-b',
          target: 'node-c',
          data: {
            relationship: { id: 'rel-2', type: 'composition', source: 'b', target: 'c', sourceRole: 'parts' },
            label: '',
            isSelected: false,
            isDirty: false,
          },
        },
      ] as unknown as UMLDiagram['edges'],
    } as Partial<UMLDiagram>)

    // Part hangs under both Root and Stray — no class is fully unconnected, yet no single record
    // shape exists either. Two non-embedded classes remain, so the export rejects as ambiguous.
    let thrown: unknown
    try {
      exportToJsonSchema(diagram)
    } catch (error) {
      thrown = error
    }
    expect(thrown).toBeInstanceOf(SchemaExportError)
    expect((thrown as SchemaExportError).failure).toEqual({
      code: 'ambiguousRoot',
      candidateNames: ['Root', 'Stray'],
    })
  })

  it('rejects an isolated enumeration as unreachable from the root class', () => {
    const diagram = baseDiagram({
      name: 'S',
      nodes: [
        cls('a', 'Root', [{ id: 'a1', name: 'a1' }]),
        {
          id: 'node-e1',
          type: 'enumeration',
          position: { x: 0, y: 0 },
          data: {
            element: {
              id: 'e1',
              name: 'Status',
              type: 'enumeration',
              literals: [{ id: 'l1', name: 'ON' }],
            },
            label: 'Status',
          },
        },
      ] as unknown as UMLDiagram['nodes'],
      edges: [],
    } as Partial<UMLDiagram>)

    let thrown: unknown
    try {
      exportToJsonSchema(diagram)
    } catch (error) {
      thrown = error
    }
    expect(thrown).toBeInstanceOf(SchemaExportError)
    expect((thrown as SchemaExportError).failure).toEqual({
      code: 'unreachable',
      rootName: 'Root',
      unreachableNames: ['Status'],
    })
  })

  it('rejects several enumerations without any class as ambiguous', () => {
    const diagram = baseDiagram({
      name: 'S',
      nodes: [
        {
          id: 'node-e1',
          type: 'enumeration',
          position: { x: 0, y: 0 },
          data: {
            element: {
              id: 'e1',
              name: 'Status',
              type: 'enumeration',
              literals: [{ id: 'l1', name: 'ON' }],
            },
            label: 'Status',
          },
        },
        {
          id: 'node-e2',
          type: 'enumeration',
          position: { x: 0, y: 0 },
          data: {
            element: {
              id: 'e2',
              name: 'Kind',
              type: 'enumeration',
              literals: [{ id: 'l2', name: 'A' }],
            },
            label: 'Kind',
          },
        },
      ] as unknown as UMLDiagram['nodes'],
      edges: [],
    } as Partial<UMLDiagram>)

    let thrown: unknown
    try {
      exportToJsonSchema(diagram)
    } catch (error) {
      thrown = error
    }
    expect(thrown).toBeInstanceOf(SchemaExportError)
    expect((thrown as SchemaExportError).failure).toEqual({
      code: 'ambiguousRoot',
      candidateNames: ['Status', 'Kind'],
    })
  })

  it('keeps $defs keys free of JSON-Pointer-special characters', () => {
    const diagram = baseDiagram({
      name: 'S',
      nodes: [cls('a', 'Road/Segment~Part', [{ id: 'a1', name: 'a1' }])] as unknown as UMLDiagram['nodes'],
      edges: [],
    } as Partial<UMLDiagram>)

    const schema = exportToJsonSchema(diagram)
    // In a def key, '~' would make '#/$defs/<key>' an invalid JSON Pointer and '/' a valid one
    // that resolves to the wrong, nested location.
    expect(schema.properties).toEqual({ RoadSegmentPart: { $ref: '#/$defs/Road-Segment-Part' } })
    expect((schema.$defs as Record<string, unknown>)['Road-Segment-Part']).toBeDefined()
  })

  it('exports the designated root (isRoot) when the derivation alone would be ambiguous', () => {
    // Beta is referenced only as an attribute type: reachable from Alpha, but not embedded by any
    // edge — without the designation both classes would be root candidates.
    const alpha = cls('a', 'Alpha', [{ id: 'a1', name: 'beta', type: { id: 'b' } as unknown as string }])
    ;(alpha.data.element as { isRoot?: boolean }).isRoot = true
    const diagram = baseDiagram({
      name: 'S',
      nodes: [alpha, cls('b', 'Beta', [{ id: 'a2', name: 'b1' }])] as unknown as UMLDiagram['nodes'],
      edges: [],
    } as Partial<UMLDiagram>)

    const schema = exportToJsonSchema(diagram)
    expect(schema.properties).toEqual({ Alpha: { $ref: '#/$defs/Alpha' } })
    expect((classDef(schema, 'Alpha').properties as Record<string, unknown>).Beta).toEqual({ $ref: '#/$defs/Beta' })
  })

  it('does not collapse an ambiguous diagram onto a diagram-name-matching class', () => {
    const diagram = baseDiagram({
      name: 'beta',
      nodes: [
        cls('a', 'Alpha', [{ id: 'a1', name: 'a1' }]),
        cls('b', 'Beta', [{ id: 'a2', name: 'b1' }]),
      ] as unknown as UMLDiagram['nodes'],
      edges: [],
    } as Partial<UMLDiagram>)

    // A class named like the diagram must not silently win — the ambiguity is the user's to fix.
    let thrown: unknown
    try {
      exportToJsonSchema(diagram)
    } catch (error) {
      thrown = error
    }
    expect(thrown).toBeInstanceOf(SchemaExportError)
    expect((thrown as SchemaExportError).failure).toEqual({
      code: 'ambiguousRoot',
      candidateNames: ['Alpha', 'Beta'],
    })
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
