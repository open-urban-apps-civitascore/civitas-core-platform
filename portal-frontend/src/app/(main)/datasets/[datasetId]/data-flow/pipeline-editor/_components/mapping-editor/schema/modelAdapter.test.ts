import { describe, expect, it } from 'vitest'

import { requiredFieldPaths } from './fieldTree'
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
  it('rejects an empty model document', () => {
    expect(() => modelToSchemaTree({}, 'fallback')).toThrow(/no resolvable definition/)
  })

  it('returns an empty tree for an enumeration-only model', () => {
    expect(modelToSchemaTree({ title: 'Status', enum: ['ON', 'OFF'] }, 'fallback')).toEqual({
      name: 'Status',
      fields: [],
    })
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

  it('renders a multi-root document as one object node per root property, without a record node', () => {
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
    // The record is the document root itself — a `$` node would only repeat the tree name.
    expect(tree.fields.map(f => ({ name: f.name, path: f.path, type: f.type }))).toEqual([
      { name: 'building', path: '$.building', type: 'object' },
      { name: 'street', path: '$.street', type: 'object' },
    ])
    expect(tree.fields[0].children?.map(f => f.path)).toEqual(['$.building.floors'])
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

  it('marks the record node required only when a direct child is required', () => {
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

  it('does not force a whole-record mapping for a required leaf under an optional container', () => {
    // requiredFieldPaths recurses only into required containers; marking the record node required
    // for a leaf it cannot reach would degenerate to demanding the whole record.
    const tree = modelToSchemaTree(
      wrapperModel({
        Thing: { type: 'object', properties: { reading: { $ref: '#/$defs/Reading' } } },
        Reading: { type: 'object', properties: { value: { type: 'number' } }, required: ['value'] },
      }),
      'fallback',
    )
    expect(tree.fields[0].required).toBeUndefined()
    expect(requiredFieldPaths(tree)).toEqual([])
  })

  it('does not require multi-root trees: a record may populate only some roots', () => {
    const tree = modelToSchemaTree(
      {
        title: 'MultiRoot',
        type: 'object',
        properties: { building: { $ref: '#/$defs/Building' }, street: { $ref: '#/$defs/Street' } },
        $defs: {
          Building: { type: 'object', properties: { floors: { type: 'integer' } }, required: ['floors'] },
          Street: { type: 'object', properties: { name: { type: 'string' } } },
        },
      },
      'fallback',
    )
    expect(tree.fields.map(f => f.required ?? false)).toEqual([false, false])
    expect(requiredFieldPaths(tree)).toEqual([])
  })

  it('exposes a document root with a non-empty allOf as flat merged fields', () => {
    const tree = modelToSchemaTree(
      {
        title: 'Merged',
        type: 'object',
        allOf: [{ $ref: '#/$defs/Base' }],
        properties: { own: { type: 'string' } },
        $defs: { Base: { type: 'object', properties: { inherited: { type: 'string' } } } },
      },
      'fallback',
    )
    expect(tree.name).toBe('Merged')
    expect(tree.fields.map(f => f.path)).toEqual(['$.inherited', '$.own'])
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
    expect(tree.fields.map(f => f.path)).toEqual(['$.attribut'])
  })

  it('rejects a dangling allOf parent instead of silently truncating the tree', () => {
    expect(() =>
      modelToSchemaTree(
        wrapperModel({
          Thing: { allOf: [{ $ref: '#/$defs/Gone' }], type: 'object', properties: { a: { type: 'string' } } },
        }),
        'fallback',
      ),
    ).toThrow(/Gone/)
  })

  it('rejects a local property $ref that resolves to no definition', () => {
    expect(() =>
      modelToSchemaTree(
        wrapperModel({
          Thing: { type: 'object', properties: { part: { $ref: '#/$defs/Gone' } } },
        }),
        'fallback',
      ),
    ).toThrow(/Gone/)
  })

  it('renders non-local property $refs as opaque object leaves (the engine reads them as JSONB)', () => {
    const tree = modelToSchemaTree(
      wrapperModel({
        Thing: {
          type: 'object',
          properties: {
            relative: { $ref: 'common.json#/$defs/Base' },
            external: { $ref: 'https://example.org/other-schema.json' },
          },
        },
      }),
      'fallback',
    )
    expect(tree.fields[0].children).toEqual([
      { path: '$.relative', name: 'relative', type: 'object', portType: 'object' },
      { path: '$.external', name: 'external', type: 'object', portType: 'object' },
    ])
  })

  it('does not treat a root with an empty allOf list as a wrapper', () => {
    // Matches the backend: an allOf array on the root — even empty — disqualifies wrapper
    // unwrapping, so the record is the root itself and the single $ref property stays nested.
    const tree = modelToSchemaTree(
      {
        title: 'S',
        type: 'object',
        allOf: [],
        properties: { thing: { $ref: '#/$defs/Thing' } },
        $defs: { Thing: { type: 'object', properties: { a: { type: 'string' } } } },
      },
      'fallback',
    )
    expect(tree.fields.map(f => f.path)).toEqual(['$.thing'])
    expect(tree.fields[0].children?.map(f => f.path)).toEqual(['$.thing.a'])
  })

  it('terminates on mutually recursive $refs (A → B → A)', () => {
    const tree = modelToSchemaTree(
      wrapperModel(
        {
          A: { type: 'object', properties: { b: { $ref: '#/$defs/B' } } },
          B: { type: 'object', properties: { a: { $ref: '#/$defs/A' } } },
        },
        'A',
      ),
      'fallback',
    )
    const b = tree.fields[0].children?.[0]
    expect(b?.path).toBe('$.b')
    // The cycle back to A is truncated instead of recursing forever.
    expect(b?.children?.[0]).toMatchObject({ path: '$.b.a', type: 'object' })
    expect(b?.children?.[0].children).toBeUndefined()
  })

  describe('named-definition fallback (root without own properties)', () => {
    it('selects the single definition', () => {
      const tree = modelToSchemaTree(
        { title: 'X', type: 'object', $defs: { Only: { type: 'object', properties: { a: { type: 'string' } } } } },
        'fallback',
      )
      expect(tree.fields[0]).toMatchObject({ path: '$', name: 'Only' })
    })

    it('selects the title-matching definition among several', () => {
      const tree = modelToSchemaTree(
        {
          title: 'Second',
          type: 'object',
          $defs: {
            First: { type: 'object', properties: { a: { type: 'string' } } },
            Second: { type: 'object', properties: { b: { type: 'string' } } },
          },
        },
        'fallback',
      )
      expect(tree.fields[0]).toMatchObject({ path: '$', name: 'Second', type: 'object' })
      expect(tree.fields[0].children?.map(f => f.name)).toEqual(['b'])
    })

    it('selects the single properties-carrying definition, ignoring allOf-only ones', () => {
      const tree = modelToSchemaTree(
        {
          title: 'X',
          type: 'object',
          $defs: {
            Mixin: { allOf: [{ $ref: '#/$defs/Real' }] },
            Real: { type: 'object', properties: { a: { type: 'string' } } },
          },
        },
        'fallback',
      )
      expect(tree.fields[0]).toMatchObject({ path: '$', name: 'Real' })
    })

    it('rejects ambiguous definitions with no title match', () => {
      expect(() =>
        modelToSchemaTree(
          {
            title: 'X',
            type: 'object',
            $defs: {
              A: { type: 'object', properties: { a: { type: 'string' } } },
              B: { type: 'object', properties: { b: { type: 'string' } } },
            },
          },
          'fallback',
        ),
      ).toThrow(/none matches the title/)
    })
  })
})
