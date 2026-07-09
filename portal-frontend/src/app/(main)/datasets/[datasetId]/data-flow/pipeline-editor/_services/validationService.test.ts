import { describe, expect, it } from 'vitest'

import type { Pipeline, PipelineNode } from '../_types/pipeline'
import { createEmptyPipeline } from './pipelineService'
import { isValidNifiCron, validatePipeline } from './validationService'

interface TestNode {
  id: string
  type: string
  data: Record<string, unknown>
}

const pipelineWith = (nodes: TestNode[]): Pipeline => ({
  ...createEmptyPipeline('test'),
  nodes: nodes.map(node => ({ ...node, position: { x: 0, y: 0 } })) as unknown as PipelineNode[],
})

const REQUIRED_FIELDS_KEY = 'validation.messages.mappingRequiredFieldsMissing'
const MAPPING_NOT_SAVED_KEY = 'validation.messages.mappingNotSaved'

const mappingNode = (
  fields: Record<string, unknown>,
  targetRequiredFields?: string[],
  configured = true,
): TestNode => ({
  id: 'map-1',
  type: 'mapping',
  data: { label: 'Mapping', configured, mappingConfig: { fields, positions: {} }, targetRequiredFields },
})

describe('isValidNifiCron', () => {
  it('accepts a 6-field expression', () => {
    expect(isValidNifiCron('0 0 6 * * ?')).toBe(true)
  })

  it('rejects a 7-field expression with a year (NiFi 2.x dropped the Quartz year field)', () => {
    expect(isValidNifiCron('0 0 6 * * ? 2026')).toBe(false)
    expect(isValidNifiCron('0 0 6 * * ? *')).toBe(false)
  })

  it('rejects a 5-field expression', () => {
    expect(isValidNifiCron('0 0 * * *')).toBe(false)
  })

  it('accepts Spring day-of-week 0 (Sunday); rejects out-of-range 8', () => {
    expect(isValidNifiCron('0 0 6 * * 0')).toBe(true)
    expect(isValidNifiCron('0 0 6 * * 7')).toBe(true)
    expect(isValidNifiCron('0 0 6 * * 8')).toBe(false)
  })

  it('validates per-field ranges, not just the field count', () => {
    expect(isValidNifiCron('0 0 25 * * ?')).toBe(false) // hour out of range (>23)
    expect(isValidNifiCron('0 60 6 * * ?')).toBe(false) // minute out of range (>59)
    expect(isValidNifiCron('0 0 6 32 * ?')).toBe(false) // day-of-month out of range (>31)
    expect(isValidNifiCron('0 0 6 * 13 ?')).toBe(false) // month out of range (>12)
  })

  it('accepts ranges, steps, lists and named months/days', () => {
    expect(isValidNifiCron('0 0/15 9-17 ? * MON-FRI')).toBe(true)
    expect(isValidNifiCron('0 0 6 1,15 JAN,JUL ?')).toBe(true)
  })

  it('bounds range ends like the base values', () => {
    expect(isValidNifiCron('0 0 6-99 * * ?')).toBe(false) // hours range end >23
    expect(isValidNifiCron('0 0-75 6 * * ?')).toBe(false) // minutes range end >59
    expect(isValidNifiCron('0 0 6 1-40 * ?')).toBe(false) // day-of-month range end >31
    expect(isValidNifiCron('0 0 6 * * 1-9')).toBe(false) // day-of-week range end >7
  })

  it('bounds steps like the base values', () => {
    expect(isValidNifiCron('0 0/999 6 * * ?')).toBe(false) // minutes step >59
    expect(isValidNifiCron('0 0 */24 * * ?')).toBe(false) // hours step >23
    expect(isValidNifiCron('0 0/0 6 * * ?')).toBe(false) // zero step
  })

  it('rejects 0 for the 1-based day-of-month and month fields', () => {
    expect(isValidNifiCron('0 0 6 0 * ?')).toBe(false)
    expect(isValidNifiCron('0 0 6 * 0 ?')).toBe(false)
  })

  it('restricts W, L and #n to single values (the parser rejects them on ranges)', () => {
    expect(isValidNifiCron('0 0 6 15W * ?')).toBe(true)
    expect(isValidNifiCron('0 0 6 LW * ?')).toBe(true)
    expect(isValidNifiCron('0 0 6 ? * MON#3')).toBe(true)
    expect(isValidNifiCron('0 0 6 ? * FRIL')).toBe(true)
    expect(isValidNifiCron('0 0 6 5-10W * ?')).toBe(false)
    expect(isValidNifiCron('0 0 6 ? * MON-FRI#3')).toBe(false)
    expect(isValidNifiCron('0 0 6 ? * MON-FRIL')).toBe(false)
  })
})

