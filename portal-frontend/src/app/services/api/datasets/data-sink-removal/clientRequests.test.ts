import { vi } from 'vitest'

import { apiRequest } from '../../request/apiRequest'
import { getDatasetResources } from './clientRequests'

vi.mock('../../request/apiRequest', () => ({
  apiRequest: vi.fn(),
}))

const responses: Readonly<Record<string, unknown>> = {
  '/datasets/dataset-1/datasinks': [{ id: 'sink-1' }],
  '/datasets/dataset-1/layers': [{ id: 'layer-1' }],
  '/datasets/dataset-1': { namedApis: [{ slug: 'ows' }] },
}

describe('getDatasetResources', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiRequest).mockImplementation(async ({ endpoint }) => ({ data: responses[endpoint] }) as never)
  })

  // Without a page size the lists stop at the backend's default of 20, and the dialog misses removed sinks.
  it('asks for the largest page of data sinks and layers', async () => {
    await getDatasetResources('dataset-1')

    const listCalls = vi.mocked(apiRequest).mock.calls.filter(([config]) => config.endpoint !== '/datasets/dataset-1')
    expect(listCalls).toHaveLength(2)
    for (const [config] of listCalls) expect(config.params?.get('size')).toBe('200')
  })

  it('returns the data sinks, layers and named APIs of the dataset', async () => {
    expect(await getDatasetResources('dataset-1')).toEqual({
      dataSinks: [{ id: 'sink-1' }],
      layers: [{ id: 'layer-1' }],
      apis: [{ slug: 'ows' }],
    })
  })

  it('returns no APIs when the dataset has none', async () => {
    vi.mocked(apiRequest).mockImplementation(
      async ({ endpoint }) => ({ data: endpoint === '/datasets/dataset-1' ? {} : responses[endpoint] }) as never,
    )

    expect((await getDatasetResources('dataset-1')).apis).toEqual([])
  })
})
