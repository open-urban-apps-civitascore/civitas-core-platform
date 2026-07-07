import { describe, expect, it } from 'vitest'

import { modelToSchemaTree } from './modelAdapter'

const wrapperModel = (defs: Record<string, unknown>, rootRef = 'Thing', title = 'MyStructure') => ({
  $id: 'urn:example',
  $schema: 'https://json-schema.org/draft/2020-12/schema',
  title,
  type: 'object',
  properties: { [rootRef.toLowerCase()]: { $ref: `#/$defs/${rootRef}` } },
  $defs: defs,
})

describe('modelToSchemaTree', () => {
  it('returns an empty tree for a missing model', () => {
    expect(modelToSchemaTree(null, 'fallback')).toEqual({ name: 'fallback', fields: [] })
    expect(modelToSchemaTree(undefined, 'fallback')).toEqual({ name: 'fallback', fields: [] })
  })

  it('resolves a wrapper root: tree named after the structure, class as the mappable $ node', () => {
    const tree = modelToSchemaTree(
      wrapperModel({
        Thing: {
          type: 'object',
          properties: { name: { type: 'string' }, count: { type: 'integer' } },
          required: ['name'],
        },
      }),
      'fallback',
    )

    expect(tree.name).toBe('MyStructure')
    expect(tree.fields).toHaveLength(1)
    const root = tree.fields[0]
    expect(root).toMatchObject({ path: '$', name: 'Thing', type: 'object', portType: 'object', required: true })
    expect(root.children).toEqual([
      { path: '$.name', name: 'name', type: 'str', portType: 'scalar', required: true },
      { path: '$.count', name: 'count', type: 'int', portType: 'scalar' },
    ])
  })

  it('maps scalar types and formats like the diagram adapter (date-time, uuid, number, boolean)', () => {
    const tree = modelToSchemaTree(
      wrapperModel({
        Thing: {
          type: 'object',
          properties: {
            ts: { type: 'string', format: 'date-time' },
            id: { type: 'string', format: 'uuid' },
            value: { type: 'number' },
            active: { type: 'boolean' },
          },
        },
      }),
      'fallback',
    )
    expect(tree.fields[0].children?.map(f => f.type)).toEqual(['date', 'str', 'float', 'bool'])
  })

  it('maps geojson $refs to concrete geometry types', () => {
    const tree = modelToSchemaTree(
      wrapperModel({
        Thing: {
          type: 'object',
          properties: {
            location: { $ref: 'https://geojson.org/schema/Point.json', crs: 'EPSG:4326' },
            area: { $ref: 'https://geojson.org/schema/Polygon.json' },
          },
        },
      }),
      'fallback',
    )
    expect(tree.fields[0].children).toEqual([
      { path: '$.location', name: 'location', type: 'Point', portType: 'geometry' },
      { path: '$.area', name: 'area', type: 'Polygon', portType: 'geometry' },
    ])
  })

  it('nests local $ref properties and arrays of $refs with [] path segments', () => {
    const tree = modelToSchemaTree(
      wrapperModel({
        Thing: {
          type: 'object',
          properties: {
            unit: { $ref: '#/$defs/Unit' },
            readings: { type: 'array', items: { $ref: '#/$defs/Reading' } },
          },
        },
        Unit: { type: 'object', properties: { symbol: { type: 'string' } } },
        Reading: { type: 'object', properties: { value: { type: 'number' } } },
      }),
      'fallback',
    )
    const [unit, readings] = tree.fields[0].children ?? []
    expect(unit).toMatchObject({ path: '$.unit', type: 'object', portType: 'object' })
    expect(unit.children).toEqual([{ path: '$.unit.symbol', name: 'symbol', type: 'str', portType: 'scalar' }])
    expect(readings).toMatchObject({ path: '$.readings', type: 'array', portType: 'array' })
    expect(readings.children).toEqual([
      { path: '$.readings[].value', name: 'value', type: 'float', portType: 'scalar' },
    ])
  })

  it('inlines allOf inheritance parents-first with own properties overriding in place', () => {
    const tree = modelToSchemaTree(
      wrapperModel(
        {
          Animal: { type: 'object', properties: { id: { type: 'string' }, name: { type: 'string' } } },
          Dog: {
            allOf: [{ $ref: '#/$defs/Animal' }],
            type: 'object',
            properties: { name: { type: 'integer' }, breed: { type: 'string' } },
          },
        },
        'Dog',
      ),
      'fallback',
    )
    expect(tree.fields[0].children?.map(f => ({ name: f.name, type: f.type }))).toEqual([
      { name: 'id', type: 'str' },
      { name: 'name', type: 'int' },
      { name: 'breed', type: 'str' },
    ])
  })

  it('lets $defs shadow legacy definitions on key collisions', () => {
    const tree = modelToSchemaTree(
      {
        title: 'S',
        type: 'object',
        properties: { thing: { $ref: '#/$defs/Thing' } },
        definitions: { Thing: { type: 'object', properties: { old: { type: 'string' } } } },
        $defs: { Thing: { type: 'object', properties: { current: { type: 'string' } } } },
      },
      'fallback',
    )
    expect(tree.fields[0].children?.map(f => f.name)).toEqual(['current'])
  })

  it('renders a multi-root document as one object node per root property', () => {
    const tree = modelToSchemaTree(
      {
        title: 'TrafficSensor',
        type: 'object',
        properties: { building: { $ref: '#/$defs/Building' }, street: { $ref: '#/$defs/Street' } },
        $defs: {
          Building: { type: 'object', properties: { floors: { type: 'integer' } } },
          Street: { type: 'object', properties: { name: { type: 'string' } } },
        },
      },
      'fallback',
    )
    expect(tree.name).toBe('TrafficSensor')
    const root = tree.fields[0]
    expect(root).toMatchObject({ path: '$', name: 'TrafficSensor', type: 'object' })
    expect(root.children?.map(f => ({ name: f.name, path: f.path, type: f.type }))).toEqual([
      { name: 'building', path: '$.building', type: 'object' },
      { name: 'street', path: '$.street', type: 'object' },
    ])
    expect(root.children?.[0].children?.map(f => f.path)).toEqual(['$.building.floors'])
  })

  it('treats an enum $ref property as a scalar leaf', () => {
    const tree = modelToSchemaTree(
      wrapperModel({
        Thing: { type: 'object', properties: { status: { $ref: '#/$defs/Status' } } },
        Status: { title: 'Status', enum: ['ON', 'OFF'] },
      }),
      'fallback',
    )
    expect(tree.fields[0].children?.[0]).toMatchObject({ path: '$.status', type: 'str', portType: 'scalar' })
  })

  it('guards against recursive $refs instead of recursing forever', () => {
    const tree = modelToSchemaTree(
      wrapperModel({
        Thing: { type: 'object', properties: { child: { $ref: '#/$defs/Thing' } } },
      }),
      'fallback',
    )
    const child = tree.fields[0].children?.[0]
    expect(child?.path).toBe('$.child')
    expect(child?.children).toBeUndefined()
  })

  it('marks the record node required only when it has required descendants', () => {
    const allOptional = modelToSchemaTree(
      wrapperModel({ Thing: { type: 'object', properties: { a: { type: 'string' } } } }),
      'fallback',
    )
    expect(allOptional.fields[0].required).toBeUndefined()

    const withRequired = modelToSchemaTree(
      wrapperModel({ Thing: { type: 'object', properties: { a: { type: 'string' } }, required: ['a'] } }),
      'fallback',
    )
    expect(withRequired.fields[0].required).toBe(true)
  })

  it('rejects a wrapper whose $ref target is missing', () => {
    expect(() =>
      modelToSchemaTree(
        { title: 'S', type: 'object', properties: { thing: { $ref: '#/$defs/Missing' } }, $defs: {} },
        'fallback',
      ),
    ).toThrow(/Missing/)
  })

  it('rejects a relative (non-local, non-http) $ref', () => {
    expect(() =>
      modelToSchemaTree(
        wrapperModel({
          Thing: {
            allOf: [{ $ref: 'common.json#/$defs/Base' }],
            type: 'object',
            properties: { a: { type: 'string' } },
          },
        }),
        'fallback',
      ),
    ).toThrow(/non-local/)
  })

  it('skips external http(s) allOf parents like the backend does', () => {
    const tree = modelToSchemaTree(
      wrapperModel({
        Thing: {
          allOf: [{ $ref: 'https://example.org/external.json' }],
          type: 'object',
          properties: { a: { type: 'string' } },
        },
      }),
      'fallback',
    )
    expect(tree.fields[0].children?.map(f => f.name)).toEqual(['a'])
  })

  it('resolves a legacy flat root (own properties, no wrapper) at the document root', () => {
    const tree = modelToSchemaTree(
      {
        $id: 'http://civitas.org/model/Legacy/1.0.0',
        title: 'Legacy',
        type: 'object',
        properties: { attribut: { type: 'string' } },
      },
      'fallback',
    )
    expect(tree.name).toBe('Legacy')
    expect(tree.fields[0]).toMatchObject({ path: '$', name: 'Legacy' })
    expect(tree.fields[0].children?.map(f => f.path)).toEqual(['$.attribut'])
  })
})
