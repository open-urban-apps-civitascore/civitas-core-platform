import { describe, expect, it } from 'vitest'

import type { UMLDiagram, UMLNode } from '../types/diagram'
import type { UMLElement } from '../types/uml'
import { exportToJsonSchema } from './jsonSchemaExportService'
import { diagramFromJsonSchema, importFromJsonSchema, SchemaImportError } from './jsonSchemaImportService'

type JsonSchemaObject = Record<string, unknown>

const node = (element: UMLElement, position = { x: 0, y: 0 }): UMLNode =>
  ({ id: `node-${element.id}`, type: element.type, position, data: { element, label: element.name } }) as UMLNode

const diagram = (elements: UMLElement[], edges: UMLDiagram['edges'] = []): UMLDiagram => ({
  id: 'diagram-1',
  name: 'Structure',
  nodes: elements.map(element => node(element)),
  edges,
  lastModified: new Date('2026-01-01'),
  isDirty: false,
})

const composition = (
  partId: string,
  containerId: string,
  multiplicity: string,
  role?: string,
): UMLDiagram['edges'][number] =>
  ({
    id: `edge-${partId}-${containerId}`,
    source: partId,
    target: containerId,
    type: 'composition',
    data: {
      relationship: {
        id: `rel-${partId}-${containerId}`,
        type: 'composition',
        source: partId,
        target: containerId,
        sourceMultiplicity: multiplicity,
        ...(role ? { sourceRole: role } : {}),
      },
    },
  }) as UMLDiagram['edges'][number]

const inheritance = (childId: string, parentId: string): UMLDiagram['edges'][number] =>
  ({
    id: `edge-${childId}-${parentId}`,
    source: childId,
    target: parentId,
    type: 'inheritance',
    data: {
      relationship: { id: `rel-${childId}-${parentId}`, type: 'inheritance', source: childId, target: parentId },
    },
  }) as UMLDiagram['edges'][number]

/**
 * Exports a diagram, reads it back, and exports again. Two diagrams cannot be compared — the
 * import mints its own identifiers and places the classes itself — but two documents can, and the
 * document is what the platform stores and every reader interprets.
 */
const roundTrip = (source: UMLDiagram): { first: JsonSchemaObject; second: JsonSchemaObject } => {
  const first = exportToJsonSchema(source)
  const second = exportToJsonSchema(diagramFromJsonSchema(first, source.name))
  return { first, second }
}

const expectRoundTrip = (source: UMLDiagram) => {
  const { first, second } = roundTrip(source)
  expect(second).toEqual(first)
}

