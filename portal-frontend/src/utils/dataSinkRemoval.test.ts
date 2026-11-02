import { describe, expect, it } from 'vitest'

import { DataSink, DataSinkType } from '@/types/datasinks'
import { Layer } from '@/types/layers'
import { ApiStandard, NamedApi } from '@/types/namedApis'

import { getDataSinkRemoval, getPipelineDataSinkIds, hasRemovalConsequences } from './dataSinkRemoval'

const sink = (id: string, dataSinkType: DataSinkType, overrides: Partial<DataSink> = {}) =>
  ({ id, dataSinkType, pipelineIds: [], provisioned: false, ...overrides }) as DataSink

const layer = (id: string, dataSinkId: string) => ({ id, dataSinkId, layerName: id, title: id }) as Layer

const api = (slug: string, standard: ApiStandard): NamedApi => ({ slug, name: slug, standard })

const APIS = [api('ows', 'OWS'), api('sta', 'STA'), api('custom', 'CUSTOM')]

describe('getDataSinkRemoval', () => {
  it('removes the API whose last backing data sink goes and keeps the others', () => {
    const dataSinks = [sink('frost-1', 'FROST'), sink('postgis-1', 'POSTGIS')]

    const removal = getDataSinkRemoval(['frost-1'], { dataSinks, layers: [], apis: APIS })

    expect(removal.apis).toEqual([api('sta', 'STA')])
  })

  it('keeps the API while another data sink of its type remains', () => {
    const dataSinks = [sink('frost-1', 'FROST'), sink('frost-2', 'FROST'), sink('postgis-1', 'POSTGIS')]

    const removal = getDataSinkRemoval(['frost-1'], { dataSinks, layers: [], apis: APIS })

    expect(removal.apis).toEqual([])
  })

  it('keeps an API that no removed data sink backs, even without a backing data sink', () => {
    const dataSinks = [sink('postgis-1', 'POSTGIS')]

    const removal = getDataSinkRemoval(['postgis-1'], { dataSinks, layers: [], apis: APIS })

    expect(removal.apis).toEqual([api('ows', 'OWS')])
  })

  it('removes only the Layers of the removed data sinks', () => {
    const dataSinks = [sink('postgis-1', 'POSTGIS'), sink('postgis-2', 'POSTGIS'), sink('frost-1', 'FROST')]
    const layers = [layer('trees', 'postgis-1'), layer('roads', 'postgis-2')]

    const removal = getDataSinkRemoval(['postgis-1'], { dataSinks, layers, apis: APIS })

    expect(removal.layers).toEqual([layer('trees', 'postgis-1')])
    expect(removal.apis).toEqual([])
  })

  it('reports stored data only for a provisioned data sink', () => {
    const dataSinks = [sink('frost-1', 'FROST'), sink('frost-2', 'FROST', { provisioned: true })]
    const context = { dataSinks, layers: [], apis: [] }

    expect(getDataSinkRemoval(['frost-1'], context).hasStoredData).toBe(false)
    expect(getDataSinkRemoval(['frost-2'], context).hasStoredData).toBe(true)
  })
})

describe('hasRemovalConsequences', () => {
  it('is false when nothing but an unprovisioned data sink goes', () => {
    const dataSinks = [sink('frost-1', 'FROST'), sink('frost-2', 'FROST'), sink('postgis-1', 'POSTGIS')]

    expect(hasRemovalConsequences(getDataSinkRemoval(['frost-1'], { dataSinks, layers: [], apis: APIS }))).toBe(false)
  })
})

describe('getPipelineDataSinkIds', () => {
  it('returns the data sinks the pipeline writes into', () => {
    const dataSinks = [
      sink('frost-1', 'FROST', { pipelineIds: ['p-1'] }),
      sink('frost-2', 'FROST', { pipelineIds: ['p-2'] }),
    ]

    expect(getPipelineDataSinkIds(dataSinks, 'p-1')).toEqual(['frost-1'])
  })
})
