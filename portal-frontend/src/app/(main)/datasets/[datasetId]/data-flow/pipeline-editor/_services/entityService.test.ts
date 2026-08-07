import { renderHook } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { useGetUsableDatasources } from '@/app/services/api/datasets/usable-datasources/clientRequests'
import type { DatasourceSummary } from '@/types/datasources'

import { useDataSourceEntities } from './entityService'

vi.mock('@/app/services/api/datasets/usable-datasources/clientRequests', () => ({
  useGetUsableDatasources: vi.fn(),
}))

const DATASET_ID = 'dataset-1'

const mockDatasource = (overrides?: Partial<DatasourceSummary>): DatasourceSummary => ({
  id: 'ds-1',
  name: 'Test Datasource',
  description: 'A test datasource',
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
  it('resolves a datasource by id', () => {
    setupMock([mockDatasource({ id: 'ds-1' }), mockDatasource({ id: 'ds-2', name: 'Other' })])

    const { result } = renderHook(() => useDataSourceEntities({ datasetId: DATASET_ID }))

    expect(result.current.getEntityById('ds-2')?.name).toBe('Other')
  })

  it('resolves an id the dataset may not use to undefined', () => {
    // A pipeline can reference a source that has since left the datapool; the panel needs to tell
    // that apart from a resolved one rather than reading a stale name.
    setupMock([mockDatasource()])

    const { result } = renderHook(() => useDataSourceEntities({ datasetId: DATASET_ID }))

    expect(result.current.getEntityById('not-here')).toBeUndefined()
  })

  it('yields no entities before the response arrives', () => {
    vi.mocked(useGetUsableDatasources).mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      error: null,
    } as unknown as ReturnType<typeof useGetUsableDatasources>)

    const { result } = renderHook(() => useDataSourceEntities({ datasetId: DATASET_ID }))

    expect(result.current.entities).toEqual([])
  })
})
