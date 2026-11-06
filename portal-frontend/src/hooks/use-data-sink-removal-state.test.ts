import { renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetDataset } from '@/app/services/api/datasets/clientRequests'
import { useGetDataSinks } from '@/app/services/api/datasets/datasinks/clientRequests'
import { useGetLayers } from '@/app/services/api/datasets/layers/clientRequests'

import { useDataSinkRemovalState } from './use-data-sink-removal-state'

vi.mock('@/app/services/api/datasets/clientRequests')
vi.mock('@/app/services/api/datasets/datasinks/clientRequests')
vi.mock('@/app/services/api/datasets/layers/clientRequests')

const DATA_SINK = { id: 'sink-1', dataSinkType: 'POSTGIS', provisioned: true, pipelineIds: ['pipeline-1'] }
const LAYER = { id: 'layer-1', dataSinkId: 'sink-1', namedApiId: 'api-1', layerName: 'roads', title: 'Roads' }
const API = { id: 'api-1', slug: 'map', name: 'Map', standard: 'OWS' }

const mockLoaded = (loaded: { dataSinks: boolean; layers: boolean; dataset: boolean }) => {
  vi.mocked(useGetDataSinks).mockReturnValue({
    data: loaded.dataSinks ? { data: [DATA_SINK] } : undefined,
  } as ReturnType<typeof useGetDataSinks>)
  vi.mocked(useGetLayers).mockReturnValue({
    data: loaded.layers ? { data: [LAYER] } : undefined,
  } as ReturnType<typeof useGetLayers>)
  vi.mocked(useGetDataset).mockReturnValue({
    data: loaded.dataset ? { data: { id: 'dataset-1', namedApis: [API] } } : undefined,
  } as ReturnType<typeof useGetDataset>)
}

describe('useDataSinkRemovalState', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // A state built before every list is loaded would plan a removal without the Layers or APIs it takes along.
  it.each([
    { dataSinks: false, layers: true, dataset: true },
    { dataSinks: true, layers: false, dataset: true },
    { dataSinks: true, layers: true, dataset: false },
  ])('is null until the Data sinks, Layers and Dataset are all loaded (%o)', loaded => {
    mockLoaded(loaded)

    const { result } = renderHook(() => useDataSinkRemovalState('dataset-1'))

    expect(result.current).toBeNull()
  })

  it('keeps the ids that link Layers to their Data sink and API', () => {
    mockLoaded({ dataSinks: true, layers: true, dataset: true })

    const { result } = renderHook(() => useDataSinkRemovalState('dataset-1'))

    expect(result.current).toEqual({
      dataSinks: [DATA_SINK],
      layers: [LAYER],
      apis: [API],
    })
  })
})