describe('importFromJsonSchema — round trip against the export', () => {
  it('keeps a class with its attributes, types and multiplicities', () => {
    expectRoundTrip(
      diagram([
        {
          id: 'e1',
          name: 'Station',
          type: 'class',
          documentation: 'A measuring station',
          attributes: [
            { id: 'a1', name: 'reference', type: 'String', isId: true, multiplicity: '1..1' },
            { id: 'a2', name: 'height', type: 'Number', multiplicity: '0..1' },
            { id: 'a3', name: 'builtAt', type: 'DateTime', multiplicity: '1..1' },
            { id: 'a4', name: 'active', type: 'Boolean', multiplicity: '1..1' },
            { id: 'a5', name: 'floors', type: 'Integer', multiplicity: '1..1' },
            { id: 'a6', name: 'key', type: 'Uuid', multiplicity: '1..1' },
            { id: 'a7', name: 'since', type: 'Date', multiplicity: '0..1' },
            { id: 'a8', name: 'tags', type: 'String', multiplicity: '*' },
            { id: 'a9', name: 'codes', type: 'String', multiplicity: '2..5' },
          ],
          operations: [],
        },
      ]),
    )
  })

  it('keeps a geometry attribute with its reference system', () => {
    expectRoundTrip(
      diagram([
        {
          id: 'e1',
          name: 'Site',
          type: 'class',
          attributes: [
            {
              id: 'a1',
              name: 'location',
              type: 'Point',
              multiplicity: '1..1',
              meta: { gisInfo: { crs: 'EPSG:25832' } },
            },
            { id: 'a2', name: 'area', type: 'Polygon', multiplicity: '0..1' },
          ],
          operations: [],
        },
      ]),
    )
  })

  it('keeps a default value', () => {
    expectRoundTrip(
      diagram([
        {
          id: 'e1',
          name: 'Sensor',
          type: 'class',
          attributes: [{ id: 'a1', name: 'unit', type: 'String', multiplicity: '1..1', defaultValue: 'degC' }],
          operations: [],
        },
      ]),
    )
  })

  it('keeps a composition, its role and its multiplicity', () => {
    expectRoundTrip(
      diagram(
        [
          { id: 'e1', name: 'Thing', type: 'class', attributes: [], operations: [], isRoot: true },
          {
            id: 'e2',
            name: 'Datastream',
            type: 'class',
            attributes: [{ id: 'a1', name: 'name', type: 'String', multiplicity: '1..1' }],
            operations: [],
          },
          { id: 'e3', name: 'Location', type: 'class', attributes: [], operations: [] },
        ],
        [
          // A collection under a role that is not the part's own name, and a mandatory single part.
          composition('e2', 'e1', '1..*', 'datastreams'),
          composition('e3', 'e1', '1..1'),
        ],
      ),
    )
  })

  it('keeps inheritance', () => {
    expectRoundTrip(
      diagram(
        [
          {
            id: 'e1',
            name: 'Measurement',
            type: 'class',
            attributes: [{ id: 'a1', name: 'result', type: 'Number', multiplicity: '1..1' }],
            operations: [],
            isRoot: true,
          },
          {
            id: 'e2',
            name: 'Reading',
            type: 'class',
            attributes: [{ id: 'a2', name: 'unit', type: 'String', multiplicity: '1..1' }],
            operations: [],
          },
        ],
        [inheritance('e1', 'e2')],
      ),
    )
  })

  it('keeps an enumeration, including a numeric literal', () => {
    expectRoundTrip(
      diagram(
        [
          { id: 'e1', name: 'Station', type: 'class', attributes: [], operations: [], isRoot: true },
          {
            id: 'e2',
            name: 'Quality',
            type: 'enumeration',
            literals: [
              { id: 'l1', name: 'good' },
              { id: 'l2', name: 'poor', value: 'bad' },
              { id: 'l3', name: 'one', value: 1 },
            ],
          },
        ],
        [composition('e2', 'e1', '0..1')],
      ),
    )
  })

  it('keeps the root designation', () => {
    const { first, second } = roundTrip(
      diagram(
        [
          { id: 'e1', name: 'Thing', type: 'class', attributes: [], operations: [], isRoot: true },
          { id: 'e2', name: 'Part', type: 'class', attributes: [], operations: [] },
        ],
        [composition('e2', 'e1', '0..1')],
      ),
    )

    expect(first.$ref).toBe('#/$defs/Thing')
    expect(second.$ref).toBe('#/$defs/Thing')
  })

  it('keeps an external reference', () => {
    expectRoundTrip(
      diagram([
        {
          id: 'e1',
          name: 'Station',
          type: 'class',
          attributes: [
            {
              id: 'a1',
              name: 'owner',
              type: { id: 't1', name: 'Party', isExternal: true, href: 'https://example.org/party.json' },
              multiplicity: '1..1',
            },
          ],
          operations: [],
        },
      ]),
    )
  })
})

