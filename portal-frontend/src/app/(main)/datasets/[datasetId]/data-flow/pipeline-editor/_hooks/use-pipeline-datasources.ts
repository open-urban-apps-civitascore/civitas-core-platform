'use client'

/**
 * One request serves both the inspector selector and the canvas node names, so selecting a source
 * and rendering its label cannot disagree.
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
  /**
   * Resolves a datasource name from its id. Undefined while the list is unavailable, so a failed
   * request does not read as a canvas full of anonymous sources; the fallback label marks an id the
   * loaded list does not contain.
   */
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
      if (isLoading || isError) return undefined
      return t('dataSourcePanel.anonymousName')
    },
    [getEntityById, isLoading, isError, t],
  )

  return {
    entities,
    isLoading,
    isError,
    getEntityById,
    getName,
  }
}