describe('validateMappingCoversRequiredTargetFields', () => {
  it('flags a mapping that does not assign every required target field, naming the missing ones', () => {
    const result = validatePipeline(pipelineWith([mappingNode({ '$.name': 'x' }, ['$.name', '$.id'])]))
    const errors = result.errors.filter(error => error.messageKey === REQUIRED_FIELDS_KEY)
    expect(errors).toHaveLength(1)
    expect(errors[0].elementId).toBe('map-1')
    expect(errors[0].messageParams?.fields).toBe('$.id')
  })

  it('accepts a mapping that assigns all required target fields', () => {
    const result = validatePipeline(pipelineWith([mappingNode({ '$.name': 'x', '$.id': 'y' }, ['$.name', '$.id'])]))
    expect(result.errors.some(error => error.messageKey === REQUIRED_FIELDS_KEY)).toBe(false)
  })

  it('treats an empty or null mapping value as unmapped', () => {
    const result = validatePipeline(pipelineWith([mappingNode({ '$.name': '  ', '$.id': null }, ['$.name', '$.id'])]))
    const errors = result.errors.filter(error => error.messageKey === REQUIRED_FIELDS_KEY)
    expect(errors).toHaveLength(1)
    expect(errors[0].messageParams?.fields).toBe('$.name, $.id')
  })

  it('treats a required leaf as covered when an ancestor (object/array) path is mapped', () => {
    // mapping the whole $.location subtree covers its required leaves
    const result = validatePipeline(
      pipelineWith([mappingNode({ '$.location': '$.geo' }, ['$.location.lat', '$.location.lon'])]),
    )
    expect(result.errors.some(error => error.messageKey === REQUIRED_FIELDS_KEY)).toBe(false)
  })

  it('still flags a required leaf when neither it nor an ancestor is mapped', () => {
    const result = validatePipeline(
      pipelineWith([mappingNode({ '$.location': '$.geo' }, ['$.location.lat', '$.other'])]),
    )
    const errors = result.errors.filter(error => error.messageKey === REQUIRED_FIELDS_KEY)
    expect(errors).toHaveLength(1)
    expect(errors[0].messageParams?.fields).toBe('$.other')
  })

  it('does not require optional target fields (known-empty snapshot is valid)', () => {
    const result = validatePipeline(pipelineWith([mappingNode({}, [])]))
    expect(result.errors.some(error => error.messageKey === REQUIRED_FIELDS_KEY)).toBe(false)
    expect(result.errors.some(error => error.messageKey === MAPPING_NOT_SAVED_KEY)).toBe(false)
  })

  it('errors on a configured node without a saved-mapping snapshot (never saved / invalidated)', () => {
    const result = validatePipeline(pipelineWith([mappingNode({}, undefined)]))
    const errors = result.errors.filter(error => error.messageKey === MAPPING_NOT_SAVED_KEY)
    expect(errors).toHaveLength(1)
    expect(errors[0].elementId).toBe('map-1')
  })

  it('does not report a not-yet-configured node (validateNodeConfiguration owns that)', () => {
    const result = validatePipeline(pipelineWith([mappingNode({}, undefined, false)]))
    expect(result.errors.some(error => error.messageKey === MAPPING_NOT_SAVED_KEY)).toBe(false)
  })
})