describe('importFromJsonSchema', () => {
  const structure = {
    $id: 'urn:core:platform:civitas:datastructure:frost:Things:4eg5hgeyxv',
    title: 'Things',
    type: 'object',
    properties: { thing: { $ref: '#/$defs/Thing' } },
    $defs: {
      Thing: {
        type: 'object',
        title: 'Thing',
        properties: {
          name: { type: 'string' },
          properties: { $ref: '#/$defs/ThingProperties' },
        },
        required: ['name', 'properties'],
      },
      ThingProperties: {
        type: 'object',
        title: 'ThingProperties',
        properties: { reference: { type: 'string', 'x-core-primaryKey': true } },
        required: ['reference'],
      },
    },
  }

  it('reads a published port structure', () => {
    const imported = importFromJsonSchema(structure)

    expect(imported.nodes.map(n => n.data.element.name)).toEqual(['Thing', 'ThingProperties'])
    const thing = imported.nodes[0].data.element
    // The bag is a class of its own, so the reference is a field with a type, and the link to it
    // is a composition.
    expect(imported.edges).toHaveLength(1)
    expect(imported.edges[0].type).toBe('composition')
    expect(thing.id).toBe(imported.edges[0].target)
  })

  it('marks the reference as the key', () => {
    const imported = importFromJsonSchema(structure)
    const bag = imported.nodes[1].data.element

    expect(bag.type).toBe('class')
    const reference = bag.type === 'class' ? bag.attributes[0] : null
    expect(reference?.isId).toBe(true)
    expect(reference?.multiplicity).toBe('1..1')
  })

  it('places the classes in rows from the root, and offsets them on request', () => {
    const imported = importFromJsonSchema(structure, { origin: { x: 100, y: 50 } })

    // The root first, its part in the row below: a chain reads top to bottom.
    expect(imported.nodes[0].position).toEqual({ x: 100, y: 50 })
    expect(imported.nodes[1].position.y).toBeGreaterThan(imported.nodes[0].position.y)
  })

  it('refuses a construct the platform does not write', () => {
    const alien = {
      $defs: {
        Thing: { type: 'object', title: 'Thing', properties: { when: { type: 'timestamp' } } },
      },
    }

    expect(() => importFromJsonSchema(alien)).toThrow(SchemaImportError)
    // The message names the construct, so the modeller learns what stopped the import.
    expect(() => importFromJsonSchema(alien)).toThrow(/timestamp/)
  })

  it('refuses a reference that leaves the document', () => {
    const dangling = {
      $defs: {
        Thing: { type: 'object', title: 'Thing', allOf: [{ $ref: 'https://example.org/other.json' }, {}] },
      },
    }

    expect(() => importFromJsonSchema(dangling)).toThrow(/inherits from outside/)
  })
})

describe('importFromJsonSchema — designated root and inherited shapes', () => {
  it('takes a wrapper that names its member by URN as the root, without a class of its own', () => {
    const urn = 'urn:core:platform:civitas:element:frost:Thing:0123456789:1.0.0'
    const document: JsonSchemaObject = {
      type: 'object',
      title: 'Wrapper',
      properties: { root: { $ref: urn } },
      $defs: { Thing: { $id: urn, type: 'object', title: 'Thing', properties: { name: { type: 'string' } } } },
    }

    const imported = importFromJsonSchema(document)

    expect(imported.nodes.map(n => n.data.element.name)).toEqual(['Thing'])
    expect(imported.nodes[0].data.element.isRoot).toBe(true)
  })

  it('lifts an inline object from the own shape of a class that inherits', () => {
    const document: JsonSchemaObject = {
      $ref: '#/$defs/Station',
      $defs: {
        Base: { type: 'object', title: 'Base', properties: { name: { type: 'string' } } },
        Station: {
          type: 'object',
          title: 'Station',
          allOf: [
            { $ref: '#/$defs/Base' },
            { type: 'object', properties: { position: { type: 'object', properties: { lat: { type: 'number' } } } } },
          ],
        },
      },
    }

    const imported = importFromJsonSchema(document)

    expect(imported.nodes.map(n => n.data.element.name)).toContain('StationPosition')
    expect(imported.edges.map(edge => edge.type).sort()).toEqual(['composition', 'inheritance'])
  })
})

describe('the Json attribute type', () => {
  const station = (): UMLDiagram =>
    diagram([
      {
        id: 'e1',
        name: 'Station',
        type: 'class',
        isRoot: true,
        attributes: [
          { id: 'a1', name: 'reference', type: 'String', isId: true, multiplicity: '1..1' },
          { id: 'a2', name: 'quality', type: 'Json', multiplicity: '0..1' },
          { id: 'a3', name: 'history', type: 'Json', multiplicity: '0..*' },
        ],
        operations: [],
      },
    ])

  it('is written as an object the structure does not describe', () => {
    const defs = exportToJsonSchema(station()).$defs as Record<string, JsonSchemaObject>
    const properties = defs.Station.properties as Record<string, JsonSchemaObject>

    expect(properties.quality).toEqual({ type: 'object' })
    expect(properties.history).toMatchObject({ type: 'array', items: { type: 'object' } })
  })

  it('reads an inline object back as Json, and survives the round trip', () => {
    const imported = importFromJsonSchema(exportToJsonSchema(station()))
    const element = imported.nodes[0].data.element
    const attributes = element.type === 'class' ? element.attributes : []

    expect(attributes.find(attribute => attribute.name === 'quality')?.type).toBe('Json')
    expectRoundTrip(station())
  })
})
