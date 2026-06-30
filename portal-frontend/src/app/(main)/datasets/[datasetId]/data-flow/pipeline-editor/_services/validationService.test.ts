import { describe, expect, it } from 'vitest'

import type { Pipeline, PipelineNode } from '../_types/pipeline'
import { createEmptyPipeline } from './pipelineService'
import { isValidQuartzCron, validatePipeline } from './validationService'

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

describe('isValidQuartzCron', () => {
  it('accepts a 6-field expression', () => {
    expect(isValidQuartzCron('0 0 6 * * ?')).toBe(true)
  })

  it('accepts a 7-field expression with an optional year (matches the backend)', () => {
    expect(isValidQuartzCron('0 0 6 * * ? 2026')).toBe(true)
    expect(isValidQuartzCron('0 0 6 * * ? *')).toBe(true)
  })

  it('rejects a 5-field (non-Quartz) expression', () => {
    expect(isValidQuartzCron('0 0 * * *')).toBe(false)
  })

  it('rejects an 8-field expression and a malformed year', () => {
    expect(isValidQuartzCron('0 0 6 * * ? 2026 extra')).toBe(false)
    expect(isValidQuartzCron('0 0 6 * * ? 20')).toBe(false)
  })

  it('validates per-field ranges, not just the field count', () => {
    expect(isValidQuartzCron('0 0 25 * * ?')).toBe(false) // hour out of range (>23)
    expect(isValidQuartzCron('0 60 6 * * ?')).toBe(false) // minute out of range (>59)
    expect(isValidQuartzCron('0 0 6 32 * ?')).toBe(false) // day-of-month out of range (>31)
    expect(isValidQuartzCron('0 0 6 * 13 ?')).toBe(false) // month out of range (>12)
  })

  it('accepts ranges, steps, lists and named months/days', () => {
    expect(isValidQuartzCron('0 0/15 9-17 ? * MON-FRI')).toBe(true)
    expect(isValidQuartzCron('0 0 6 1,15 JAN,JUL ?')).toBe(true)
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
})