// Skipped while validateFrostMappingCoversStaGroups is commented out of VALIDATION_RULES.
describe.skip('validateFrostMappingCoversStaGroups', () => {
  const frostSink: TestNode = { id: 'frost-1', type: 'frost', data: { label: 'FROST', configured: true } }

  const MATCH_KEYS = {
    thing: ['$.properties.reference'],
    datastream: ['$.Datastreams[].properties.reference'],
    isFallback: false,
    thingBag: ['$.properties.reference'],
    datastreamBag: ['$.Datastreams[].properties.reference'],
  }

  // 'omit' drops the snapshot entirely — an explicit undefined would just re-trigger the default.
  const staMapping = (fields: Record<string, unknown>, staMatchKeys: unknown = MATCH_KEYS): TestNode => ({
    id: 'map-1',
    type: 'mapping',
    data: {
      label: 'Mapping',
      configured: true,
      mappingConfig: { fields, positions: {} },
      targetRequiredFields: [],
      ...(staMatchKeys === 'omit' ? {} : { staMatchKeys }),
    },
  })

  const creatableThingFields = {
    '$.properties.reference': '$.ref',
    '$.name': '$.station',
    '$.description': '$.desc',
  }

  const has = (pipeline: Pipeline, key: string): boolean =>
    validatePipeline(pipeline).errors.some(error => error.messageKey === key)

  const MATCH_KEYS_KEY = 'validation.messages.frostMappingMissingMatchKeys'
  const NO_KEY_IN_STRUCTURE_KEY = 'validation.messages.frostMappingNoMatchKeyInStructure'
  const CREATE_SET_KEY = 'validation.messages.frostMappingCreateSetIncomplete'
  const UNKNOWN_KEY = 'validation.messages.frostMappingUnknownStaTarget'
  const LOCATION_KEY = 'validation.messages.frostMappingLocationNeedsCreatableThing'
  const RESULT_KEY = 'validation.messages.frostMappingObservationNeedsResult'
  const FALLBACK_KEY = 'validation.messages.frostMappingFallbackMatchKey'

  /** The mapping node wired into the FROST sink — the condition under which the rule applies. */
  const wiredToFrost = (mapping: TestNode): Pipeline => ({
    ...pipelineWith([frostSink, mapping]),
    edges: [{ id: 'e1', source: mapping.id, target: frostSink.id }] as Pipeline['edges'],
  })

  it('rejects a FROST mapping that misses the Thing match key, naming it', () => {
    const result = validatePipeline(wiredToFrost(staMapping({ '$.name': '$.station', '$.description': '$.d' })))
    const errors = result.errors.filter(e => e.messageKey === MATCH_KEYS_KEY)
    expect(errors).toHaveLength(1)
    expect(errors[0].elementId).toBe('map-1')
    expect(errors[0].messageParams?.entity).toBe('thing')
    expect(errors[0].messageParams?.fields).toBe('$.properties.reference')
  })

  it('accepts a lookup-only Thing mapping (key only, no create fields)', () => {
    const result = validatePipeline(wiredToFrost(staMapping({ '$.properties.reference': '$.ref' })))
    expect(result.errors.filter(e => e.messageKey.startsWith('validation.messages.frostMapping'))).toEqual([])
  })

  it('rejects a partially mapped create set, naming the missing fields', () => {
    const result = validatePipeline(
      wiredToFrost(staMapping({ '$.properties.reference': '$.ref', '$.name': '$.station' })),
    )
    const errors = result.errors.filter(e => e.messageKey === CREATE_SET_KEY)
    expect(errors).toHaveLength(1)
    expect(errors[0].messageParams?.entity).toBe('thing')
    expect(errors[0].messageParams?.fields).toBe('$.description')
  })

  it('requires the datastream match key once any Datastreams path is touched', () => {
    const result = validatePipeline(
      wiredToFrost(
        staMapping({
          '$.properties.reference': '$.ref',
          '$.Datastreams[].Observations[].result': { op: 'toFloat', input: '$.temp' },
        }),
      ),
    )
    const errors = result.errors.filter(e => e.messageKey === MATCH_KEYS_KEY)
    expect(errors).toHaveLength(1)
    expect(errors[0].messageParams?.entity).toBe('datastream')
    expect(errors[0].messageParams?.fields).toBe('$.Datastreams[].properties.reference')
  })

  it('reports a structure without a datastream match key only when the entity is touched', () => {
    const noDsKey = { ...MATCH_KEYS, datastream: [], datastreamBag: [] }
    expect(has(wiredToFrost(staMapping({ '$.properties.reference': '$.ref' }, noDsKey)), NO_KEY_IN_STRUCTURE_KEY)).toBe(
      false,
    )
    expect(
      has(
        wiredToFrost(
          staMapping({ '$.properties.reference': '$.ref', '$.Datastreams[].Observations[].result': '$.v' }, noDsKey),
        ),
        NO_KEY_IN_STRUCTURE_KEY,
      ),
    ).toBe(true)
  })

  it('rejects a Location without a creatable Thing (deep insert only)', () => {
    const result = validatePipeline(
      wiredToFrost(
        staMapping({
          '$.properties.reference': '$.ref',
          '$.Locations[].name': '$.loc',
          '$.Locations[].description': '$.d',
          '$.Locations[].encodingType': '$.e',
          '$.Locations[].location': { op: 'geoPoint', lon: '$.lon', lat: '$.lat' },
        }),
      ),
    )
    expect(result.errors.some(e => e.messageKey === LOCATION_KEY)).toBe(true)
  })

  it('rejects an Observation without result', () => {
    expect(
      has(
        wiredToFrost(
          staMapping({
            '$.properties.reference': '$.ref',
            '$.Datastreams[].properties.reference': '$.ref',
            '$.Datastreams[].Observations[].phenomenonTime': '$.ts',
          }),
        ),
        RESULT_KEY,
      ),
    ).toBe(true)
  })

  it('rejects a target path outside the catalog and match keys, naming it', () => {
    const result = validatePipeline(wiredToFrost(staMapping({ ...creatableThingFields, '$.serialNumber': '$.sn' })))
    const errors = result.errors.filter(e => e.messageKey === UNKNOWN_KEY)
    expect(errors).toHaveLength(1)
    expect(errors[0].messageParams?.path).toBe('$.serialNumber')
  })

  it('accepts a full creatable chain including optional observation paths', () => {
    const result = validatePipeline(
      wiredToFrost(
        staMapping({
          ...creatableThingFields,
          '$.Datastreams[].properties.reference': '$.ref',
          '$.Datastreams[].name': '$.dsName',
          '$.Datastreams[].description': '$.d',
          '$.Datastreams[].observationType': '$.t',
          '$.Datastreams[].unitOfMeasurement.name': '$.u',
          '$.Datastreams[].unitOfMeasurement.symbol': '$.u',
          '$.Datastreams[].unitOfMeasurement.definition': '$.u',
          '$.Datastreams[].Sensor.name': '$.s',
          '$.Datastreams[].Sensor.description': '$.s',
          '$.Datastreams[].Sensor.encodingType': '$.s',
          '$.Datastreams[].Sensor.metadata': '$.s',
          '$.Datastreams[].ObservedProperty.name': '$.o',
          '$.Datastreams[].ObservedProperty.definition': '$.o',
          '$.Datastreams[].ObservedProperty.description': '$.o',
          '$.Datastreams[].Observations[].result': { op: 'toFloat', input: '$.temp' },
          '$.Datastreams[].Observations[].phenomenonTime': '$.ts',
          '$.Datastreams[].Observations[].resultTime': '$.ts',
        }),
      ),
    )
    expect(result.errors.filter(e => e.messageKey.startsWith('validation.messages.frostMapping'))).toEqual([])
  })

  it('accepts the optional properties bags and extra observation fields', () => {
    const result = validatePipeline(
      wiredToFrost(
        staMapping({
          ...creatableThingFields,
          '$.Locations[].name': '$.loc',
          '$.Locations[].description': '$.d',
          '$.Locations[].encodingType': '$.e',
          '$.Locations[].location': { op: 'geoPoint', lon: '$.lon', lat: '$.lat' },
          '$.Locations[].properties': '$.meta',
          '$.Datastreams[].properties.reference': '$.ref',
          '$.Datastreams[].Observations[].result': { op: 'toFloat', input: '$.temp' },
          '$.Datastreams[].Observations[].resultQuality': '$.q',
          '$.Datastreams[].Observations[].validTime': '$.valid',
        }),
      ),
    )
    expect(result.errors.filter(e => e.messageKey.startsWith('validation.messages.frostMapping'))).toEqual([])
  })

  const FEATURE_OF_INTEREST_KEY = 'validation.messages.frostMappingFeatureOfInterestNeedsObservation'

  it('rejects a FeatureOfInterest without a mapped Observation', () => {
    expect(
      has(
        wiredToFrost(
          staMapping({
            '$.properties.reference': '$.ref',
            '$.Datastreams[].properties.reference': '$.ref',
            '$.Datastreams[].Observations[].FeatureOfInterest.name': '$.n',
            '$.Datastreams[].Observations[].FeatureOfInterest.description': '$.d',
            '$.Datastreams[].Observations[].FeatureOfInterest.encodingType': '$.e',
            '$.Datastreams[].Observations[].FeatureOfInterest.feature': { op: 'geoPoint', lon: '$.lon', lat: '$.lat' },
          }),
        ),
        FEATURE_OF_INTEREST_KEY,
      ),
    ).toBe(true)
  })

  it('rejects a partially mapped FeatureOfInterest create set', () => {
    expect(
      has(
        wiredToFrost(
          staMapping({
            '$.properties.reference': '$.ref',
            '$.Datastreams[].properties.reference': '$.ref',
            '$.Datastreams[].Observations[].result': { op: 'toFloat', input: '$.temp' },
            '$.Datastreams[].Observations[].FeatureOfInterest.name': '$.n',
          }),
        ),
        CREATE_SET_KEY,
      ),
    ).toBe(true)
  })

  it('accepts an Observation with a fully mapped FeatureOfInterest', () => {
    const result = validatePipeline(
      wiredToFrost(
        staMapping({
          '$.properties.reference': '$.ref',
          '$.Datastreams[].properties.reference': '$.ref',
          '$.Datastreams[].Observations[].result': { op: 'toFloat', input: '$.temp' },
          '$.Datastreams[].Observations[].FeatureOfInterest.name': '$.n',
          '$.Datastreams[].Observations[].FeatureOfInterest.description': '$.d',
          '$.Datastreams[].Observations[].FeatureOfInterest.encodingType': '$.e',
          '$.Datastreams[].Observations[].FeatureOfInterest.feature': { op: 'geoPoint', lon: '$.lon', lat: '$.lat' },
        }),
      ),
    )
    expect(result.errors.filter(e => e.messageKey.startsWith('validation.messages.frostMapping'))).toEqual([])
  })

  it('exempts the FROST-final mapping from the unconditional required-fields rule', () => {
    // A lookup-only mapping deliberately leaves required create fields (e.g. $.name) unmapped —
    // the catalog's conditional requiredness owns this node, not the generic snapshot rule.
    const REQUIRED_KEY = 'validation.messages.mappingRequiredFieldsMissing'
    const node: TestNode = {
      id: 'map-1',
      type: 'mapping',
      data: {
        label: 'Mapping',
        configured: true,
        mappingConfig: { fields: { '$.properties.reference': '$.ref' }, positions: {} },
        targetRequiredFields: ['$.name'],
        staMatchKeys: MATCH_KEYS,
      },
    }
    expect(has(wiredToFrost(node), REQUIRED_KEY)).toBe(false)
    // the same node NOT feeding a FROST sink stays subject to the rule
    expect(has(pipelineWith([node]), REQUIRED_KEY)).toBe(true)
  })

  it('rejects an unsafe match-key name at edit time (mirrors the deploy whitelist)', () => {
    const UNSAFE_KEY = 'validation.messages.frostMappingUnsafeMatchKeyName'
    const umlaut = { ...MATCH_KEYS, thing: ['$.properties.größe'], thingBag: ['$.properties.größe'] }
    const result = validatePipeline(wiredToFrost(staMapping({ '$.properties.größe': '$.ref' }, umlaut)))
    const errors = result.errors.filter(e => e.messageKey === UNSAFE_KEY)
    expect(errors).toHaveLength(1)
    expect(errors[0].messageParams?.keyName).toBe('größe')
  })

  it("rejects a match key named 'properties' (it shadows the bag it lives in)", () => {
    const RESERVED_KEY = 'validation.messages.frostMappingReservedMatchKeyName'
    const reserved = { ...MATCH_KEYS, thing: ['$.properties.properties'], thingBag: ['$.properties.properties'] }
    const result = validatePipeline(wiredToFrost(staMapping({ '$.properties.properties': '$.n' }, reserved)))
    expect(result.errors.some(e => e.messageKey === RESERVED_KEY)).toBe(true)
  })

  it('warns when the match key came from the reference fallback', () => {
    const fallback = { ...MATCH_KEYS, isFallback: true }
    const result = validatePipeline(wiredToFrost(staMapping({ '$.properties.reference': '$.ref' }, fallback)))
    expect(result.warnings.some(w => w.messageKey === FALLBACK_KEY)).toBe(true)
  })

  it('treats a configured node without a match-key snapshot as never saved', () => {
    const result = validatePipeline(wiredToFrost(staMapping({ '$.properties.reference': '$.ref' }, 'omit')))
    expect(result.errors.some(e => e.messageKey === 'validation.messages.mappingNotSaved')).toBe(true)
  })

  it('finds the FROST sink transitively downstream, not only via a direct edge', () => {
    const mapping = staMapping({ '$.name': '$.station', '$.description': '$.d' })
    const between: TestNode = { id: 'geo-1', type: 'geoPersistence', data: { label: 'Geo', configured: true } }
    const pipeline: Pipeline = {
      ...pipelineWith([frostSink, mapping, between]),
      edges: [
        { id: 'e1', source: mapping.id, target: between.id },
        { id: 'e2', source: between.id, target: frostSink.id },
      ] as Pipeline['edges'],
    }
    expect(has(pipeline, MATCH_KEYS_KEY)).toBe(true)
  })

  it('ignores a mapping that does not feed the FROST sink (unconnected FROST node on the canvas)', () => {
    expect(
      has(pipelineWith([frostSink, staMapping({ '$.name': '$.x', '$.description': '$.d' })]), MATCH_KEYS_KEY),
    ).toBe(false)
  })

  it('binds only to the last mapping of a chain before the FROST sink', () => {
    const first = staMapping({ '$.name': '$.x', '$.description': '$.d' })
    const last: TestNode = { ...staMapping({ '$.name': '$.x', '$.description': '$.d' }), id: 'map-2' }
    const pipeline: Pipeline = {
      ...pipelineWith([frostSink, first, last]),
      edges: [
        { id: 'e1', source: first.id, target: last.id },
        { id: 'e2', source: last.id, target: frostSink.id },
      ] as Pipeline['edges'],
    }
    const errors = validatePipeline(pipeline).errors.filter(error => error.messageKey === MATCH_KEYS_KEY)
    expect(errors).toHaveLength(1)
    expect(errors[0].elementId).toBe('map-2')
  })
})

