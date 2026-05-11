import { useQuery } from '@tanstack/react-query'
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

type CapturedQueryOptions = {
  queryKey: readonly unknown[]
  queryFn: () => Promise<unknown>
  enabled?: boolean
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
  })

  describe('endpoint', () => {
    it('calls apiRequest with /${key}/${id} when id is provided', async () => {
      renderHook(() => useDataQuery({ key: 'items', id: 'abc', errorMessage: 'Error' }))
      await lastQueryOptions().queryFn()
      expect(vi.mocked(apiRequest)).toHaveBeenCalledWith(expect.objectContaining({ endpoint: '/items/abc' }))
    })

    it('calls apiRequest with /${key} when id is not provided', async () => {
      renderHook(() => useDataQuery({ key: 'items', errorMessage: 'Error' }))
      await lastQueryOptions().queryFn()
      expect(vi.mocked(apiRequest)).toHaveBeenCalledWith(expect.objectContaining({ endpoint: '/items' }))
    })
  })

  describe('isEnabled', () => {
    it('passes enabled: true to useQuery when isEnabled is true', () => {
      renderHook(() => useDataQuery({ key: 'items', errorMessage: 'Error', isEnabled: true }))
      expect(lastQueryOptions().enabled).toBe(true)
    })

    it('passes enabled: false to useQuery when isEnabled is false', () => {
      renderHook(() => useDataQuery({ key: 'items', errorMessage: 'Error', isEnabled: false }))
      expect(lastQueryOptions().enabled).toBe(false)
    })
  })

  describe('placeholderData', () => {
    it('returns previous data when previous data exists', () => {
      renderHook(() => useDataQuery({ key: 'items', errorMessage: 'Error' }))
      const previousData = { data: [{ id: '1' }] }
      expect(lastQueryOptions().placeholderData(previousData)).toBe(previousData)
    })

    it('returns undefined when there is no previous data', () => {
      renderHook(() => useDataQuery({ key: 'items', errorMessage: 'Error' }))
      expect(lastQueryOptions().placeholderData(undefined)).toBeUndefined()
    })
  })
})
