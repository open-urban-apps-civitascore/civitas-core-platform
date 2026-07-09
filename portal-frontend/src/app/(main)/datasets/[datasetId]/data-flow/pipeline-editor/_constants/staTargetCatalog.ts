/**
 * The record-anchored paths a mapping may target when it feeds a FROST sink — a mirror of the
 * deploy engine's `StaTargetCatalog` plus its match-key derivation. The target datastructure is a
 * normal, user-modelled Thing-shaped structure (one record = one Thing with its Locations,
 * Datastreams and their Observations); the engine accepts exactly these paths and rejects
 * everything else at deploy, so validation surfaces the same constraints at edit time.
 *
 * Each entity follows two engine rules mirrored here: its match-key paths (the `{id}`-marked
 * attributes of the entity class, fallback: a `reference` attribute) must be mapped once the
 * entity is touched, and its fixed create set is all-or-nothing — all mapped makes the entity
 * creatable (a FROST miss creates it), none leaves it lookup-only (a miss is a data error).
 */

import type { FieldNode, SchemaTree } from '../_components/mapping-editor/_types'

export interface StaEntity {
  /** Stable key, also used in validation message params. */
  readonly key: 'thing' | 'location' | 'datastream' | 'observation' | 'featureOfInterest'
  /** The element prefix every field path of the entity starts with (`$.` for the Thing itself). */
  readonly pathPrefix: string
  /** Paths that must ALL be assigned once ANY of them is (the entity's create set). */
  readonly createPaths: readonly string[]
  /** Paths the engine accepts but never demands. */
  readonly optionalPaths: readonly string[]
}

export const STA_ENTITIES: readonly StaEntity[] = [
  {
    key: 'thing',
    pathPrefix: '$.',
    createPaths: ['$.name', '$.description'],
    optionalPaths: [],
  },
  {
    key: 'location',
    pathPrefix: '$.Locations[',
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
    pathPrefix: '$.Datastreams[',
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
    pathPrefix: '$.Datastreams[].Observations[',
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
    pathPrefix: '$.Datastreams[].Observations[].FeatureOfInterest',
    createPaths: [
      '$.Datastreams[].Observations[].FeatureOfInterest.name',
      '$.Datastreams[].Observations[].FeatureOfInterest.description',
      '$.Datastreams[].Observations[].FeatureOfInterest.encodingType',
      '$.Datastreams[].Observations[].FeatureOfInterest.feature',
    ],
    optionalPaths: ['$.Datastreams[].Observations[].FeatureOfInterest.properties'],
  },
]

/**
 * Mirror of the engine's identifier whitelist for schema-derived key names — they end up in
 * OData $filter expressions and generated template keys, so anything else fails the deploy.
 */
export const isSafeStaKeyName = (keyName: string): boolean => /^[A-Za-z_][A-Za-z0-9_]*$/.test(keyName)

/**
 * Mirror of the engine's reserved-name rule: a match key named like a standard SensorThings
 * field of its entity ({@link STA_ENTITIES} first path segment) would make one mapping path mean
 * two things, so the deploy rejects it.
 */
export const isReservedStaKeyName = (entityKey: 'thing' | 'datastream', keyName: string): boolean => {
  const entity = STA_ENTITIES.find(candidate => candidate.key === entityKey) as StaEntity
  return entity.createPaths.some(path => firstSegment(path, entity.pathPrefix) === keyName)
}

const firstSegment = (path: string, pathPrefix: string): string => {
  const prefix = pathPrefix === '$.' ? '$.' : `${pathPrefix}].`
  return path.slice(prefix.length).split(/[.[]/)[0]
}

/** All fixed catalog paths (the schema-derived match-key paths come on top per structure). */
export const STA_FIXED_TARGET_PATHS: ReadonlySet<string> = new Set(
  STA_ENTITIES.flatMap(entity => [...entity.createPaths, ...entity.optionalPaths]),
)

/**
 * The effective match keys of a Thing-shaped target structure, snapshotted onto the mapping node
 * at save time (the validation rules cannot re-derive them — they never see the schema tree).
 */
export interface StaMatchKeys {
  /** Record paths of the Thing's match-key attributes (e.g. `["$.reference"]`). */
  readonly thing: readonly string[]
  /** Record paths of the Datastream's match-key attributes; empty without a Datastreams class. */
  readonly datastream: readonly string[]
  /** Whether any entity's keys came from the `reference` fallback instead of an `{id}` marker. */
  readonly isFallback: boolean
}

/**
 * Derives the match keys the engine will use: the entity class's `{id}`-marked scalar attributes,
 * else a declared `reference` attribute, else none (the validation reports the gap only when the
 * entity is actually mapped). Mirrors the engine's `FrostSinkStage.resolveStaKeys`.
 */
export const deriveStaMatchKeys = (targetTree: SchemaTree): StaMatchKeys => {
  const record = recordFields(targetTree)
  const datastreams = record.find(child => child.name === 'Datastreams')?.children ?? []

  const thingMarked = keyPaths(record)
  const datastreamMarked = keyPaths(datastreams)
  const thing = thingMarked.length > 0 ? thingMarked : fallbackReference(record)
  const datastream = datastreamMarked.length > 0 ? datastreamMarked : fallbackReference(datastreams)

  return {
    thing,
    datastream,
    // Fallback on EITHER entity — the warning must fire whenever any find-or-create key is not
    // an explicit {id} marker, not only the Thing's.
    isFallback:
      (thingMarked.length === 0 && thing.length > 0) || (datastreamMarked.length === 0 && datastream.length > 0),
  }
}

/**
 * The record's direct fields: the tree either roots a single resolved class node (`$`) or exposes
 * the document-root properties directly (multi-root/legacy flat) — same duality as the tree
 * builder.
 */
const recordFields = (tree: SchemaTree): FieldNode[] => {
  if (tree.fields.length === 1 && tree.fields[0].path === '$') {
    return tree.fields[0].children ?? []
  }
  return tree.fields
}

const isScalarLeaf = (node: FieldNode): boolean => !node.children && node.portType !== 'array'

const keyPaths = (fields: FieldNode[]): string[] =>
  fields.filter(node => node.primaryKey === true && isScalarLeaf(node)).map(node => node.path)

const fallbackReference = (fields: FieldNode[]): string[] =>
  fields.filter(node => node.name === 'reference' && isScalarLeaf(node)).map(node => node.path)