// ============================================================================
// Graph-driven flow rules (mirror of the adapter's FlowPath derivation)
// ============================================================================

const graph = (nodes: TestNode[], edges: { source: string; target: string }[]): Pipeline => ({
  ...pipelineWith(nodes),
  edges: edges.map((edge, index) => ({ ...edge, id: `edge-${index}` })) as Pipeline['edges'],
})

const startNode: TestNode = { id: 'start-1', type: 'start', data: { label: 'Start', configured: true } }
const endNode: TestNode = { id: 'end-1', type: 'end', data: { label: 'End', configured: true } }

const sourceAt = (id: string, connector?: string): TestNode => ({
  id,
  type: 'dataSource',
  data: {
    label: id,
    configured: true,
    entityType: 'datasource',
    entityMetadata: connector === undefined ? {} : { connector },
  },
})

const frostAt = (id: string): TestNode => ({
  id,
  type: 'frost',
  data: { label: id, configured: true, entityType: 'frost' },
})

const geoAt = (id: string): TestNode => ({
  id,
  type: 'geoPersistence',
  data: { label: id, configured: true, entityType: 'persistence', tableName: id },
})

const mappingAt = (id: string, schema?: Record<string, unknown>): TestNode => ({
  id,
  type: 'mapping',
  data: {
    label: id,
    configured: true,
    mappingConfig: { fields: {}, positions: {} },
    targetRequiredFields: [],
    ...schema,
  },
})

