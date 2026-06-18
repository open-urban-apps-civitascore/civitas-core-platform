import { useQuery, type UseQueryOptions } from '@tanstack/react-query'
import { renderHook } from '@testing-library/react'

import { apiRequest } from '@/app/services/api/request/apiRequest'

import { useDataQuery } from './use-data-query'

vi.mock('@tanstack/react-query', async importOriginal => {
  const mod = await importOriginal<typeof import('@tanstack/react-query')>()
  return { ...mod, useQuery: vi.fn() }
})

vi.mock('@/app/services/api/request/apiRequest', () => ({
  apiRequest: vi.fn(),
}))

type CapturedQueryOptions = Pick<UseQueryOptions, 'queryKey' | 'enabled'> & {
  queryFn: (...args: unknown[]) => Promise<unknown>
  placeholderData: (previousData: unknown) => unknown
}

const lastQueryOptions = (): CapturedQueryOptions =>
  vi.mocked(useQuery).mock.lastCall?.[0] as unknown as CapturedQueryOptions

describe('useDataQuery', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useQuery).mockReturnValue({} as never)
  })

  describe('queryKey', () => {
    it('uses queryKey as first element when queryKey is provided', () => {
      renderHook(() => useDataQuery({ key: 'items', queryKey: 'custom-key', errorMessage: 'Error' }))
      expect(lastQueryOptions().queryKey).toEqual(['custom-key', undefined])
    })

    it('falls back to key as first element when queryKey is not provided', () => {
      renderHook(() => useDataQuery({ key: 'items', errorMessage: 'Error' }))
      expect(lastQueryOptions().queryKey).toEqual(['items', undefined])
    })

    it('uses id as second element when id is provided', () => {
      renderHook(() => useDataQuery({ key: 'items', id: 'abc', errorMessage: 'Error' }))
      expect(lastQueryOptions().queryKey).toEqual(['items', 'abc'])
    })

    it('uses params.toString() as second element when params are provided and id is not', () => {
      const params = new URLSearchParams({ page: '1' })
      renderHook(() => useDataQuery({ key: 'items', params, errorMessage: 'Error' }))
      expect(lastQueryOptions().queryKey).toEqual(['items', params.toString()])
    })

    it('has undefined as second element when neither id nor params are provided', () => {
      renderHook(() => useDataQuery({ key: 'items', errorMessage: 'Error' }))
      expect(lastQueryOptions().queryKey).toEqual(['items', undefined])
    })

    it('uses id as second element when both id and params are provided', () => {
      const params = new URLSearchParams({ page: '1' })
      renderHook(() => useDataQuery({ key: 'items', id: 'abc', params, errorMessage: 'Error' }))
      expect(lastQueryOptions().queryKey).toEqual(['items', 'abc'])
    })
  })

  describe('queryFn forwards options to apiRequest', () => {
    it('calls apiRequest with /${key}/${id} when id is provided', async () => {
      renderHook(() => useDataQuery({ key: 'items', id: 'abc', errorMessage: 'Error' }))
      await lastQueryOptions().queryFn!({} as never)
      expect(vi.mocked(apiRequest)).toHaveBeenCalledWith(expect.objectContaining({ endpoint: '/items/abc' }))
    })

    it('builds endpoint /${key} when id is not provided', async () => {
      renderHook(() => useDataQuery({ key: 'items', errorMessage: 'Error' }))
      await lastQueryOptions().queryFn!({} as never)
      expect(vi.mocked(apiRequest)).toHaveBeenCalledWith(expect.objectContaining({ endpoint: '/items' }))
    })

    it('forwards headers to apiRequest', async () => {
      const headers = { Authorization: 'Bearer token' }
      renderHook(() => useDataQuery({ key: 'items', headers, errorMessage: 'Error' }))
      await lastQueryOptions().queryFn!({} as never)
      expect(vi.mocked(apiRequest)).toHaveBeenCalledWith(expect.objectContaining({ headers }))
    })

    it('forwards errorMessage to apiRequest', async () => {
      renderHook(() => useDataQuery({ key: 'items', errorMessage: 'Something went wrong' }))
      await lastQueryOptions().queryFn!({} as never)
      expect(vi.mocked(apiRequest)).toHaveBeenCalledWith(
        expect.objectContaining({ errorMessage: 'Something went wrong' }),
      )
    })

    it('forwards params to apiRequest', async () => {
      const params = new URLSearchParams({ page: '1', size: '10' })
      renderHook(() => useDataQuery({ key: 'items', params, errorMessage: 'Error' }))
      await lastQueryOptions().queryFn!({} as never)
      expect(vi.mocked(apiRequest)).toHaveBeenCalledWith(expect.objectContaining({ params }))
    })

    it('propagates apiRequest rejection', async () => {
      const error = new Error('Network error')
      vi.mocked(apiRequest).mockRejectedValueOnce(error)

      renderHook(() => useDataQuery({ key: 'items', errorMessage: 'Error' }))
      await expect(lastQueryOptions().queryFn!({} as never)).rejects.toThrow('Network error')
    })
  })

  describe('isEnabled', () => {
    it.each([
      { isEnabled: true, expected: true },
      { isEnabled: false, expected: false },
      { isEnabled: undefined, expected: undefined },
    ])('passes enabled: $expected to useQuery when isEnabled is $isEnabled', ({ isEnabled, expected }) => {
      renderHook(() => useDataQuery({ key: 'items', errorMessage: 'Error', isEnabled }))
      expect(lastQueryOptions().enabled).toBe(expected)
    })
  })

  describe('placeholderData', () => {
    it('returns the same reference as previous data (identity function)', () => {
      renderHook(() => useDataQuery({ key: 'items', errorMessage: 'Error' }))
      const previousData = { data: [{ id: '1' }] }
      expect(lastQueryOptions().placeholderData(previousData)).toBe(previousData)
    })

    it('returns undefined when there is no previous data', () => {
      renderHook(() => useDataQuery({ key: 'items', errorMessage: 'Error' }))
      expect(lastQueryOptions().placeholderData(undefined)).toBeUndefined()
    })
  })

  describe('return value', () => {
    it('returns the result of useQuery', () => {
      const queryResult = { data: { items: [] }, isLoading: false, isError: false }
      vi.mocked(useQuery).mockReturnValue(queryResult as never)

      const { result } = renderHook(() => useDataQuery({ key: 'items', errorMessage: 'Error' }))
      expect(result.current).toBe(queryResult)
    })
  })
})
