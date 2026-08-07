import { renderHook } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { useGetUsableDatasources } from '@/app/services/api/datasets/usable-datasources/clientRequests'
import type { DatasourceSummary } from '@/types/datasources'

import { datasourceToSelectable, useDataSourceEntities } from './entityService'

vi.mock('@/app/services/api/datasets/usable-datasources/clientRequests', () => ({
  useGetUsableDatasources: vi.fn(),
}))

const DATASET_ID = 'dataset-1'

const mockDatasource = (overrides?: Partial<DatasourceSummary>): DatasourceSummary => ({
  id: 'ds-1',
  name: 'Test Datasource',
  connectorType: 'SQL',
  ...overrides,
})

const setupMock = (datasources: DatasourceSummary[] = []) => {
  vi.mocked(useGetUsableDatasources).mockReturnValue({
    data: { data: datasources },
    isLoading: false,
    isError: false,
    error: null,
  } as unknown as ReturnType<typeof useGetUsableDatasources>)
}

beforeEach(() => {
  vi.clearAllMocks()
  setupMock()
})

describe('useDataSourceEntities', () => {
  describe('request', () => {
    it('queries the usable datasources of the given dataset', () => {
      renderHook(() => useDataSourceEntities({ datasetId: DATASET_ID }))
      expect(vi.mocked(useGetUsableDatasources).mock.calls[0][0]).toBe(DATASET_ID)
    })
  })

  describe('isEnabled', () => {
    it('passes isEnabled=false to the query', () => {
      renderHook(() => useDataSourceEntities({ datasetId: DATASET_ID, isEnabled: false }))
      expect(vi.mocked(useGetUsableDatasources).mock.calls[0][1]?.isEnabled).toBe(false)
    })
  })

  describe('return values', () => {
    it('returns entities from the API response', () => {
      const ds = mockDatasource()
      setupMock([ds])
      const { result } = renderHook(() => useDataSourceEntities({ datasetId: DATASET_ID }))
      expect(result.current.entities).toHaveLength(1)
      expect(result.current.entities[0].id).toBe('ds-1')
    })

    it('returns empty array when response has no data', () => {
      vi.mocked(useGetUsableDatasources).mockReturnValue({
        data: undefined,
        isLoading: false,
        isError: false,
        error: null,
      } as unknown as ReturnType<typeof useGetUsableDatasources>)
      const { result } = renderHook(() => useDataSourceEntities({ datasetId: DATASET_ID }))
      expect(result.current.entities).toEqual([])
    })

    it('returns the correct entity from getEntityById', () => {
      setupMock([mockDatasource({ id: 'ds-1' }), mockDatasource({ id: 'ds-2', name: 'Other' })])
      const { result } = renderHook(() => useDataSourceEntities({ datasetId: DATASET_ID }))
      expect(result.current.getEntityById('ds-2')?.name).toBe('Other')
    })

    it('returns undefined from getEntityById for an unknown id', () => {
      setupMock([mockDatasource()])
      const { result } = renderHook(() => useDataSourceEntities({ datasetId: DATASET_ID }))
      expect(result.current.getEntityById('not-here')).toBeUndefined()
    })

    it('reflects isLoading state', () => {
      vi.mocked(useGetUsableDatasources).mockReturnValue({
        data: undefined,
        isLoading: true,
        isError: false,
        error: null,
      } as unknown as ReturnType<typeof useGetUsableDatasources>)
      const { result } = renderHook(() => useDataSourceEntities({ datasetId: DATASET_ID }))
      expect(result.current.isLoading).toBe(true)
    })

    it('reflects isError state and exposes the error', () => {
      const err = new Error('Network failure')
      vi.mocked(useGetUsableDatasources).mockReturnValue({
        data: undefined,
        isLoading: false,
        isError: true,
        error: err,
      } as unknown as ReturnType<typeof useGetUsableDatasources>)
      const { result } = renderHook(() => useDataSourceEntities({ datasetId: DATASET_ID }))
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

  it('carries the connector type into metadata', () => {
    // The connector drives the node's payload form and the schedule rules, so losing it here
    // silently disables pipeline validation.
    const ds = mockDatasource({ connectorType: 'MQTT' })
    const result = datasourceToSelectable(ds)
    expect(result.metadata?.connectorType).toBe('MQTT')
  })

  it('keeps connectorType null when the source has none', () => {
    const ds = mockDatasource({ connectorType: null })
    const result = datasourceToSelectable(ds)
    expect(result.metadata?.connectorType).toBeNull()
  })
})