const cronAt = (id: string): TestNode => ({
  id,
  type: 'cron',
  data: { label: id, configured: true, cronExpression: '0 0 6 * * ?' },
})

const errorsFor = (pipeline: Pipeline, key: string) =>
  validatePipeline(pipeline).errors.filter(error => error.messageKey === `validation.messages.${key}`)

describe('validateFlowShape', () => {
  it('accepts a linear source → sink flow', () => {
    const pipeline = graph(
      [startNode, endNode, sourceAt('src-1', 'MQTT'), frostAt('frost-1')],
      [
        { source: 'start-1', target: 'src-1' },
        { source: 'src-1', target: 'frost-1' },
        { source: 'frost-1', target: 'end-1' },
      ],
    )
    expect(validatePipeline(pipeline).errors).toEqual([])
  })

  it('requires a data source node (structure error)', () => {
    const pipeline = graph([startNode, endNode, frostAt('frost-1')], [{ source: 'start-1', target: 'frost-1' }])
    const errors = errorsFor(pipeline, 'sourceNodeRequired')
    expect(errors).toHaveLength(1)
    expect(errors[0].type).toBe('structure')
  })

  it('requires a data sink node (structure error) — there is no implicit FROST default', () => {
    const pipeline = graph(
      [startNode, endNode, sourceAt('src-1', 'MQTT')],
      [
        { source: 'start-1', target: 'src-1' },
        { source: 'src-1', target: 'end-1' },
      ],
    )
    const errors = errorsFor(pipeline, 'sinkNodeRequired')
    expect(errors).toHaveLength(1)
    expect(errors[0].type).toBe('structure')
  })

  it('anchors the error for a second data source at the additional node', () => {
    const pipeline = graph(
      [sourceAt('src-1', 'SQL'), sourceAt('src-2', 'SQL'), geoAt('geo-1')],
      [{ source: 'src-1', target: 'geo-1' }],
    )
    const errors = errorsFor(pipeline, 'multipleSourceNodes')
    expect(errors).toHaveLength(1)
    expect(errors[0].elementId).toBe('src-2')
  })

  it('anchors the error for a second data sink at the additional node (frost + geoPersistence count together)', () => {
    const pipeline = graph(
      [sourceAt('src-1', 'MQTT'), frostAt('frost-1'), geoAt('geo-1')],
      [{ source: 'src-1', target: 'frost-1' }],
    )
    const errors = errorsFor(pipeline, 'multipleSinkNodes')
    expect(errors).toHaveLength(1)
    expect(errors[0].elementId).toBe('geo-1')
  })

  it('rejects a source with an incoming data edge (unsupported position)', () => {
    const pipeline = graph(
      [sourceAt('src-1', 'SQL'), mappingAt('map-1'), geoAt('geo-1')],
      [
        { source: 'map-1', target: 'src-1' },
        { source: 'src-1', target: 'geo-1' },
      ],
    )
    const errors = errorsFor(pipeline, 'nodeUnsupportedPosition')
    expect(errors).toHaveLength(1)
    expect(errors[0].elementId).toBe('src-1')
  })

  it('rejects a sink with an outgoing data edge (unsupported position)', () => {
    const pipeline = graph(
      [sourceAt('src-1', 'SQL'), mappingAt('map-1'), geoAt('geo-1')],
      [
        { source: 'src-1', target: 'geo-1' },
        { source: 'geo-1', target: 'map-1' },
      ],
    )
    const errors = errorsFor(pipeline, 'nodeUnsupportedPosition')
    expect(errors).toHaveLength(1)
    expect(errors[0].elementId).toBe('geo-1')
  })

  it('rejects a branching data flow at the branching node', () => {
    const pipeline = graph(
      [sourceAt('src-1', 'SQL'), mappingAt('map-1'), geoAt('geo-1')],
      [
        { source: 'src-1', target: 'map-1' },
        { source: 'src-1', target: 'geo-1' },
        { source: 'map-1', target: 'geo-1' },
      ],
    )
    const errors = errorsFor(pipeline, 'flowBranches')
    expect(errors).toHaveLength(1)
    expect(errors[0].elementId).toBe('src-1')
  })

  it('rejects a cycle at the revisited node', () => {
    const pipeline = graph(
      [sourceAt('src-1', 'SQL'), mappingAt('map-1'), mappingAt('map-2'), geoAt('geo-1')],
      [
        { source: 'src-1', target: 'map-1' },
        { source: 'map-1', target: 'map-2' },
        { source: 'map-2', target: 'map-1' },
      ],
    )
    const errors = errorsFor(pipeline, 'flowCycle')
    expect(errors).toHaveLength(1)
    expect(errors[0].elementId).toBe('map-1')
  })

  it('reports a dead end before the sink as a missing data path (structure error)', () => {
    const pipeline = graph(
      [sourceAt('src-1', 'SQL'), mappingAt('map-1'), geoAt('geo-1')],
      [{ source: 'src-1', target: 'map-1' }],
    )
    const errors = errorsFor(pipeline, 'noDataPath')
    expect(errors).toHaveLength(1)
    expect(errors[0].type).toBe('structure')
  })

  it('rejects an unknown node kind wired into the flow, but tolerates a loose one', () => {
    const unknown: TestNode = { id: 'x-1', type: 'weird', data: { label: 'Weird', configured: true } }
    const wired = graph(
      [sourceAt('src-1', 'MQTT'), frostAt('frost-1'), unknown],
      [
        { source: 'src-1', target: 'frost-1' },
        { source: 'src-1', target: 'x-1' },
      ],
    )
    const errors = errorsFor(wired, 'unknownNodeInFlow')
    expect(errors).toHaveLength(1)
    expect(errors[0].elementId).toBe('x-1')

    const loose = graph(
      [sourceAt('src-1', 'MQTT'), frostAt('frost-1'), unknown],
      [{ source: 'src-1', target: 'frost-1' }],
    )
    expect(errorsFor(loose, 'unknownNodeInFlow')).toEqual([])
  })

  it('rejects a fully wired mapping that is not on the source-to-sink path', () => {
    const pipeline = graph(
      [startNode, endNode, sourceAt('src-1', 'MQTT'), frostAt('frost-1'), mappingAt('map-1')],
      [
        { source: 'start-1', target: 'src-1' },
        { source: 'src-1', target: 'frost-1' },
        { source: 'frost-1', target: 'end-1' },
        { source: 'start-1', target: 'map-1' },
        { source: 'map-1', target: 'end-1' },
      ],
    )
    const errors = errorsFor(pipeline, 'mappingNotOnPath')
    expect(errors).toHaveLength(1)
    expect(errors[0].elementId).toBe('map-1')
  })

  it('suppresses mapping-off-path noise while the walk is aborted (the branch is the finding)', () => {
    // map-1 is fully wired but the walk aborts at the branch; piling "not on path" onto it would
    // blame a node the user cannot fix before resolving the branch
    const pipeline = graph(
      [sourceAt('src-1', 'SQL'), mappingAt('map-1'), geoAt('geo-1')],
      [
        { source: 'src-1', target: 'map-1' },
        { source: 'src-1', target: 'geo-1' },
        { source: 'map-1', target: 'geo-1' },
      ],
    )
    expect(errorsFor(pipeline, 'flowBranches')).toHaveLength(1)
    expect(errorsFor(pipeline, 'mappingNotOnPath')).toEqual([])
  })

  it('accepts one schedule trigger on a pull source and anchors a second one at the additional cron node', () => {
    const single = graph(
      [startNode, sourceAt('src-1', 'SQL'), geoAt('geo-1'), cronAt('cron-1')],
      [
        { source: 'start-1', target: 'cron-1' },
        { source: 'cron-1', target: 'src-1' },
        { source: 'src-1', target: 'geo-1' },
      ],
    )
    expect(errorsFor(single, 'cronTriggerCapacity')).toEqual([])

    const double = graph(
      [startNode, sourceAt('src-1', 'SQL'), geoAt('geo-1'), cronAt('cron-1'), cronAt('cron-2')],
      [
        { source: 'start-1', target: 'cron-1' },
        { source: 'start-1', target: 'cron-2' },
        { source: 'cron-1', target: 'src-1' },
        { source: 'cron-2', target: 'src-1' },
        { source: 'src-1', target: 'geo-1' },
      ],
    )
    const errors = errorsFor(double, 'cronTriggerCapacity')
    expect(errors).toHaveLength(1)
    expect(errors[0].elementId).toBe('cron-2')
  })

  it('rejects a cron feeding anything but the source (unsupported position)', () => {
    const pipeline = graph(
      [startNode, sourceAt('src-1', 'SQL'), mappingAt('map-1'), geoAt('geo-1'), cronAt('cron-1')],
      [
        { source: 'start-1', target: 'cron-1' },
        { source: 'cron-1', target: 'map-1' },
        { source: 'src-1', target: 'map-1' },
        { source: 'map-1', target: 'geo-1' },
      ],
    )
    const errors = errorsFor(pipeline, 'nodeUnsupportedPosition')
    expect(errors).toHaveLength(1)
    expect(errors[0].elementId).toBe('cron-1')
  })

  it('anchors the cron-on-push-source error at the cron node', () => {
    const pipeline = graph(
      [startNode, sourceAt('src-1', 'MQTT'), frostAt('frost-1'), cronAt('cron-1')],
      [
        { source: 'start-1', target: 'cron-1' },
        { source: 'cron-1', target: 'src-1' },
        { source: 'src-1', target: 'frost-1' },
      ],
    )
    const errors = errorsFor(pipeline, 'cronMqttIncompatible').filter(error => error.elementId === 'cron-1')
    expect(errors).toHaveLength(1)
  })

  it('warns at the cron node when the wired source connector cannot be verified', () => {
    const pipeline = graph(
      [startNode, sourceAt('src-1'), geoAt('geo-1'), cronAt('cron-1')],
      [
        { source: 'start-1', target: 'cron-1' },
        { source: 'cron-1', target: 'src-1' },
        { source: 'src-1', target: 'geo-1' },
      ],
    )
    const result = validatePipeline(pipeline)
    expect(errorsFor(pipeline, 'cronMqttIncompatible')).toEqual([])
    expect(
      result.warnings.some(
        warning =>
          warning.messageKey === 'validation.messages.cronSourceConnectorUnknown' && warning.elementId === 'cron-1',
      ),
    ).toBe(true)
  })
})

