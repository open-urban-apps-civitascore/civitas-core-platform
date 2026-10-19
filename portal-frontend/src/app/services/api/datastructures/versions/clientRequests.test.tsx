import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { PropsWithChildren } from 'react'
import { vi } from 'vitest'

import { BREADCRUMB_QUERY_KEY } from '@/app/services/api/breadcrumbs/clientRequests'

import { apiRequest } from '../../request/apiRequest'
import { useUpdateDatastructureVersion } from './clientRequests'

vi.mock('../../request/apiRequest', () => ({
  apiRequest: vi.fn(),
}))

describe('useDatastructureVersionMutation', () => {
  it('reloads the breadcrumb after a version write', async () => {
    vi.mocked(apiRequest).mockResolvedValue({ data: {} } as never)
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries')
    const wrapper = ({ children }: PropsWithChildren) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )

    const { result } = renderHook(() => useUpdateDatastructureVersion(), { wrapper })
    await result.current.mutateAsync({
      data: { id: 'version-1' } as never,
      endpoint: '/datastructures/ds-1/versions/version-1',
    })

    // A version write can change the number the breadcrumb names it by.
    await waitFor(() =>
      expect(invalidateQueries).toHaveBeenCalledWith(expect.objectContaining({ queryKey: [BREADCRUMB_QUERY_KEY] })),
    )
  })
})
