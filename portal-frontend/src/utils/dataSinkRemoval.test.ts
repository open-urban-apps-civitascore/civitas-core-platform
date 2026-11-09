import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { describe, expect, it } from 'vitest'

import type {
  DataSinkRemoval,
  DataSinkRemovalRequest,
  PlannedApi,
  PlannedDataSink,
  PlannedLayer,
  RemovedDataSink,
} from '@/types/dataSinkRemoval'
import type { DataSinkType } from '@/types/datasinks'
import type { ApiStandard } from '@/types/namedApis'

import {
  deletesStoredData,
  mergeRemovals,
  needsConfirmation,
  NO_REMOVAL,
  planDataSinkRemoval,
  planPipelineDelete,
  planPipelineSaves,
} from './dataSinkRemoval'

const sink = (id: string, provisioned: boolean): RemovedDataSink => ({ id, dataSinkType: 'FROST', provisioned })

const ROADS = { id: 'layer-roads', layerName: 'roads', title: 'Roads' }
const TREES = { id: 'layer-trees', layerName: 'trees', title: null }
const MAPS = { id: 'api-maps', slug: 'maps', name: 'Maps', standard: 'OWS' } as const

describe('deletesStoredData', () => {
  it('is true when a removed data sink is provisioned', () => {
    expect(deletesStoredData({ ...NO_REMOVAL, dataSinks: [sink('a', false), sink('b', true)] })).toBe(true)
  })

  it('is false when no removed data sink is provisioned', () => {
    expect(deletesStoredData({ ...NO_REMOVAL, dataSinks: [sink('a', false)] })).toBe(false)
  })
})

describe('mergeRemovals', () => {
  it('combines the removals and lists an item named by several only once', () => {
    const first: DataSinkRemoval = { dataSinks: [sink('a', true)], layers: [ROADS], apis: [MAPS] }
    const second: DataSinkRemoval = { dataSinks: [sink('a', true), sink('b', false)], layers: [TREES], apis: [MAPS] }

    expect(mergeRemovals([first, second])).toEqual({
      dataSinks: [sink('a', true), sink('b', false)],
      layers: [ROADS, TREES],
      apis: [MAPS],
    })
  })

  it('is empty without removals', () => {
    expect(mergeRemovals([])).toEqual(NO_REMOVAL)
  })
})

interface RemovalCase {
  name: string
  dataSinks: { id: string; type: DataSinkType; provisioned: boolean; pipelineIds: string[] }[]
  layers: { id: string; dataSinkId: string; namedApiId: string | null }[]
  apis: { id: string; standard: ApiStandard }[]
  request: DataSinkRemovalRequest
  expected: { dataSinks: string[]; layers: string[]; apis: string[]; deletesStoredData: boolean }
}

const SHARED_CASES_FILE = resolve(__dirname, '../../../api/portal-backend/data-sink-removal-cases.json')
const { cases } = JSON.parse(readFileSync(SHARED_CASES_FILE, 'utf8')) as { cases: RemovalCase[] }

const sortedIds = (items: { id: string }[]) => items.map(item => item.id).sort()

describe('planDataSinkRemoval', () => {
  it.each(cases.map(removalCase => [removalCase.name, removalCase] as const))('%s', (_name, removalCase) => {
    const removal = planDataSinkRemoval({
      dataSinks: removalCase.dataSinks.map(({ type, ...dataSink }) => ({ ...dataSink, dataSinkType: type })),
      layers: removalCase.layers.map(layer => ({ ...layer, layerName: layer.id, title: null })),
      apis: removalCase.apis.map(api => ({ ...api, slug: api.id, name: api.id })),
      request: removalCase.request,
    })

    expect(sortedIds(removal.dataSinks)).toEqual([...removalCase.expected.dataSinks].sort())
    expect(sortedIds(removal.layers)).toEqual([...removalCase.expected.layers].sort())
    expect(sortedIds(removal.apis)).toEqual([...removalCase.expected.apis].sort())
    expect(deletesStoredData(removal)).toBe(removalCase.expected.deletesStoredData)
  })
})

describe('needsConfirmation', () => {
  it('is false for a removal of Data sinks without stored data, Layers or APIs', () => {
    expect(needsConfirmation({ ...NO_REMOVAL, dataSinks: [sink('a', false)] })).toBe(false)
  })

  it('is true for a removal of a Layer alone', () => {
    expect(needsConfirmation({ ...NO_REMOVAL, layers: [ROADS] })).toBe(true)
  })
})

const STATE_SINK = (id: string, pipelineIds: string[], provisioned = true): PlannedDataSink => ({
  id,
  dataSinkType: 'POSTGIS',
  provisioned,
  pipelineIds,
})
const OWS_API: PlannedApi = { id: 'ows', slug: 'map', name: 'Map', standard: 'OWS' }
const MAP_LAYER: PlannedLayer = { id: 'layer', dataSinkId: 'shared', namedApiId: 'ows', layerName: 'map', title: 'Map' }

describe('planPipelineSaves', () => {
  it('removes a Data sink that two saves unlink in the second save', () => {
    const state = { dataSinks: [STATE_SINK('shared', ['first', 'second'])], layers: [MAP_LAYER], apis: [OWS_API] }

    const [first, second] = planPipelineSaves(state, [
      { pipelineId: 'first', dataSinkIds: [] },
      { pipelineId: 'second', dataSinkIds: [] },
    ])

    expect(first).toEqual(NO_REMOVAL)
    expect(sortedIds(second.dataSinks)).toEqual(['shared'])
    expect(sortedIds(second.layers)).toEqual(['layer'])
    expect(sortedIds(second.apis)).toEqual(['ows'])
  })

  it('keeps the API when a later save links a Data sink that an earlier save created', () => {
    const state = {
      dataSinks: [STATE_SINK('old', ['first']), STATE_SINK('new', [], false)],
      layers: [],
      apis: [OWS_API],
    }

    const [first, second] = planPipelineSaves(state, [
      { pipelineId: 'first', dataSinkIds: [] },
      { pipelineId: 'second', dataSinkIds: ['new'] },
    ])

    expect(sortedIds(first.dataSinks)).toEqual(['old'])
    expect(first.apis).toEqual([])
    expect(second).toEqual(NO_REMOVAL)
  })
})

describe('planPipelineDelete', () => {
  it('removes every link of the Pipeline', () => {
    const state = { dataSinks: [STATE_SINK('shared', ['deleted'])], layers: [MAP_LAYER], apis: [OWS_API] }

    const removal = planPipelineDelete(state, 'deleted')

    expect(sortedIds(removal.dataSinks)).toEqual(['shared'])
    expect(sortedIds(removal.apis)).toEqual(['ows'])
  })
})
