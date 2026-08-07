/**
 * Entity Service for Pipeline Editor
 *
 * Provides hooks for fetching entities used in pipeline nodes.
 * - DataSources: Uses real API via useGetUsableDatasources()
 *
 */

import { useCallback, useMemo } from 'react'

import { useGetUsableDatasources } from '@/app/services/api/datasets/usable-datasources/clientRequests'
import type { DatasourceSummary } from '@/types/datasources'

// ============================================================================
// Types
// ============================================================================

/**
 * Generic entity interface for unified handling in EntitySelector.
 *
 */
export interface SelectableEntity {
  id: string
  name: string
  metadata?: Record<string, unknown>
}

/**
 * Return type for entity hooks.
 *
 */
export interface UseEntityResult<T extends SelectableEntity> {
  entities: T[]
  isLoading: boolean
  isError: boolean
  error: Error | null
  getEntityById: (id: string) => T | undefined
}

// ============================================================================
// DataSource Hook (Real API)
// ============================================================================

/**
 * Hook to fetch the datasources usable in this dataset's pipelines.
 *
 * Status and datapool filtering happen server-side, on the same rule that rejects a pipeline
 * referencing an unusable source — so the picker cannot offer one that saving would reject.
 */
export const useDataSourceEntities = (opts: {
  isEnabled?: boolean
  datasetId: string
}): UseEntityResult<DatasourceSummary> => {
  const {
    data: response,
    isLoading,
    isError,
    error,
  } = useGetUsableDatasources(opts.datasetId, { isEnabled: opts.isEnabled })

  // Extract the data array from ApiServiceResponse
  const entities = useMemo(() => response?.data ?? [], [response])

  const getEntityById = useCallback(
    (id: string): DatasourceSummary | undefined => {
      return entities.find((e: DatasourceSummary) => e.id === id)
    },
    [entities],
  )

  return {
    entities,
    isLoading,
    isError,
    error: error ?? null,
    getEntityById,
  }
}

// ============================================================================
// Entity Conversion Helpers
// ============================================================================

/**
 * Converts a Datasource entity to SelectableEntity format.
 *
 */
export const datasourceToSelectable = (ds: DatasourceSummary): SelectableEntity => ({
  id: ds.id,
  name: ds.name,
  metadata: {
    connectorType: ds.connectorType,
  },
})
