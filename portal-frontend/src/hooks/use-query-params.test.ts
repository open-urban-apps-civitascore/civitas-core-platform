import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { QUERY_PARAMS } from '@/const/searchParams'

import { useQueryParams } from './use-query-params'

const push = vi.fn()
const replace = vi.fn()
let mockSearchParams: URLSearchParams
const pathname = '/test'

vi.mock('next/navigation', () => {
  return {
    useRouter: () => ({ push, replace }),
    usePathname: () => pathname,
    useSearchParams: () => mockSearchParams,
    ReadonlyURLSearchParams: URLSearchParams,
  }
})

describe('useQueryParams', () => {
  beforeEach(() => {
    push.mockClear()
    replace.mockClear()
    mockSearchParams = new URLSearchParams()
  })

  it('should return default values when no params are set', () => {
    const { result } = renderHook(() => useQueryParams())

    expect(result.current.pageIndex).toBe(0)
    expect(result.current.pageSize).toBe(10)
    expect(result.current.sorting).toEqual([])
    expect(result.current.search).toBe('')
  })

  it('should parse pagination params correctly', () => {
    mockSearchParams = new URLSearchParams({
      [QUERY_PARAMS.pageIndex]: '2',
      [QUERY_PARAMS.pageSize]: '20',
    })
    const { result } = renderHook(() => useQueryParams())

    expect(result.current.pageIndex).toBe(2)
    expect(result.current.pageSize).toBe(20)
  })

  it('should parse sorting params correctly', () => {
    mockSearchParams = new URLSearchParams({
      [QUERY_PARAMS.sort]: 'name,desc',
    })
    const { result } = renderHook(() => useQueryParams())

    expect(result.current.sorting).toEqual([{ id: 'name', desc: true }])
  })

  it('should update sorting params', () => {
    const { result } = renderHook(() => useQueryParams())

    act(() => {
      result.current.setSortingParams([{ id: 'age', desc: false }])
    })

    expect(push).toHaveBeenCalledWith(`/test?${new URLSearchParams({ [QUERY_PARAMS.sort]: 'age,asc' }).toString()}`)
  })

  it('should update search param', () => {
    const { result } = renderHook(() => useQueryParams())
    const searchParam = 'mockSearchParam'

    act(() => {
      result.current.setSearchParam(searchParam)
    })

    expect(push).toHaveBeenCalledWith(
      `${pathname}?${new URLSearchParams({ [QUERY_PARAMS.search]: searchParam }).toString()}`,
    )
  })

  it('should remove search param if empty string is passed', () => {
    mockSearchParams = new URLSearchParams({ [QUERY_PARAMS.search]: 'old' })
    const { result } = renderHook(() => useQueryParams())

    act(() => {
      result.current.setSearchParam('')
    })

    expect(push).toHaveBeenCalledWith(`${pathname}?`)
  })

  it('should update pagination params', () => {
    const { result } = renderHook(() => useQueryParams())

    act(() => {
      result.current.setPaginationParams({ pageIndex: 2, pageSize: 50 })
    })

    expect(push).toHaveBeenCalledWith(
      `${pathname}?${new URLSearchParams({ [QUERY_PARAMS.pageIndex]: '2', [QUERY_PARAMS.pageSize]: '50' }).toString()}`,
    )
  })

  it('should build api request params correctly', () => {
    mockSearchParams = new URLSearchParams({
      [QUERY_PARAMS.pageIndex]: '1',
      [QUERY_PARAMS.pageSize]: '25',
      [QUERY_PARAMS.sort]: 'title,asc',
      [QUERY_PARAMS.search]: 'foo',
    })
    const { result } = renderHook(() => useQueryParams())

    const params = result.current.getApiRequestParamsByUrl()
    expect(params.get(QUERY_PARAMS.pageIndex)).toBe('1')
    expect(params.get(QUERY_PARAMS.pageSize)).toBe('25')
    expect(params.get(QUERY_PARAMS.sort)).toBe('title,asc')
    expect(params.get(QUERY_PARAMS.search)).toBe('foo')
  })

  it('should reset pageIndex if it exceeds totalPages', () => {
    mockSearchParams = new URLSearchParams({
      [QUERY_PARAMS.pageIndex]: '10',
      [QUERY_PARAMS.pageSize]: '10',
    })
    const { result } = renderHook(() => useQueryParams())

    act(() => {
      result.current.setTotalPages(9)
    })

    expect(push).toHaveBeenCalledWith(
      `${pathname}?${new URLSearchParams({ [QUERY_PARAMS.pageIndex]: '8', [QUERY_PARAMS.pageSize]: '10' }).toString()}`,
    )
  })

  it('should update tabValue param and reset pagination to page 0', () => {
    const { result } = renderHook(() => useQueryParams())
    const tabValue = 'mockTab'

    act(() => {
      result.current.setTabValueParam(tabValue)
    })

    expect(push).toHaveBeenCalledWith(
      `${pathname}?${new URLSearchParams({
        [QUERY_PARAMS.tabValue]: tabValue,
        [QUERY_PARAMS.pageIndex]: '0',
      }).toString()}`,
    )
  })

  it('should remove tabValue param if empty string is passed', () => {
    mockSearchParams = new URLSearchParams({ [QUERY_PARAMS.tabValue]: 'oldTab' })
    const { result } = renderHook(() => useQueryParams())

    act(() => {
      result.current.setTabValueParam('')
    })

    expect(push).toHaveBeenCalledWith(
      `${pathname}?${new URLSearchParams({ [QUERY_PARAMS.pageIndex]: '0' }).toString()}`,
    )
  })

  it('should use replace instead of push when replace flag is set', () => {
    const { result } = renderHook(() => useQueryParams())

    act(() => {
      result.current.setTabValueParam('mockTab', true)
    })

    expect(push).not.toHaveBeenCalled()
    expect(replace).toHaveBeenCalledWith(
      `${pathname}?${new URLSearchParams({
        [QUERY_PARAMS.tabValue]: 'mockTab',
        [QUERY_PARAMS.pageIndex]: '0',
      }).toString()}`,
    )
  })
})
