import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook } from '@testing-library/react'
import { PropsWithChildren } from 'react'
import { vi } from 'vitest'

import { apiRequest } from '../request/apiRequest'
import { useDeleteMapping, useUpdateMapping } from './clientRequests'

vi.mock('../request/apiRequest', () => ({
  apiRequest: vi.fn(),
}))

const MAPPING_ID = '0bdbebd9-e766-86ff-bec5-f04da0a8abdd'
const LOGICAL_URN = `urn:core:platform:civitas:mapping:common:${MAPPING_ID}`

// A pipeline saved before mappings had their own path holds only mappingLogicalUrn on its nodes;
// these tests guard that such a node still reaches its mapping.
describe('mapping mutations', () => {
  let queryClient: QueryClient
  const wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiRequest).mockResolvedValue({ data: {} } as never)
    queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  })

  it('sends update to the item path of the mapping without its logical URN in the body', async () => {
    const { result } = renderHook(() => useUpdateMapping('dataset-1'), { wrapper })

    await result.current.mutateAsync({ logicalUrn: LOGICAL_URN, title: 'Sensors', fields: {} })

    expect(apiRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'PUT',
        endpoint: `/datasets/dataset-1/mappings/${MAPPING_ID}`,
        data: { title: 'Sensors', fields: {} },
      }),
    )
  })

  it('sends delete to the item path of the mapping', async () => {
    const { result } = renderHook(() => useDeleteMapping('dataset-1'), { wrapper })

    await result.current.mutateAsync(LOGICAL_URN)

    expect(apiRequest).toHaveBeenCalledWith(
      expect.objectContaining({ method: 'DELETE', endpoint: `/datasets/dataset-1/mappings/${MAPPING_ID}` }),
    )
    expect(vi.mocked(apiRequest).mock.calls[0][0]).not.toHaveProperty('params')
  })
})
