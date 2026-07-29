import { describe, expect, it } from 'vitest'

import type { DataSourceNodeData, PipelineNodeData } from '../_types/nodes'
import { PIPELINE_NODE_TYPES } from '../_types/pipeline'
import { CONVERTIBLE_TO_RECORDS, isFormAccepted, NODE_FLOW_DECLARATIONS } from './nodeFlow'

/**
 * These tests pin the frontend's mirror of the adapter's stage vocabulary (PayloadForm,
 * CONVERTIBLE_TO_RECORDS, the source/sink declarations). The adapter side is pinned by its own
 * tests; a change on either side must consciously touch both.
 */

const sourceData = (connector?: string): DataSourceNodeData => ({
  label: 'Source',
  configured: true,
  entityType: 'datasource',
  entityMetadata: connector === undefined ? {} : { connector },
})

const anyData: PipelineNodeData = { label: 'x', configured: true, cronExpression: '' }

describe('CONVERTIBLE_TO_RECORDS (coercion table)', () => {
  it('contains exactly the forms the implicit ConvertRecord turns into RECORDS', () => {
    expect([...CONVERTIBLE_TO_RECORDS].sort()).toEqual(['RAW_JSON', 'STA_ENVELOPE'])
  })
})

describe('isFormAccepted', () => {
  it('accepts a directly declared form', () => {
    expect(isFormAccepted('STA_ENVELOPE', ['STA_ENVELOPE'])).toBe(true)
  })

  it('coerces convertible forms when RECORDS is accepted', () => {
    expect(isFormAccepted('RAW_JSON', ['RECORDS'])).toBe(true)
    expect(isFormAccepted('STA_ENVELOPE', ['RECORDS'])).toBe(true)
  })

  it('does not coerce towards a non-RECORDS acceptance', () => {
    expect(isFormAccepted('RECORDS', ['STA_ENVELOPE'])).toBe(false)
    expect(isFormAccepted('RAW_JSON', ['STA_ENVELOPE'])).toBe(false)
  })
})

describe('node flow declarations mirror the adapter stages', () => {
  it('declares roles per node type', () => {
    const roles = Object.fromEntries(Object.entries(NODE_FLOW_DECLARATIONS).map(([type, decl]) => [type, decl.role]))
    expect(roles).toEqual({
      start: 'control',
      end: 'control',
      cron: 'trigger',
      dataSource: 'source',
      mapping: 'transform',
      frost: 'sink',
      geoPersistence: 'sink',
    })
  })

  describe('dataSource', () => {
    const decl = NODE_FLOW_DECLARATIONS[PIPELINE_NODE_TYPES.DataSource]
    if (decl.role !== 'source') throw new Error('dataSource must declare the source role')

    it('MQTT emits the SensorThings envelope and rejects a schedule (push source)', () => {
      expect(decl.output(sourceData('MQTT'))).toBe('STA_ENVELOPE')
      expect(decl.acceptsSchedule(sourceData('MQTT'))).toBe(false)
    })

    it('SQL emits records and accepts a schedule (pull source)', () => {
      expect(decl.output(sourceData('SQL'))).toBe('RECORDS')
      expect(decl.acceptsSchedule(sourceData('SQL'))).toBe(true)
    })

    it('an unknown connector cannot be verified either way', () => {
      expect(decl.output(sourceData())).toBeUndefined()
      expect(decl.acceptsSchedule(sourceData())).toBeUndefined()
      expect(decl.output(sourceData('HTTP'))).toBeUndefined()
      expect(decl.acceptsSchedule(sourceData('HTTP'))).toBeUndefined()
    })
  })

  describe('mapping', () => {
    const decl = NODE_FLOW_DECLARATIONS[PIPELINE_NODE_TYPES.Mapping]
    if (decl.role !== 'transform') throw new Error('mapping must declare the transform role')

    it('is a RECORDS → RECORDS transform', () => {
      expect(decl.output(anyData)).toBe('RECORDS')
      expect(decl.acceptedInputs({ mappedUpstream: false })).toEqual(['RECORDS'])
      expect(decl.acceptedInputs({ mappedUpstream: true })).toEqual(['RECORDS'])
    })
  })

  describe('frost sink', () => {
    const decl = NODE_FLOW_DECLARATIONS[PIPELINE_NODE_TYPES.Frost]
    if (decl.role !== 'sink') throw new Error('frost must declare the sink role')

    it('demands the envelope in passthrough mode and records with a mapping upstream', () => {
      expect(decl.acceptedInputs({ mappedUpstream: false })).toEqual(['STA_ENVELOPE'])
      expect(decl.acceptedInputs({ mappedUpstream: true })).toEqual(['RECORDS'])
    })
  })

  describe('geoPersistence sink', () => {
    const decl = NODE_FLOW_DECLARATIONS[PIPELINE_NODE_TYPES.GeoPersistence]
    if (decl.role !== 'sink') throw new Error('geoPersistence must declare the sink role')

    it('always consumes records, independent of a mapping upstream', () => {
      expect(decl.acceptedInputs({ mappedUpstream: false })).toEqual(['RECORDS'])
      expect(decl.acceptedInputs({ mappedUpstream: true })).toEqual(['RECORDS'])
    })
  })
})
