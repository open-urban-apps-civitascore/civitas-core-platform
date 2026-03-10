import { describe, expect, it } from 'vitest'

import {
  ApiRequestParams,
  getApiRequestParams,
  getJsonServerRequestParams,
  JsonServerRequestParams,
} from './requestParams'

describe('getApiRequestParams', () => {
  it('should return the correct request params when all URL params are set', () => {
    const mockParams: ApiRequestParams = {
      page: '1',
      size: '20',
      sort: ['fullName,ASC'],
      q: 'firstName lastName',
    }
    const result = getApiRequestParams(mockParams)
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
    const mockParams: ApiRequestParams = {}
    const result = getApiRequestParams(mockParams)
    expect(result.sort).toEqual([])
    expect(result.pageIndex).toBe(0)
    expect(result.pageSize).toBe(10)
    expect(result.search).toBe('')
    expect(result.apiParams.get('page')).toBe('0')
    expect(result.apiParams.get('size')).toBe('10')
    expect(result.apiParams.get('q')).toBeNull()
    expect(result.apiParams.getAll('sort')).toEqual([])
  })
})

describe('getJsonServerRequestParams', () => {
  it('should return the correct request params when all URL params are set', () => {
    const mockParams: JsonServerRequestParams = {
      _page: '1',
      _limit: '20',
      _sort: 'fullName',
      _order: 'ASC',
      q: 'firstName lastName',
    }
    const result = getJsonServerRequestParams(mockParams)
    expect(result.sort).toBe('fullName')
    expect(result.order).toBe('ASC')
    expect(result.pageIndex).toBe(1)
    expect(result.pageSize).toBe(20)
    expect(result.search).toBe('firstName lastName')
    expect(result.jsonServerParams.get('_page')).toBe('1')
    expect(result.jsonServerParams.get('_limit')).toBe('20')
    expect(result.jsonServerParams.get('q')).toBe('firstName lastName')
    expect(result.jsonServerParams.get('_sort')).toBe('fullName')
    expect(result.jsonServerParams.get('_order')).toBe('ASC')
  })

  it('should return the correct request params when no URL params are set', () => {
    const mockParams: JsonServerRequestParams = {}
    const result = getJsonServerRequestParams(mockParams)
    expect(result.sort).toBeUndefined()
    expect(result.order).toBeUndefined()
    expect(result.pageIndex).toBe(0)
    expect(result.pageSize).toBe(10)
    expect(result.search).toBe('')
    expect(result.jsonServerParams.get('_page')).toBe('0')
    expect(result.jsonServerParams.get('_limit')).toBe('10')
    expect(result.jsonServerParams.get('q')).toBeNull()
    expect(result.jsonServerParams.get('_sort')).toBeNull()
    expect(result.jsonServerParams.get('_order')).toBeNull()
  })
})
