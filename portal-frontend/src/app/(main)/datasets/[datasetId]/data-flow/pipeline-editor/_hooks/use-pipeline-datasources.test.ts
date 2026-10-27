import { renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetDataset } from '@/app/services/api/datasets/clientRequests'

import { useDataSourceEntities } from '../_services/entityService'
import { usePipelineDatasources } from './use-pipeline-datasources'

vi.mock('next/navigation', () => ({
  useParams: () => ({ datasetId: 'dataset-1' }),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-permissions', () => ({
  usePermissions: () => ({ hasPermission: () => true }),
}))

vi.mock('@/app/services/api/datasets/clientRequests', () => ({
  useGetDataset: vi.fn(),
}))

vi.mock('../_services/entityService', () => ({
  useDataSourceEntities: vi.fn(),
}))

const mockDataset = ({ isFetching = false } = {}) => {
  vi.mocked(useGetDataset).mockReturnValue({
    data: { data: { id: 'dataset-1', datapool: { id: 'pool-1' } } },
    isLoading: false,
    isFetching,
    isError: false,
  } as unknown as ReturnType<typeof useGetDataset>)
}

const mockDatasources = ({ isFetching = false } = {}) => {
  vi.mocked(useDataSourceEntities).mockReturnValue({
    entities: [],
    isLoading: false,
    isFetching,
    isError: false,
    error: null,
    getEntityById: () => undefined,
  })
}

beforeEach(() => {
  vi.clearAllMocks()
  mockDataset()
  mockDatasources()
})

describe('usePipelineDatasources', () => {
  describe('isFetching', () => {
    it('is false when no query is fetching', () => {
      const { result } = renderHook(() => usePipelineDatasources())
      expect(result.current.isFetching).toBe(false)
    })

    it('is true while the dataset is fetched', () => {
      mockDataset({ isFetching: true })
      const { result } = renderHook(() => usePipelineDatasources())
      expect(result.current.isFetching).toBe(true)
    })

    it('is true while the Data source list is fetched', () => {
      mockDatasources({ isFetching: true })
      const { result } = renderHook(() => usePipelineDatasources())
      expect(result.current.isFetching).toBe(true)
    })
  })
})
