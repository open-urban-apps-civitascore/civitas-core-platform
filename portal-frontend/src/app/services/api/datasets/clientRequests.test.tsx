import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook } from '@testing-library/react'
import { PropsWithChildren } from 'react'
import { vi } from 'vitest'

import { DatasetUpdateApiData } from '@/types/datasets'

import { apiRequest } from '../request/apiRequest'
import { useUpdateReadyDatasetMeta, useUpdateReleasedDatasetMeta } from './clientRequests'

vi.mock('../request/apiRequest', () => ({
  apiRequest: vi.fn(),
}))

const wrapper = ({ children }: PropsWithChildren) => (
  <QueryClientProvider client={new QueryClient({ defaultOptions: { mutations: { retry: false } } })}>
    {children}
  </QueryClientProvider>
)

const metaPatch: DatasetUpdateApiData = { id: 'dataset-1', name: 'Name', description: 'Description' }

describe('dataset metadata mutations', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiRequest).mockResolvedValue({ data: {} } as never)
  })

  it.each([
    ['ready', useUpdateReadyDatasetMeta],
    ['released', useUpdateReleasedDatasetMeta],
  ])('patches the %s metadata endpoint without the id in the body', async (stage, useMetaMutation) => {
    const { result } = renderHook(() => useMetaMutation(), { wrapper })

    await result.current.mutateAsync(metaPatch)

    expect(apiRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'PATCH',
        endpoint: `/datasets/dataset-1/${stage}/meta`,
        data: { name: 'Name', description: 'Description' },
      }),
    )
  })
})
