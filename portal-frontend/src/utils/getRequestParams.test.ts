import { describe, expect, it } from 'vitest'

import { getRequestParams, RequestParams } from './getRequestParams'

describe('getRequestParams', () => {
  it('should return the correct request params when all URL params are set', () => {
    const mockParams: RequestParams = {
      page: '1',
      pageSize: '20',
      sort: ['fullName,ASC'],
      q: 'firstName lastName',
    }
    const result = getRequestParams(mockParams)
    expect(result.sort).toEqual(['fullName,ASC'])
    expect(result.pageIndex).toBe(1)
    expect(result.pageSize).toBe(20)
    expect(result.search).toBe('firstName lastName')
    expect(result.apiParams.get('page')).toBe('1')
    expect(result.apiParams.get('size')).toBe('20')
    expect(result.apiParams.get('q')).toBe('firstName lastName')
    expect(result.apiParams.getAll('sort')).toEqual(['fullName,ASC'])
  })

  it('should return the correct request params when no URL params are set', () => {
    const mockParams: RequestParams = {}
    const result = getRequestParams(mockParams)
    expect(result.sort).toEqual([])
    expect(result.pageIndex).toBe(0)
    expect(result.pageSize).toBe(10)
    expect(result.search).toBe('')
    expect(result.apiParams.get('page')).toBe('0')
    expect(result.apiParams.get('size')).toBe('10')
    expect(result.apiParams.get('q')).toBe(null)
    expect(result.apiParams.getAll('sort')).toEqual([])
  })
})
