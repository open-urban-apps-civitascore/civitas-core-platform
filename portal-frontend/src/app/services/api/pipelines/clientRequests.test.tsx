import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook } from '@testing-library/react'
import { PropsWithChildren } from 'react'
import { vi } from 'vitest'

import { PipelinePayload } from '@/app/(main)/datasets/[datasetId]/data-flow/pipeline-editor/_types'

import { apiRequest } from '../request/apiRequest'
import { useCreatePipeline, useDeletePipeline, useUpdatePipeline } from './clientRequests'

vi.mock('../request/apiRequest', () => ({
  apiRequest: vi.fn(),
}))

const payload = {} as PipelinePayload

describe('pipeline mutations', () => {
  let queryClient: QueryClient
  const wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiRequest).mockResolvedValue({ data: {} } as never)
    queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  })

  it('invalidates the data sinks of the dataset after create', async () => {
    const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries')
    const { result } = renderHook(() => useCreatePipeline('dataset-1'), { wrapper })

    await result.current.mutateAsync(payload)

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['datasinks', 'dataset-1'] })
  })

  it('invalidates the data sinks of the dataset after update', async () => {
    const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries')
    const { result } = renderHook(() => useUpdatePipeline('dataset-1'), { wrapper })

    await result.current.mutateAsync({ pipelineId: 'pipeline-1', data: payload })

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['datasinks', 'dataset-1'] })
  })

  it('invalidates the data sinks of the dataset after delete', async () => {
    const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries')
    const { result } = renderHook(() => useDeletePipeline('dataset-1'), { wrapper })

    await result.current.mutateAsync('pipeline-1')

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['datasinks', 'dataset-1'] })
  })
})