describe('validateEdgeEndpoints', () => {
  it('errors on an edge whose endpoint resolves to no node (dangling after a corrupt round-trip)', () => {
    const pipeline = graph(
      [sourceAt('src-1', 'MQTT'), frostAt('frost-1')],
      [
        { source: 'src-1', target: 'frost-1' },
        { source: 'src-1', target: 'ghost' },
      ],
    )
    const errors = errorsFor(pipeline, 'edgeEndpointMissing')
    expect(errors).toHaveLength(1)
    expect(errors[0].elementId).toBe('edge-1')
    expect(errors[0].type).toBe('edge')
  })

  it('accepts edges whose endpoints both resolve', () => {
    const pipeline = graph([sourceAt('src-1', 'MQTT'), frostAt('frost-1')], [{ source: 'src-1', target: 'frost-1' }])
    expect(errorsFor(pipeline, 'edgeEndpointMissing')).toEqual([])
  })
})

describe('validateMappingDataShape', () => {
  const CORRUPT_KEY = 'validation.messages.mappingDataCorrupt'

  it('errors on a mapping-typed node whose data lost the mapping shape (corrupt round-trip)', () => {
    // every mapping rule skips such a node, so without this rule it would validate clean and fail
    // only at deploy
    const corrupt: TestNode = { id: 'map-x', type: 'mapping', data: { label: 'Broken', configured: true } }
    const errors = validatePipeline(pipelineWith([corrupt])).errors.filter(e => e.messageKey === CORRUPT_KEY)
    expect(errors).toHaveLength(1)
    expect(errors[0].elementId).toBe('map-x')
  })

  it('accepts a mapping node carrying the mapping shape', () => {
    const errors = validatePipeline(pipelineWith([mappingAt('map-1')])).errors.filter(e => e.messageKey === CORRUPT_KEY)
    expect(errors).toEqual([])
  })
})

