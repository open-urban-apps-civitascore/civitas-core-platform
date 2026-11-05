import { describe, expect, it } from 'vitest'

import { DataSinkRemoval, deletesStoredData, mergeRemovals, NO_REMOVAL, RemovedDataSink } from './dataSinkRemoval'

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
