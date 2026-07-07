import { describe, expect, it } from 'vitest'

import type { SchemaTree } from '../_components/mapping-editor/_types'
import { field } from '../_components/mapping-editor/_types'
import {
  deriveStaMatchKeys,
  isReservedStaKeyName,
  isSafeStaKeyName,
  STA_ENTITIES,
  STA_FIXED_TARGET_PATHS,
} from './staTargetCatalog'

/**
 * Pins the STA catalog mirror against the adapter's `StaTargetCatalog` (config-adapter-nifi,
 * `FrostMappingCompilerTest` pins the same rules on the Java side). A path added or re-required on
 * one side only would let the editor accept a mapping the deploy then rejects — this test makes
 * that drift a visible failure instead of a silent divergence.
 */
describe('STA target catalog mirrors the adapter catalog', () => {
  it('pins the entities and their create sets', () => {
    expect(
      STA_ENTITIES.map(entity => ({
        key: entity.key,
        createPaths: [...entity.createPaths],
        optionalPaths: [...entity.optionalPaths],
      })),
    ).toEqual([
      { key: 'thing', createPaths: ['$.name', '$.description'], optionalPaths: [] },
      {
        key: 'location',
        createPaths: [
          '$.Locations[].name',
          '$.Locations[].description',
          '$.Locations[].encodingType',
          '$.Locations[].location',
        ],
        optionalPaths: [],
      },
      {
        key: 'datastream',
        createPaths: [
          '$.Datastreams[].name',
          '$.Datastreams[].description',
          '$.Datastreams[].observationType',
          '$.Datastreams[].unitOfMeasurement.name',
          '$.Datastreams[].unitOfMeasurement.symbol',
          '$.Datastreams[].unitOfMeasurement.definition',
          '$.Datastreams[].Sensor.name',
          '$.Datastreams[].Sensor.description',
          '$.Datastreams[].Sensor.encodingType',
          '$.Datastreams[].Sensor.metadata',
          '$.Datastreams[].ObservedProperty.name',
          '$.Datastreams[].ObservedProperty.definition',
          '$.Datastreams[].ObservedProperty.description',
        ],
        optionalPaths: [],
      },
      {
        key: 'observation',
        createPaths: ['$.Datastreams[].Observations[].result'],
        optionalPaths: ['$.Datastreams[].Observations[].phenomenonTime', '$.Datastreams[].Observations[].resultTime'],
      },
    ])
  })

  it('the fixed whitelist is exactly the union of the entities', () => {
    expect(STA_FIXED_TARGET_PATHS.size).toBe(
      STA_ENTITIES.flatMap(entity => [...entity.createPaths, ...entity.optionalPaths]).length,
    )
  })
})

describe('deriveStaMatchKeys', () => {
  const thingTree = (children: ReturnType<typeof field>[]): SchemaTree => ({
    name: 'SensorThingsDataModel',
    fields: [field('$', 'Thing', 'object', false, children)],
  })

  it('uses the {id}-marked scalar attributes of Thing and Datastream', () => {
    const tree = thingTree([
      { ...field('$.stationRef', 'stationRef', 'str', true), primaryKey: true },
      field('$.name', 'name', 'str', true),
      field('$.Datastreams', 'Datastreams', 'array', false, [
        { ...field('$.Datastreams[].dsRef', 'dsRef', 'str', true), primaryKey: true },
        field('$.Datastreams[].name', 'name', 'str', true),
      ]),
    ])

    expect(deriveStaMatchKeys(tree)).toEqual({
      thing: ['$.stationRef'],
      datastream: ['$.Datastreams[].dsRef'],
      isFallback: false,
    })
  })

  it('falls back to a declared reference attribute when no {id} is marked', () => {
    const tree = thingTree([
      field('$.reference', 'reference', 'str', true),
      field('$.Datastreams', 'Datastreams', 'array', false, [
        field('$.Datastreams[].reference', 'reference', 'str', true),
      ]),
    ])

    expect(deriveStaMatchKeys(tree)).toEqual({
      thing: ['$.reference'],
      datastream: ['$.Datastreams[].reference'],
      isFallback: true,
    })
  })

  it('yields empty keys when the structure declares neither marker nor reference', () => {
    const keys = deriveStaMatchKeys(thingTree([field('$.name', 'name', 'str', true)]))
    expect(keys.thing).toEqual([])
    expect(keys.datastream).toEqual([])
  })

  it('reports the fallback when only the datastream relies on it', () => {
    const tree = thingTree([
      { ...field('$.stationRef', 'stationRef', 'str', true), primaryKey: true },
      field('$.Datastreams', 'Datastreams', 'array', false, [
        field('$.Datastreams[].reference', 'reference', 'str', true),
      ]),
    ])
    expect(deriveStaMatchKeys(tree).isFallback).toBe(true)
  })

  it('reads document-root records (multi-root/legacy flat) directly', () => {
    const tree: SchemaTree = {
      name: 'Flat',
      fields: [{ ...field('$.reference', 'reference', 'str', true), primaryKey: true }],
    }
    expect(deriveStaMatchKeys(tree).thing).toEqual(['$.reference'])
    expect(deriveStaMatchKeys(tree).isFallback).toBe(false)
  })
})

describe('key-name mirrors of the engine rules', () => {
  it('whitelists identifiers and rejects everything else', () => {
    expect(isSafeStaKeyName('stationRef_1')).toBe(true)
    expect(isSafeStaKeyName('größe')).toBe(false)
    expect(isSafeStaKeyName('1st')).toBe(false)
    expect(isSafeStaKeyName("ref' or true")).toBe(false)
  })

  it('reserves the standard SensorThings field names per entity', () => {
    expect(isReservedStaKeyName('thing', 'name')).toBe(true)
    expect(isReservedStaKeyName('thing', 'observationType')).toBe(false)
    expect(isReservedStaKeyName('datastream', 'Sensor')).toBe(true)
    expect(isReservedStaKeyName('datastream', 'unitOfMeasurement')).toBe(true)
    expect(isReservedStaKeyName('datastream', 'stationRef')).toBe(false)
  })
})
