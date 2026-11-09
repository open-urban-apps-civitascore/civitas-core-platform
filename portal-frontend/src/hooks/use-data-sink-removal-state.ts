'use client'

import { useMemo } from 'react'

import { useGetDataset } from '@/app/services/api/datasets/clientRequests'
import { useGetDataSinks } from '@/app/services/api/datasets/datasinks/clientRequests'
import { useGetLayers } from '@/app/services/api/datasets/layers/clientRequests'
import type { DatasetRemovalState, PlannedApi } from '@/types/dataSinkRemoval'

export interface DataSinkRemovalStateResult {
  /** The Data sinks, Layers and APIs of the Dataset that a removal is planned on; null until all three are loaded. */
  removalState: DatasetRemovalState | null
  /** Whether one of the three requests failed, so that the removal state stays null. */
  isLoadingError: boolean
}

export const useDataSinkRemovalState = (datasetId: string): DataSinkRemovalStateResult => {
  const dataSinksQuery = useGetDataSinks(datasetId, { isEnabled: !!datasetId })
  const layersQuery = useGetLayers(datasetId)
  const datasetQuery = useGetDataset({ id: datasetId, isEnabled: !!datasetId })
  const dataSinksData = dataSinksQuery.data
  const layersData = layersQuery.data
  const datasetData = datasetQuery.data

  const dataSinks = dataSinksData?.data
  const layers = layersData?.data
  const namedApis = datasetData?.data?.namedApis

  const removalState = useMemo(() => {
    if (!dataSinks || !layers || !datasetData?.data) return null
    const apis: PlannedApi[] = []
    for (const api of namedApis ?? []) {
      if (api.id) apis.push({ id: api.id, slug: api.slug, name: api.name, standard: api.standard })
    }
    return {
      dataSinks: dataSinks.map(dataSink => ({
        id: dataSink.id,
        dataSinkType: dataSink.dataSinkType,
        provisioned: dataSink.provisioned ?? false,
        pipelineIds: dataSink.pipelineIds,
      })),
      layers: layers.map(({ id, dataSinkId, namedApiId, layerName, title }) => ({
        id,
        dataSinkId,
        namedApiId,
        layerName,
        title,
      })),
      apis,
    }
  }, [dataSinks, layers, namedApis, datasetData?.data])

  const isLoadingError = dataSinksQuery.isLoadingError || layersQuery.isLoadingError || datasetQuery.isLoadingError
  return { removalState, isLoadingError }
}
