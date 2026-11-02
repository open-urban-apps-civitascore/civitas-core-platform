import { QueryClient } from '@tanstack/react-query'

import { DATASETS_QUERY_KEY } from '@/app/services/api/datasets/clientRequests'
import { DATASINKS_QUERY_KEY } from '@/app/services/api/datasets/datasinks/clientRequests'
import { LAYERS_QUERY_KEY } from '@/app/services/api/datasets/layers/clientRequests'
import { apiRequest } from '@/app/services/api/request/apiRequest'
import { QUERY_PARAMS } from '@/const/searchParams'
import { Dataset } from '@/types/datasets'
import { DataSink } from '@/types/datasinks'
import { Layer } from '@/types/layers'
import { DatasetResources } from '@/utils/dataSinkRemoval'

const API_REQUEST_HEADER = { 'x-api-request': 'true' }

/** The backend's spring.data.web.pageable.max-page-size. */
const MAX_PAGE_SIZE = 200

/** Reads the current Data sinks, Layers and APIs uncached, so a removal is judged on the server's state. */
export const getDatasetResources = async (datasetId: string): Promise<DatasetResources> => {
  const params = new URLSearchParams({ [QUERY_PARAMS.pageSize]: String(MAX_PAGE_SIZE) })
  const [dataSinks, layers, dataset] = await Promise.all([
    apiRequest<DataSink[]>({
      method: 'GET',
      endpoint: `/datasets/${datasetId}/datasinks`,
      headers: API_REQUEST_HEADER,
      params,
      errorMessage: 'An error occurred while loading data sinks.',
    }),
    apiRequest<Layer[]>({
      method: 'GET',
      endpoint: `/datasets/${datasetId}/layers`,
      headers: API_REQUEST_HEADER,
      params,
      errorMessage: 'An error occurred while loading layers.',
    }),
    apiRequest<Dataset>({
      method: 'GET',
      endpoint: `/datasets/${datasetId}`,
      headers: API_REQUEST_HEADER,
      errorMessage: 'An error occurred while loading dataset.',
    }),
  ])
  return { dataSinks: dataSinks.data, layers: layers.data, apis: dataset.data.namedApis ?? [] }
}

/** Query params that confirm the removal of stored data, Layers and APIs; undefined when not confirmed. */
export const confirmDataLossParams = (isDataLossConfirmed = false): URLSearchParams | undefined =>
  isDataLossConfirmed ? new URLSearchParams({ confirmDataLoss: 'true' }) : undefined

/** A removal can delete Data sinks, Layers and APIs (the Dataset's namedApis) besides the Pipeline. */
export const invalidateRemovalQueries = (queryClient: QueryClient, datasetId: string) => {
  queryClient.invalidateQueries({ queryKey: [DATASETS_QUERY_KEY] })
  queryClient.invalidateQueries({ queryKey: [LAYERS_QUERY_KEY] })
  queryClient.invalidateQueries({ queryKey: [DATASINKS_QUERY_KEY, datasetId] })
}
