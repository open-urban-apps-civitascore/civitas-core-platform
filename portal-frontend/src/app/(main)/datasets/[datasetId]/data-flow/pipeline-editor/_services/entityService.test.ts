import { renderHook } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { useGetDatasources } from '@/app/services/api/datasources/clientRequests'
import { DATASOURCE_FILTER_PARAMS, QUERY_PARAMS } from '@/const/searchParams'
import type { Datasource } from '@/types/datasources'
import { DATAPOOL_SCOPE_TYPES, DATASOURCE_STATUS_TYPES } from '@/types/datasources'

import { datasourceToSelectable, useDataSourceEntities } from './entityService'

vi.mock('@/app/services/api/datasources/clientRequests', () => ({
  useGetDatasources: vi.fn(),
}))

const mockDatasource = (overrides?: Partial<Datasource>): Datasource => ({
  id: 'ds-1',
  name: 'Test Datasource',
  dataSourceStatus: DATASOURCE_STATUS_TYPES.AVAILABLE,
  connectorType: 'SQL',
  description: 'A test datasource',
  datapoolScope: { type: DATAPOOL_SCOPE_TYPES.ALL },
  createdAt: '2024-01-01T00:00:00Z',
  modifiedAt: '2024-01-01T00:00:00Z',
  configuration: null,
  dataStructureVersion: null,
  inUse: false,
  inUseByReleased: false,
  ...overrides,
})

const setupMock = (datasources: Datasource[] = []) => {
  vi.mocked(useGetDatasources).mockReturnValue({
    data: { data: datasources },
    isLoading: false,
    isError: false,
    error: null,
  } as unknown as ReturnType<typeof useGetDatasources>)
}

const getCalledParams = (): URLSearchParams => {
  const call = vi.mocked(useGetDatasources).mock.calls[0]
  return call[0]?.params as URLSearchParams
}

beforeEach(() => {
  vi.clearAllMocks()
  setupMock()
})

describe('useDataSourceEntities', () => {
  describe('params: always set', () => {
    it('does not filter by status, so a draft pipeline can reference a draft data source', () => {
      renderHook(() => useDataSourceEntities({ datapoolId: 'some-id' }))
      expect(getCalledParams().get(DATASOURCE_FILTER_PARAMS.dataSourceStatus)).toBeNull()
    })

    it('always sends size=2000', () => {
      renderHook(() => useDataSourceEntities({ datapoolId: 'some-id' }))
      expect(getCalledParams().get(QUERY_PARAMS.pageSize)).toBe('2000')
    })
  })

  describe('params: datapoolId filter', () => {
    it('sends datapoolId when provided', () => {
      renderHook(() => useDataSourceEntities({ datapoolId: 'pool-abc' }))
      const params = getCalledParams()
      expect(params.get(DATASOURCE_FILTER_PARAMS.datapoolId)).toBe('pool-abc')
      expect(params.has(DATASOURCE_FILTER_PARAMS.datapoolScopeType)).toBe(false)
    })

    it('sends datapoolScopeType=ALL when datapoolId is null (dataset has no datapool)', () => {
      renderHook(() => useDataSourceEntities({ datapoolId: null }))
      const params = getCalledParams()
      expect(params.get(DATASOURCE_FILTER_PARAMS.datapoolScopeType)).toBe(DATAPOOL_SCOPE_TYPES.ALL)
      expect(params.has(DATASOURCE_FILTER_PARAMS.datapoolId)).toBe(false)
    })

    it('sends no datapool filter when datapoolId is undefined (dataset not yet loaded)', () => {
      renderHook(() => useDataSourceEntities({ datapoolId: undefined }))
      const params = getCalledParams()
      expect(params.has(DATASOURCE_FILTER_PARAMS.datapoolId)).toBe(false)
      expect(params.has(DATASOURCE_FILTER_PARAMS.datapoolScopeType)).toBe(false)
    })

    it('sends no datapool filter when opts is omitted', () => {
      renderHook(() => useDataSourceEntities())
      const params = getCalledParams()
      expect(params.has(DATASOURCE_FILTER_PARAMS.datapoolId)).toBe(false)
      expect(params.has(DATASOURCE_FILTER_PARAMS.datapoolScopeType)).toBe(false)
    })
  })

  describe('isEnabled', () => {
    it('defaults to enabled when not specified', () => {
      renderHook(() => useDataSourceEntities())
      expect(vi.mocked(useGetDatasources).mock.calls[0][0]?.isEnabled).toBe(true)
    })

    it('passes isEnabled=false to the query', () => {
      renderHook(() => useDataSourceEntities({ isEnabled: false }))
      expect(vi.mocked(useGetDatasources).mock.calls[0][0]?.isEnabled).toBe(false)
    })
  })

  describe('return values', () => {
    it('returns entities from the API response', () => {
      const ds = mockDatasource()
      setupMock([ds])
      const { result } = renderHook(() => useDataSourceEntities())
      expect(result.current.entities).toHaveLength(1)
      expect(result.current.entities[0].id).toBe('ds-1')
    })

    it('returns empty array when response has no data', () => {
      vi.mocked(useGetDatasources).mockReturnValue({
        data: undefined,
        isLoading: false,
        isError: false,
        error: null,
      } as unknown as ReturnType<typeof useGetDatasources>)
      const { result } = renderHook(() => useDataSourceEntities())
      expect(result.current.entities).toEqual([])
    })

    it('returns the correct entity from getEntityById', () => {
      setupMock([mockDatasource({ id: 'ds-1' }), mockDatasource({ id: 'ds-2', name: 'Other' })])
      const { result } = renderHook(() => useDataSourceEntities())
      expect(result.current.getEntityById('ds-2')?.name).toBe('Other')
    })

    it('returns undefined from getEntityById for an unknown id', () => {
      setupMock([mockDatasource()])
      const { result } = renderHook(() => useDataSourceEntities())
      expect(result.current.getEntityById('not-here')).toBeUndefined()
    })

    it('reflects isLoading state', () => {
      vi.mocked(useGetDatasources).mockReturnValue({
        data: undefined,
        isLoading: true,
        isError: false,
        error: null,
      } as unknown as ReturnType<typeof useGetDatasources>)
      const { result } = renderHook(() => useDataSourceEntities())
      expect(result.current.isLoading).toBe(true)
    })

    it('reflects isError state and exposes the error', () => {
      const err = new Error('Network failure')
      vi.mocked(useGetDatasources).mockReturnValue({
        data: undefined,
        isLoading: false,
        isError: true,
        error: err,
      } as unknown as ReturnType<typeof useGetDatasources>)
      const { result } = renderHook(() => useDataSourceEntities())
      expect(result.current.isError).toBe(true)
      expect(result.current.error).toBe(err)
    })
  })
})

describe('datasourceToSelectable', () => {
  it('maps id and name', () => {
    const ds = mockDatasource({ id: 'ds-x', name: 'My DS' })
    const result = datasourceToSelectable(ds)
    expect(result.id).toBe('ds-x')
    expect(result.name).toBe('My DS')
  })

  it('includes connectorType, status, and description in metadata', () => {
    const ds = mockDatasource({ connectorType: 'MQTT', description: 'desc' })
    const result = datasourceToSelectable(ds)
    expect(result.metadata?.connectorType).toBe('MQTT')
    expect(result.metadata?.status).toBe(DATASOURCE_STATUS_TYPES.AVAILABLE)
    expect(result.metadata?.description).toBe('desc')
  })

  it('sets description to null when not provided', () => {
    const ds = mockDatasource({ description: null })
    const result = datasourceToSelectable(ds)
    expect(result.metadata?.description).toBeNull()
  })
})