describe('validateEdgeCompatibility', () => {
  it('rejects a records source feeding an unmapped FROST sink, anchored at the sink', () => {
    const pipeline = graph([sourceAt('src-1', 'SQL'), frostAt('frost-1')], [{ source: 'src-1', target: 'frost-1' }])
    const errors = errorsFor(pipeline, 'sqlSourceToFrost').filter(error => error.elementId === 'frost-1')
    expect(errors).toHaveLength(1)
  })

  it('accepts a records source feeding a FROST sink through a wired mapping', () => {
    const pipeline = graph(
      [sourceAt('src-1', 'SQL'), mappingAt('map-1'), frostAt('frost-1')],
      [
        { source: 'src-1', target: 'map-1' },
        { source: 'map-1', target: 'frost-1' },
      ],
    )
    expect(errorsFor(pipeline, 'sqlSourceToFrost')).toEqual([])
    expect(errorsFor(pipeline, 'edgeFormIncompatible')).toEqual([])
  })

  it('judges mapped-upstream by wiring, not by a mapping existing elsewhere on the canvas', () => {
    const pipeline = graph(
      [sourceAt('src-1', 'SQL'), mappingAt('map-1'), frostAt('frost-1')],
      [{ source: 'src-1', target: 'frost-1' }],
    )
    const errors = errorsFor(pipeline, 'sqlSourceToFrost').filter(error => error.elementId === 'frost-1')
    expect(errors).toHaveLength(1)
  })

  it('accepts an envelope source feeding an unmapped FROST sink (passthrough)', () => {
    const pipeline = graph([sourceAt('src-1', 'MQTT'), frostAt('frost-1')], [{ source: 'src-1', target: 'frost-1' }])
    expect(errorsFor(pipeline, 'sqlSourceToFrost')).toEqual([])
    expect(errorsFor(pipeline, 'edgeFormIncompatible')).toEqual([])
  })

  it('coerces an envelope source into a records sink (the engine inserts the convert)', () => {
    const pipeline = graph([sourceAt('src-1', 'MQTT'), geoAt('geo-1')], [{ source: 'src-1', target: 'geo-1' }])
    expect(errorsFor(pipeline, 'edgeFormIncompatible')).toEqual([])
  })

  it('skips the check when the source form cannot be determined (unknown connector)', () => {
    const pipeline = graph([sourceAt('src-1'), frostAt('frost-1')], [{ source: 'src-1', target: 'frost-1' }])
    expect(errorsFor(pipeline, 'sqlSourceToFrost')).toEqual([])
    expect(errorsFor(pipeline, 'edgeFormIncompatible')).toEqual([])
  })
})

