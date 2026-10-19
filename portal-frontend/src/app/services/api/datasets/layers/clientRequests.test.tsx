import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook } from '@testing-library/react'
import { PropsWithChildren } from 'react'
import { vi } from 'vitest'

import { LayerApiPayload } from '@/types/layers'

import { apiRequest } from '../../request/apiRequest'
import { useCreateLayer, useDeleteLayer, useUpdateLayer } from './clientRequests'

vi.mock('../../request/apiRequest', () => ({
  apiRequest: vi.fn(),
}))

const data = {} as LayerApiPayload

describe('layer mutations', () => {
  let queryClient: QueryClient
  const wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiRequest).mockResolvedValue({ data: {} } as never)
    queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  })

  it.each([
    ['create', useCreateLayer, { datasetId: 'dataset-1', data }],
    ['update', useUpdateLayer, { datasetId: 'dataset-1', layerId: 'layer-1', data }],
    ['delete', useDeleteLayer, { datasetId: 'dataset-1', layerId: 'layer-1' }],
  ] as const)('invalidates the data sinks of the dataset after %s', async (_name, useMutationHook, variables) => {
    const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries')
    const { result } = renderHook(() => useMutationHook(), { wrapper })

    await (result.current.mutateAsync as (input: typeof variables) => Promise<unknown>)(variables)

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['datasinks', 'dataset-1'] })
  })
})
