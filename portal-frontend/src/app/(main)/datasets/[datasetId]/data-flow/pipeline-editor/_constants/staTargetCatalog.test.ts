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
        optionalPaths: ['$.Locations[].properties'],
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
        optionalPaths: ['$.Datastreams[].Sensor.properties', '$.Datastreams[].ObservedProperty.properties'],
      },
      {
        key: 'observation',
        createPaths: ['$.Datastreams[].Observations[].result'],
        optionalPaths: [
          '$.Datastreams[].Observations[].phenomenonTime',
          '$.Datastreams[].Observations[].resultTime',
          '$.Datastreams[].Observations[].resultQuality',
          '$.Datastreams[].Observations[].validTime',
        ],
      },
      {
        key: 'featureOfInterest',
        createPaths: [
          '$.Datastreams[].Observations[].FeatureOfInterest.name',
          '$.Datastreams[].Observations[].FeatureOfInterest.description',
          '$.Datastreams[].Observations[].FeatureOfInterest.encodingType',
          '$.Datastreams[].Observations[].FeatureOfInterest.feature',
        ],
        optionalPaths: ['$.Datastreams[].Observations[].FeatureOfInterest.properties'],
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
  // The match key lives inside the entity's `properties` bag, mirrored here as a child object node.
  const thingProperties = (children: ReturnType<typeof field>[]) =>
    field('$.properties', 'properties', 'object', false, children)
  const datastreamProperties = (children: ReturnType<typeof field>[]) =>
    field('$.Datastreams[].properties', 'properties', 'object', false, children)

  const thingTree = (children: ReturnType<typeof field>[]): SchemaTree => ({
    name: 'SensorThingsDataModel',
    fields: [field('$', 'Thing', 'object', false, children)],
  })

  it('uses the {id}-marked scalar attributes inside the properties bag of Thing and Datastream', () => {
    const tree = thingTree([
      thingProperties([{ ...field('$.properties.stationRef', 'stationRef', 'str', true), primaryKey: true }]),
      field('$.name', 'name', 'str', true),
      field('$.Datastreams', 'Datastreams', 'array', false, [
        datastreamProperties([
          { ...field('$.Datastreams[].properties.dsRef', 'dsRef', 'str', true), primaryKey: true },
        ]),
        field('$.Datastreams[].name', 'name', 'str', true),
      ]),
    ])

    expect(deriveStaMatchKeys(tree)).toEqual({
      thing: ['$.properties.stationRef'],
      datastream: ['$.Datastreams[].properties.dsRef'],
      isFallback: false,
    })
  })

  it('falls back to a reference attribute inside the properties bag when no {id} is marked', () => {
    const tree = thingTree([
      thingProperties([field('$.properties.reference', 'reference', 'str', true)]),
      field('$.Datastreams', 'Datastreams', 'array', false, [
        datastreamProperties([field('$.Datastreams[].properties.reference', 'reference', 'str', true)]),
      ]),
    ])

    expect(deriveStaMatchKeys(tree)).toEqual({
      thing: ['$.properties.reference'],
      datastream: ['$.Datastreams[].properties.reference'],
      isFallback: true,
    })
  })

  it('yields empty keys when the properties bag declares neither marker nor reference', () => {
    const keys = deriveStaMatchKeys(thingTree([thingProperties([field('$.properties.note', 'note', 'str', false)])]))
    expect(keys.thing).toEqual([])
    expect(keys.datastream).toEqual([])
  })

  it('yields empty keys when the entity has no properties bag', () => {
    const keys = deriveStaMatchKeys(thingTree([field('$.name', 'name', 'str', true)]))
    expect(keys.thing).toEqual([])
    expect(keys.datastream).toEqual([])
  })

  it('reports the fallback when only the datastream relies on it', () => {
    const tree = thingTree([
      thingProperties([{ ...field('$.properties.stationRef', 'stationRef', 'str', true), primaryKey: true }]),
      field('$.Datastreams', 'Datastreams', 'array', false, [
        datastreamProperties([field('$.Datastreams[].properties.reference', 'reference', 'str', true)]),
      ]),
    ])
    expect(deriveStaMatchKeys(tree).isFallback).toBe(true)
  })

  it('reads document-root records (multi-root/legacy flat) directly', () => {
    const tree: SchemaTree = {
      name: 'Flat',
      fields: [thingProperties([{ ...field('$.properties.reference', 'reference', 'str', true), primaryKey: true }])],
    }
    expect(deriveStaMatchKeys(tree).thing).toEqual(['$.properties.reference'])
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

  it("reserves only 'properties', the bag the match key lives in", () => {
    expect(isReservedStaKeyName('properties')).toBe(true)
    // Standard SensorThings field names no longer collide — the key lives inside the bag.
    expect(isReservedStaKeyName('name')).toBe(false)
    expect(isReservedStaKeyName('Sensor')).toBe(false)
    expect(isReservedStaKeyName('reference')).toBe(false)
    expect(isReservedStaKeyName('stationRef')).toBe(false)
  })
})
