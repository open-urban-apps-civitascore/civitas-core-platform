/**
 * Entity Service for Pipeline Editor
 *
 * Provides hooks for fetching entities used in pipeline nodes.
 * - DataSources: Uses real API via useGetDatasources()
 *
 */

import { useCallback, useMemo } from 'react'

import { useGetDatasources } from '@/app/services/api/datasources/clientRequests'
import { DATASOURCE_FILTER_PARAMS, QUERY_PARAMS } from '@/const/searchParams'
import type { Datasource } from '@/types/datasources'
import { DATAPOOL_SCOPE_TYPES, DATASOURCE_STATUS_TYPES } from '@/types/datasources'

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

const DATASOURCE_PAGE_SIZE = 2000

// ============================================================================
// DataSource Hook (Real API)
// ============================================================================

/**
 * Hook to fetch datasources for the DataSource node.
 * Uses the real datasources API.
 *
 */
export const useDataSourceEntities = (opts?: {
  isEnabled?: boolean
  datapoolId?: string | null
}): UseEntityResult<Datasource> => {
  const isEnabled = opts?.isEnabled ?? true
  const datapoolId = opts?.datapoolId

  const params = useMemo(() => {
    const p = new URLSearchParams()
    p.set(DATASOURCE_FILTER_PARAMS.dataSourceStatus, DATASOURCE_STATUS_TYPES.AVAILABLE)
    p.set(QUERY_PARAMS.pageSize, String(DATASOURCE_PAGE_SIZE))
    if (datapoolId) {
      p.set(DATASOURCE_FILTER_PARAMS.datapoolId, datapoolId)
    } else if (datapoolId === null) {
      p.set(DATASOURCE_FILTER_PARAMS.datapoolScopeType, DATAPOOL_SCOPE_TYPES.ALL)
    }
    return p
  }, [datapoolId])

  const { data: response, isLoading, isError, error } = useGetDatasources({ params, isEnabled })

  // Extract the data array from ApiServiceResponse
  const entities = useMemo(() => response?.data ?? [], [response])

  const getEntityById = useCallback(
    (id: string): Datasource | undefined => {
      return entities.find((e: Datasource) => e.id === id)
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
export const datasourceToSelectable = (ds: Datasource): SelectableEntity => ({
  id: ds.id,
  name: ds.name,
  metadata: {
    connectorType: ds.connectorType,
    status: ds.dataSourceStatus,
    description: ds.description,
  },
})
