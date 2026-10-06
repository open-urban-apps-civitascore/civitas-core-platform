import { describe, expect, it } from 'vitest'

import type { SchemaTree } from '../_components/mapping-editor/_types'
import { field } from '../_components/mapping-editor/_types'
import {
  deriveStaMatchKeys,
  isReservedStaKeyName,
  isSafeStaKeyName,
  STA_ENTITIES,
  STA_FIXED_TARGET_PATHS,
  STA_OBSERVATION_PORT_ENTITIES,
  STA_OBSERVATION_REQUIRED_PATHS,
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
        optionalPaths: [
          '$.Datastreams[].Observations[].phenomenonTime',
          '$.Datastreams[].Observations[].resultTime',
          '$.Datastreams[].Observations[].validTime',
        ],
      },
      {
        key: 'featureOfInterest',
        createPaths: [
          '$.Datastreams[].Observations[].FeatureOfInterest.name',
          '$.Datastreams[].Observations[].FeatureOfInterest.description',
          '$.Datastreams[].Observations[].FeatureOfInterest.encodingType',
        ],
        optionalPaths: [],
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

  it('pins the vocabulary of the Observations port at the measurement root', () => {
    // The port publishes the measurement itself as its structure, so the same fields sit one root
    // higher than in the Thing-shaped catalog. The engine rebases them before it validates.
    expect(
      STA_OBSERVATION_PORT_ENTITIES.map(entity => ({
        key: entity.key,
        createPaths: [...entity.createPaths],
        optionalPaths: [...entity.optionalPaths],
      })),
    ).toEqual([
      {
        key: 'observation',
        createPaths: ['$.result'],
        optionalPaths: ['$.phenomenonTime', '$.resultTime', '$.validTime'],
      },
      {
        key: 'featureOfInterest',
        createPaths: [
          '$.FeatureOfInterest.name',
          '$.FeatureOfInterest.description',
          '$.FeatureOfInterest.encodingType',
        ],
        optionalPaths: [],
      },
    ])
    expect([...STA_OBSERVATION_REQUIRED_PATHS]).toEqual([
      '$.result',
      '$.parameters.thingReference',
      '$.parameters.datastreamReference',
    ])
  })

  it('reads the measurement bag from parameters, the one entity without a properties bag', () => {
    const tree: SchemaTree = {
      name: 'Observations',
      fields: [
        field('$', 'Observation', 'object', false, [
          field('$.result', 'result', 'str', true),
          field('$.parameters', 'parameters', 'object', false, [
            { ...field('$.parameters.reference', 'reference', 'str', true), primaryKey: true },
            field('$.parameters.thingReference', 'thingReference', 'str', true),
            field('$.parameters.datastreamReference', 'datastreamReference', 'str', true),
          ]),
        ]),
      ],
    }

    const keys = deriveStaMatchKeys(tree)

    expect(keys.observation).toEqual(['$.parameters.reference'])
    expect(keys.observationBag).toEqual([
      '$.parameters.reference',
      '$.parameters.thingReference',
      '$.parameters.datastreamReference',
    ])
    // Nothing of the Thing shape is there — and the measurement's key must not leak into it.
    expect(keys.thing).toEqual([])
    expect(keys.thingBag).toEqual([])
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
      thingBag: ['$.properties.stationRef'],
      datastreamBag: ['$.Datastreams[].properties.dsRef'],
      locationBag: [],
      observation: [],
      observationBag: [],
      modelledPaths: [],
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
      thingBag: ['$.properties.reference'],
      datastreamBag: ['$.Datastreams[].properties.reference'],
      locationBag: [],
      observation: [],
      observationBag: [],
      modelledPaths: [],
    })
  })

  it('collects the Location bag as mappable paths without making any of them a required key', () => {
    // The shape of the published ThingTree structure. The port gives a Location without a reference
    // of its own the Thing's, so the marker on the Location reference does not make it required.
    const tree = thingTree([
      thingProperties([{ ...field('$.properties.reference', 'reference', 'str', true), primaryKey: true }]),
      field('$.Locations', 'Locations', 'array', false, [
        field('$.Locations[].name', 'name', 'str', true),
        field('$.Locations[].properties', 'properties', 'object', false, [
          { ...field('$.Locations[].properties.reference', 'reference', 'str', false), primaryKey: true },
          field('$.Locations[].properties.floor', 'floor', 'int', false),
        ]),
      ]),
    ])

    const keys = deriveStaMatchKeys(tree)

    expect(keys.locationBag).toEqual(['$.Locations[].properties.reference', '$.Locations[].properties.floor'])
    expect(keys.thing).toEqual(['$.properties.reference'])
    expect(keys.thingBag).toEqual(['$.properties.reference'])
  })

  it('collects the values of the classes modelled under the free fields', () => {
    const tree = thingTree([
      thingProperties([
        field('$.properties.reference', 'reference', 'str', true),
        field('$.properties.site', 'site', 'object', false, [field('$.properties.site.floor', 'floor', 'int', false)]),
      ]),
      field('$.Datastreams', 'Datastreams', 'array', false, [
        field('$.Datastreams[].Sensor', 'Sensor', 'object', false, [
          field('$.Datastreams[].Sensor.properties', 'properties', 'object', false, [
            field('$.Datastreams[].Sensor.properties.vendor', 'vendor', 'str', false),
          ]),
        ]),
        // A free object shown as text is no target: it has no attribute to write.
        field('$.Datastreams[].ObservedProperty', 'ObservedProperty', 'object', false, [
          field('$.Datastreams[].ObservedProperty.properties', 'properties', 'str', false),
        ]),
        field('$.Datastreams[].Observations', 'Observations', 'array', false, [
          field('$.Datastreams[].Observations[].resultQuality', 'resultQuality', 'object', false, [
            field('$.Datastreams[].Observations[].resultQuality.accuracy', 'accuracy', 'number', false),
          ]),
          field('$.Datastreams[].Observations[].FeatureOfInterest', 'FeatureOfInterest', 'object', false, [
            field('$.Datastreams[].Observations[].FeatureOfInterest.feature', 'feature', 'Point', true),
          ]),
        ]),
      ]),
    ])

    const keys = deriveStaMatchKeys(tree)

    expect(keys.thingBag).toEqual(['$.properties.reference'])
    expect(keys.modelledPaths).toEqual([
      '$.properties.site.floor',
      '$.Datastreams[].Sensor.properties.vendor',
      '$.Datastreams[].Observations[].resultQuality.accuracy',
      '$.Datastreams[].Observations[].FeatureOfInterest.feature',
    ])
  })

  it('reads the free fields of a measurement at the record root', () => {
    const tree: SchemaTree = {
      name: 'Observations',
      fields: [
        field('$', 'Observation', 'object', false, [
          field('$.result', 'result', 'str', true),
          field('$.FeatureOfInterest', 'FeatureOfInterest', 'object', false, [
            field('$.FeatureOfInterest.feature', 'feature', 'object', false, [
              field('$.FeatureOfInterest.feature.station', 'station', 'str', false),
            ]),
          ]),
        ]),
      ],
    }

    expect(deriveStaMatchKeys(tree).modelledPaths).toEqual(['$.FeatureOfInterest.feature.station'])
  })

  it('yields an empty Location bag when the Location has no properties class', () => {
    const tree = thingTree([
      thingProperties([field('$.properties.reference', 'reference', 'str', true)]),
      field('$.Locations', 'Locations', 'array', false, [field('$.Locations[].name', 'name', 'str', true)]),
    ])

    expect(deriveStaMatchKeys(tree).locationBag).toEqual([])
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