describe('validateMappingChainStructure', () => {
  const CHAIN_KEY = 'mappingChainStructureMismatch'

  const writes = (targetId: string, targetVersion: string, targetName?: string) => ({
    targetDatastructureId: targetId,
    targetVersionId: targetVersion,
    targetName,
  })
  const reads = (sourceId: string, sourceVersion: string, sourceName?: string) => ({
    sourceDatastructureId: sourceId,
    sourceVersionId: sourceVersion,
    sourceName,
  })

  const chain = (first: TestNode, second: TestNode): Pipeline =>
    graph(
      [sourceAt('src-1', 'SQL'), first, second, geoAt('geo-1')],
      [
        { source: 'src-1', target: first.id },
        { source: first.id, target: second.id },
        { source: second.id, target: 'geo-1' },
      ],
    )

  it('accepts a chain where the downstream mapping reads what the upstream one writes', () => {
    const pipeline = chain(mappingAt('map-1', writes('ds-b', 'v1')), mappingAt('map-2', reads('ds-b', 'v1')))
    expect(errorsFor(pipeline, CHAIN_KEY)).toEqual([])
  })

  it('rejects a broken chain at the downstream mapping, naming both datastructures', () => {
    const pipeline = chain(
      mappingAt('map-1', writes('ds-b', 'v1', 'Structure B')),
      mappingAt('map-2', reads('ds-c', 'v1', 'Structure C')),
    )
    const errors = errorsFor(pipeline, CHAIN_KEY)
    expect(errors).toHaveLength(1)
    expect(errors[0].elementId).toBe('map-2')
    expect(errors[0].messageParams?.expected).toBe('Structure B')
    expect(errors[0].messageParams?.actual).toBe('Structure C')
  })

  it('rejects a version mismatch on the same datastructure', () => {
    const pipeline = chain(mappingAt('map-1', writes('ds-b', 'v1')), mappingAt('map-2', reads('ds-b', 'v2')))
    expect(errorsFor(pipeline, CHAIN_KEY)).toHaveLength(1)
  })

  it('skips mappings whose schema selection is incomplete (nodeConfigured owns those)', () => {
    const pipeline = chain(mappingAt('map-1'), mappingAt('map-2', reads('ds-c', 'v1')))
    expect(errorsFor(pipeline, CHAIN_KEY)).toEqual([])
  })
})

describe('validateCronAndMappingWired', () => {
  const NOT_WIRED_KEY = 'validation.messages.nodeNotWired'

  const wire = (nodes: TestNode[], edges: { id: string; source: string; target: string }[]): Pipeline => ({
    ...pipelineWith(nodes),
    edges,
  })

  it('flags a cron node with only an incoming edge', () => {
    const pipeline = wire(
      [
        { id: 's', type: 'start', data: {} },
        { id: 'c', type: 'cron', data: { label: 'CRON' } },
      ],
      [{ id: 'e1', source: 's', target: 'c' }],
    )
    const errors = validatePipeline(pipeline).errors.filter(error => error.messageKey === NOT_WIRED_KEY)
    expect(errors).toHaveLength(1)
    expect(errors[0].elementId).toBe('c')
  })

  it('accepts a cron node wired both ways', () => {
    const pipeline = wire(
      [
        { id: 's', type: 'start', data: {} },
        { id: 'c', type: 'cron', data: { label: 'CRON' } },
        { id: 'e', type: 'end', data: {} },
      ],
      [
        { id: 'e1', source: 's', target: 'c' },
        { id: 'e2', source: 'c', target: 'e' },
      ],
    )
    expect(validatePipeline(pipeline).errors.some(error => error.messageKey === NOT_WIRED_KEY)).toBe(false)
  })
})
