import { describe, expect, it } from 'vitest'

import { DataSink, DATASINK_TYPES, DataSinkType } from '@/types/datasinks'
import { API_STANDARDS, ApiStandard, NamedApi } from '@/types/namedApis'

import { getPipelineDeleteBlockReason } from './pipelineDeleteGuard'

const PIPELINE_ID = 'pipeline-1'
const OTHER_PIPELINE_ID = 'pipeline-2'

const makeSink = (dataSinkType: DataSinkType, pipelineId: string | null, overrides: Partial<DataSink> = {}): DataSink =>
  ({
    id: `${dataSinkType}-${pipelineId}`,
    datasetId: 'dataset-1',
    pipelineId,
    dataSinkType,
    inUseByLayer: false,
    ...overrides,
  }) as DataSink

const makeApi = (standard: ApiStandard): NamedApi => ({ name: standard, slug: standard.toLowerCase(), standard })

describe('getPipelineDeleteBlockReason', () => {
  it('returns inUseByLayer before the last-sink reasons', () => {
    const sinks = [makeSink(DATASINK_TYPES.POSTGIS, PIPELINE_ID, { inUseByLayer: true })]

    expect(getPipelineDeleteBlockReason(PIPELINE_ID, sinks, [makeApi(API_STANDARDS.OWS)])).toBe('inUseByLayer')
  })

  it('returns lastPostgisWithOws for the last POSTGIS sink when an OWS API exists', () => {
    const sinks = [makeSink(DATASINK_TYPES.POSTGIS, PIPELINE_ID)]

    expect(getPipelineDeleteBlockReason(PIPELINE_ID, sinks, [makeApi(API_STANDARDS.OWS)])).toBe('lastPostgisWithOws')
  })

  it('returns lastFrostWithSta for the last FROST sink when an STA API exists', () => {
    const sinks = [makeSink(DATASINK_TYPES.FROST, PIPELINE_ID)]

    expect(getPipelineDeleteBlockReason(PIPELINE_ID, sinks, [makeApi(API_STANDARDS.STA)])).toBe('lastFrostWithSta')
  })

  it('ignores sinks of deleted pipelines', () => {
    const sinks = [makeSink(DATASINK_TYPES.FROST, PIPELINE_ID), makeSink(DATASINK_TYPES.FROST, null)]

    expect(getPipelineDeleteBlockReason(PIPELINE_ID, sinks, [makeApi(API_STANDARDS.STA)])).toBe('lastFrostWithSta')
  })

  it('returns null when another pipeline has a sink of the same type', () => {
    const sinks = [makeSink(DATASINK_TYPES.FROST, PIPELINE_ID), makeSink(DATASINK_TYPES.FROST, OTHER_PIPELINE_ID)]

    expect(getPipelineDeleteBlockReason(PIPELINE_ID, sinks, [makeApi(API_STANDARDS.STA)])).toBeNull()
  })

  it('returns null when no API uses the sink type of the pipeline', () => {
    const sinks = [makeSink(DATASINK_TYPES.FROST, PIPELINE_ID)]

    expect(getPipelineDeleteBlockReason(PIPELINE_ID, sinks, [makeApi(API_STANDARDS.OWS)])).toBeNull()
  })

  it('returns null when the pipeline has no sink of the API type', () => {
    const sinks = [makeSink(DATASINK_TYPES.FROST, OTHER_PIPELINE_ID)]

    expect(getPipelineDeleteBlockReason(PIPELINE_ID, sinks, [makeApi(API_STANDARDS.STA)])).toBeNull()
  })
})
