import { renderHook } from '@testing-library/react'
import { useRouter, useSearchParams } from 'next/navigation'

import { useTableSearchParams } from './use-table-search-params'

vi.mock('next/navigation', () => ({
  useRouter: vi.fn(),
  useSearchParams: vi.fn(),
}))

const mockPush = vi.fn()
const mockSearchParams = (init = '') => vi.mocked(useSearchParams).mockReturnValue(new URLSearchParams(init) as never)

beforeEach(() => {
  mockPush.mockClear()
  vi.mocked(useRouter).mockReturnValue({ push: mockPush } as never)
})

describe('handleSortingChange', () => {
  it('adds sort params', () => {
    mockSearchParams('')

    const { result } = renderHook(() => useTableSearchParams())
    const newSort = [{ id: 'name', desc: false }]
    result.current.handleSortingChange(newSort)
    expect(mockPush).toHaveBeenCalledWith('?sort=name%2CASC')
  })

  it('adds multiple new sort params to existing one', () => {
    mockSearchParams('sort=name,DESC')

    const { result } = renderHook(() => useTableSearchParams())
    const newSort = [
      { id: 'age', desc: true },
      { id: 'address', desc: false },
    ]
    result.current.handleSortingChange(newSort)
    expect(mockPush).toHaveBeenCalledWith('?sort=name%2CDESC&sort=age%2CDESC&sort=address%2CASC')
  })

  it('updates the changed sort params', () => {
    mockSearchParams('sort=name,DESC')

    const { result } = renderHook(() => useTableSearchParams())
    const newSort = [{ id: 'name', desc: false }]
    result.current.handleSortingChange(newSort)
    expect(mockPush).toHaveBeenCalledWith('?sort=name%2CASC')
  })

  it('keeps the existing unrelated sort params', () => {
    mockSearchParams('sort=name,DESC&sort=age,ASC')

    const { result } = renderHook(() => useTableSearchParams())
    const newSort = [{ id: 'name', desc: false }]
    result.current.handleSortingChange(newSort)
    expect(mockPush).toHaveBeenCalledWith('?sort=name%2CASC&sort=age%2CASC')
  })
})

describe('handlePaginationChange', () => {
  it('sets page and pageSize params', () => {
    mockSearchParams('page=1&pageSize=10')

    const { result } = renderHook(() => useTableSearchParams())
    const newPagination = {
      pageIndex: 2,
      pageSize: 5,
    }
    result.current.handlePaginationChange(newPagination)
    expect(mockPush).toHaveBeenCalledWith('?page=2&pageSize=5')
  })

  it('sets page and pageSize params when none exist yet', () => {
    mockSearchParams('')

    const { result } = renderHook(() => useTableSearchParams())
    result.current.handlePaginationChange({ pageIndex: 0, pageSize: 10 })
    expect(mockPush).toHaveBeenCalledWith('?page=0&pageSize=10')
  })

  it('preserves other params like sort and q', () => {
    mockSearchParams('sort=name,DESC&q=test')

    const { result } = renderHook(() => useTableSearchParams())
    result.current.handlePaginationChange({ pageIndex: 1, pageSize: 20 })
    expect(mockPush).toHaveBeenCalledWith('?sort=name%2CDESC&q=test&page=1&pageSize=20')
  })
})

describe('handleSearchChange', () => {
  it('sets q param and sets page to 0', () => {
    mockSearchParams('')

    const { result } = renderHook(() => useTableSearchParams())
    result.current.handleSearchChange('new search')
    expect(mockPush).toHaveBeenCalledWith('?q=new+search&page=0')
  })

  it('changes q param when old search param exists and resets page to 0', () => {
    mockSearchParams('page=2&q=old+search')

    const { result } = renderHook(() => useTableSearchParams())
    result.current.handleSearchChange('new search')
    expect(mockPush).toHaveBeenCalledWith('?page=0&q=new+search')
  })

  it('removes q param when search string gets deleted and resets page to 0', () => {
    mockSearchParams('page=2&q=old+search')

    const { result } = renderHook(() => useTableSearchParams())
    result.current.handleSearchChange('')
    expect(mockPush).toHaveBeenCalledWith('?page=0')
  })

  it('keeps other parameters', () => {
    mockSearchParams('sort=name,DESC')
    const { result } = renderHook(() => useTableSearchParams())
    result.current.handleSearchChange('new search')
    expect(mockPush).toHaveBeenCalledWith('?sort=name%2CDESC&q=new+search&page=0')
  })
})
