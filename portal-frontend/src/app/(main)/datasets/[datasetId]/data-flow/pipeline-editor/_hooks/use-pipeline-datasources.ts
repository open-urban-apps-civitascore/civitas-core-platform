'use client'

/**
 * usePipelineDatasources Hook
 *
 * Source for datasource data in the pipeline editor. Fetches the datasources
 * available for the current dataset once (cached by react-query) and serves both the
 * DataSource inspector selector and the canvas node name resolution from that same request.
 *
 * `getName` resolves an id against that list, falling back to the anonymous label for ids
 * absent from it (a datasource no longer available or outside the datapool).
 */

import { useParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useCallback } from 'react'

import type { DatasourceSummary } from '@/types/datasources'

import { useDataSourceEntities } from '../_services/entityService'

export interface PipelineDatasources {
  entities: DatasourceSummary[]
  isLoading: boolean
  isError: boolean
  getEntityById: (id: string) => DatasourceSummary | undefined
  /** Resolves a datasource name from its id, or the anonymous fallback when it is not accessible. */
  getName: (entityId: string | undefined) => string | undefined
}

export const usePipelineDatasources = (): PipelineDatasources => {
  const t = useTranslations('pipelineEditor')

  const { datasetId } = useParams<{ datasetId: string }>()

  const { entities, isLoading, isError, getEntityById } = useDataSourceEntities({ datasetId })

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
    isError,
    getEntityById,
    getName,
  }
}
