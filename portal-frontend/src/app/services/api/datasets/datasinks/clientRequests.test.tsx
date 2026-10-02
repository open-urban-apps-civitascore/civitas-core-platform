import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { PropsWithChildren } from 'react'
import { vi } from 'vitest'

import { apiRequest } from '../../request/apiRequest'
import { useGetDataSinks } from './clientRequests'

vi.mock('../../request/apiRequest', () => ({
  apiRequest: vi.fn(),
}))

describe('useGetDataSinks', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiRequest).mockResolvedValue({ data: [] } as never)
  })

  it('refetches fresh data on mount when refetchOnMount is always', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } })
    const wrapper = ({ children }: PropsWithChildren) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
    const useSinks = () => useGetDataSinks('dataset-1', { isEnabled: true, refetchOnMount: 'always' })

    const first = renderHook(useSinks, { wrapper })
    await waitFor(() => expect(first.result.current.isSuccess).toBe(true))
    first.unmount()

    renderHook(useSinks, { wrapper })

    await waitFor(() => expect(apiRequest).toHaveBeenCalledTimes(2))
  })
})
