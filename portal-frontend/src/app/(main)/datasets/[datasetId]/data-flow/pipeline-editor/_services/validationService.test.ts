import { describe, expect, it } from 'vitest'

import type { Pipeline, PipelineNode } from '../_types/pipeline'
import { createEmptyPipeline } from './pipelineService'
import { isValidNifiCron, validatePipeline } from './validationService'

const CRON_MQTT_KEY = 'validation.messages.cronMqttIncompatible'

interface TestNode {
  id: string
  type: string
  data: Record<string, unknown>
}

const pipelineWith = (nodes: TestNode[]): Pipeline => ({
  ...createEmptyPipeline('test'),
  nodes: nodes.map(node => ({ ...node, position: { x: 0, y: 0 } })) as unknown as PipelineNode[],
})

const cronNode: TestNode = {
  id: 'cron-1',
  type: 'cron',
  data: { label: 'CRON', configured: true, cronExpression: '0 0 6 * * ?' },
}

const source = (connector: string): TestNode => ({
  id: 'src-1',
  type: 'dataSource',
  data: { label: 'Source', configured: true, entityType: 'datasource', entityMetadata: { connector } },
})

const hasCronMqttError = (pipeline: Pipeline): boolean =>
  validatePipeline(pipeline).errors.some(error => error.messageKey === CRON_MQTT_KEY)

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

describe('validateCronRequiresNonMqttSource', () => {
  it('rejects a CRON trigger combined with an MQTT source', () => {
    expect(hasCronMqttError(pipelineWith([cronNode, source('MQTT')]))).toBe(true)
  })

  it('allows a CRON trigger with a SQL source', () => {
    expect(hasCronMqttError(pipelineWith([cronNode, source('SQL')]))).toBe(false)
  })

  it('does not flag a pipeline without a CRON node', () => {
    expect(hasCronMqttError(pipelineWith([source('MQTT')]))).toBe(false)
  })

  it('warns (does not error) when a CRON source connector is unknown', () => {
    const unknownSource: TestNode = {
      id: 'src-1',
      type: 'dataSource',
      data: { label: 'Source', configured: true, entityType: 'datasource', entityMetadata: {} },
    }
    const result = validatePipeline(pipelineWith([cronNode, unknownSource]))
    expect(result.errors.some(error => error.messageKey === CRON_MQTT_KEY)).toBe(false)
    expect(
      result.warnings.some(warning => warning.messageKey === 'validation.messages.cronSourceConnectorUnknown'),
    ).toBe(true)
  })
})

describe('sink/source combination rules mirror the deploy engine', () => {
  const frostSink: TestNode = { id: 'frost-1', type: 'frost', data: { label: 'FROST', configured: true } }

  const staMapping = (fields: Record<string, unknown>): TestNode => ({
    id: 'map-1',
    type: 'mapping',
    data: {
      label: 'Mapping',
      configured: true,
      mappingConfig: { fields, positions: {} },
      targetRequiredFields: [],
    },
  })

  const fullThingsFields = {
    '$.things[].name': '$.station',
    '$.things[].description': '$.desc',
    '$.things[].properties.reference': '$.ref',
  }

  const has = (pipeline: Pipeline, key: string): boolean =>
    validatePipeline(pipeline).errors.some(error => error.messageKey === key)

  it('rejects a SQL source writing to a FROST sink without a mapping', () => {
    expect(has(pipelineWith([source('SQL'), frostSink]), 'validation.messages.sqlSourceToFrost')).toBe(true)
  })

  it('allows a SQL source writing to a FROST sink through a mapping (the engine rebuilds the envelope)', () => {
    expect(
      has(
        pipelineWith([source('SQL'), frostSink, staMapping(fullThingsFields)]),
        'validation.messages.sqlSourceToFrost',
      ),
    ).toBe(false)
  })

  it('allows an MQTT source writing to a FROST sink', () => {
    expect(has(pipelineWith([source('MQTT'), frostSink]), 'validation.messages.sqlSourceToFrost')).toBe(false)
  })

  describe('validateFrostMappingCoversStaGroups', () => {
    const NO_ELEMENT_KEY = 'validation.messages.frostMappingNoStaElement'
    const GROUP_KEY = 'validation.messages.frostMappingGroupIncomplete'

    /** The mapping node wired into the FROST sink — the condition under which the rule applies. */
    const wiredToFrost = (mapping: TestNode): Pipeline => ({
      ...pipelineWith([frostSink, mapping]),
      edges: [{ id: 'e1', source: mapping.id, target: frostSink.id }] as Pipeline['edges'],
    })

    it('rejects a FROST mapping that maps no STA element at all', () => {
      expect(has(wiredToFrost(staMapping({})), NO_ELEMENT_KEY)).toBe(true)
    })

    it('accepts a mapping fully covering the touched group and leaving the other unmapped', () => {
      const result = validatePipeline(wiredToFrost(staMapping(fullThingsFields)))
      expect(result.errors.some(e => e.messageKey === NO_ELEMENT_KEY || e.messageKey === GROUP_KEY)).toBe(false)
    })

    it('rejects a touched group missing its required lookup keys, naming them', () => {
      const result = validatePipeline(wiredToFrost(staMapping({ '$.things[].name': '$.station' })))
      const errors = result.errors.filter(e => e.messageKey === GROUP_KEY)
      expect(errors).toHaveLength(1)
      expect(errors[0].elementId).toBe('map-1')
      expect(errors[0].messageParams?.group).toBe('$.things')
      expect(errors[0].messageParams?.fields).toBe('$.things[].description, $.things[].properties.reference')
    })

    it('checks every touched group independently', () => {
      const result = validatePipeline(
        wiredToFrost(
          staMapping({ ...fullThingsFields, '$.observations[].result': { op: 'toFloat', input: '$.temp' } }),
        ),
      )
      const errors = result.errors.filter(e => e.messageKey === GROUP_KEY)
      expect(errors).toHaveLength(1)
      expect(errors[0].messageParams?.group).toBe('$.observations')
      expect(errors[0].messageParams?.fields).toBe(
        '$.observations[].parameters.reference, $.observations[].parameters.name',
      )
    })

    it('finds the FROST sink transitively downstream, not only via a direct edge', () => {
      const mapping = staMapping({})
      const between: TestNode = { id: 'geo-1', type: 'geoPersistence', data: { label: 'Geo', configured: true } }
      const pipeline: Pipeline = {
        ...pipelineWith([frostSink, mapping, between]),
        edges: [
          { id: 'e1', source: mapping.id, target: between.id },
          { id: 'e2', source: between.id, target: frostSink.id },
        ] as Pipeline['edges'],
      }
      expect(has(pipeline, NO_ELEMENT_KEY)).toBe(true)
    })

    it('ignores a mapping that does not feed the FROST sink (unconnected FROST node on the canvas)', () => {
      expect(has(pipelineWith([frostSink, staMapping({})]), NO_ELEMENT_KEY)).toBe(false)
    })

    it('does not run without a FROST sink (regular datastructure targets have their own rule)', () => {
      expect(has(pipelineWith([staMapping({})]), NO_ELEMENT_KEY)).toBe(false)
    })

    it('leaves unsaved/unconfigured mapping nodes to the other rules', () => {
      const unsaved: TestNode = {
        id: 'map-1',
        type: 'mapping',
        data: { label: 'Mapping', configured: true, mappingConfig: { fields: {}, positions: {} } },
      }
      expect(has(wiredToFrost(unsaved), NO_ELEMENT_KEY)).toBe(false)
    })
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
