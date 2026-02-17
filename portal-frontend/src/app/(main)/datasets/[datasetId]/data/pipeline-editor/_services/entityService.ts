/**
 * Entity Service for Pipeline Editor
 *
 * Provides hooks for fetching entities used in pipeline nodes.
 * - DataSources: Uses real API via useGetDatasources()
 *
 */

import { useCallback, useMemo } from 'react'

import { useGetDatasources } from '@/app/services/api/datasources/clientRequests'
import type { Datasource } from '@/types/datasources'

// ============================================================================
// Types
// ============================================================================

/**
 * Generic entity interface for unified handling in EntitySelector.
 *
 */
export interface SelectableEntity {
  id: string | number
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
  getEntityById: (id: string | number) => T | undefined
}

// ============================================================================
// DataSource Hook (Real API)
// ============================================================================

/**
 * Hook to fetch datasources for the DataSource node.
 * Uses the real datasources API.
 *
 */
export const useDataSourceEntities = (): UseEntityResult<Datasource> => {
  const { data: response, isLoading, isError, error } = useGetDatasources({ isEnabled: true })

  // Extract the data array from ApiServiceResponse
  const entities = useMemo(() => response?.data ?? [], [response])

  const getEntityById = useCallback(
    (id: string | number): Datasource | undefined => {
      const numericId = typeof id === 'string' ? parseInt(id, 10) : id
      return entities.find((e: Datasource) => e.id === numericId)
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
    connector: ds.connector,
    connection: ds.connection,
    status: ds.status,
    tags: ds.tags,
    description: ds.description,
  },
})
