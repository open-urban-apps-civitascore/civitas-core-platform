import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook } from '@testing-library/react'
import { PropsWithChildren } from 'react'
import { vi } from 'vitest'

import { apiRequest } from '@/app/services/api/request/apiRequest'

import { useUpdateMutation } from './use-update-mutation'

vi.mock('@/app/services/api/request/apiRequest', () => ({
  apiRequest: vi.fn(),
}))

type ItemPatch = { id: string; name: string; description: string }

const wrapper = ({ children }: PropsWithChildren) => (
  <QueryClientProvider client={new QueryClient({ defaultOptions: { mutations: { retry: false } } })}>
    {children}
  </QueryClientProvider>
)

describe('useUpdateMutation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiRequest).mockResolvedValue({ data: {} } as never)
  })

  it('sends the id in the default endpoint and leaves it out of the request body', async () => {
    const { result } = renderHook(
      () => useUpdateMutation<unknown, ItemPatch>({ method: 'PATCH', key: 'items', errorMessage: 'Error' }),
      { wrapper },
    )

    await result.current.mutateAsync({ id: 'item-1', name: 'Name', description: 'Description' })

    expect(apiRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'PATCH',
        endpoint: '/items/item-1',
        data: { name: 'Name', description: 'Description' },
      }),
    )
  })
})
