'use client'

/**
 * usePipelineDatasources Hook
 *
 * Source for datasource data in the pipeline editor. Fetches the datasources
 * available for the current dataset once (cached by react-query) and serves both the
 * DataSource inspector selector and the canvas node name resolution from that same request.
 *
 * `getName` resolves an id against that list, falling back to the anonymous label for ids
 * absent from it (no DATASOURCE_READ, or a datasource no longer available / outside the datapool).
 */

import { useParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useCallback } from 'react'

import { useGetDataset } from '@/app/services/api/datasets/clientRequests'
import { usePermissions } from '@/hooks/use-permissions'
import { PERMISSION_NAMES } from '@/types/currentUser'
import type { Datasource } from '@/types/datasources'

import { useDataSourceEntities } from '../_services/entityService'

export interface PipelineDatasources {
  entities: Datasource[]
  isLoading: boolean
  isError: boolean
  canReadDatasources: boolean
  getEntityById: (id: string) => Datasource | undefined
  /** Resolves a datasource name from its id, or the anonymous fallback when it is not accessible. */
  getName: (entityId: string | undefined) => string | undefined
}

export const usePipelineDatasources = (): PipelineDatasources => {
  const t = useTranslations('pipelineEditor')
  const { hasPermission } = usePermissions()
  const canReadDatasources = hasPermission(PERMISSION_NAMES.DATASOURCE_READ)

  const { datasetId } = useParams<{ datasetId: string }>()
  const {
    data: datasetResponse,
    isLoading: isDatasetLoading,
    isError: isDatasetError,
  } = useGetDataset({ id: datasetId })
  const datapoolId = datasetResponse?.data?.datapool?.id ?? null

  const {
    entities,
    isLoading: isDatasourcesLoading,
    isError: isDatasourcesError,
    getEntityById,
  } = useDataSourceEntities({
    isEnabled: canReadDatasources && !isDatasetLoading && !isDatasetError,
    datapoolId,
  })

  const isLoading = isDatasetLoading || isDatasourcesLoading

  const getName = useCallback(
    (entityId: string | undefined): string | undefined => {
      if (entityId === undefined) return undefined
      const entity = getEntityById(entityId)
      if (entity) return entity.name
      // Avoid flashing the anonymous label while the list is still being fetched.
      if (isLoading) return undefined
      return t('dataSourcePanel.anonymousName')
    },
    [getEntityById, isLoading, t],
  )

  return {
    entities,
    isLoading,
    isError: isDatasetError || isDatasourcesError,
    canReadDatasources,
    getEntityById,
    getName,
  }
}
