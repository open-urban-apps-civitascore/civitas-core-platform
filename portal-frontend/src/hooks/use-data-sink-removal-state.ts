'use client'

import { useMemo } from 'react'

import { useGetDataset } from '@/app/services/api/datasets/clientRequests'
import { useGetDataSinks } from '@/app/services/api/datasets/datasinks/clientRequests'
import { useGetLayers } from '@/app/services/api/datasets/layers/clientRequests'
import type { DatasetRemovalState, PlannedApi } from '@/types/dataSinkRemoval'

/** The Data sinks, Layers and APIs of the Dataset that a removal is planned on; null until all three are loaded. */
export const useDataSinkRemovalState = (datasetId: string): DatasetRemovalState | null => {
  const { data: dataSinksData } = useGetDataSinks(datasetId, { isEnabled: !!datasetId })
  const { data: layersData } = useGetLayers(datasetId)
  const { data: datasetData } = useGetDataset({ id: datasetId, isEnabled: !!datasetId })

  const dataSinks = dataSinksData?.data
  const layers = layersData?.data
  const namedApis = datasetData?.data?.namedApis

  return useMemo(() => {
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
}
