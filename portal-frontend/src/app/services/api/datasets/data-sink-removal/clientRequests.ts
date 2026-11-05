import { QueryClient } from '@tanstack/react-query'

import { DATASETS_QUERY_KEY } from '@/app/services/api/datasets/clientRequests'
import { DATASINKS_QUERY_KEY } from '@/app/services/api/datasets/datasinks/clientRequests'
import { LAYERS_QUERY_KEY } from '@/app/services/api/datasets/layers/clientRequests'

/** Query params that confirm the removal of stored data, Layers and APIs; undefined when not confirmed. */
export const confirmDataLossParams = (isDataLossConfirmed = false): URLSearchParams | undefined =>
  isDataLossConfirmed ? new URLSearchParams({ confirmDataLoss: 'true' }) : undefined

/** A removal can delete Data sinks, Layers and APIs (the Dataset's namedApis) besides the Pipeline. */
export const invalidateRemovalQueries = (queryClient: QueryClient, datasetId: string) => {
  queryClient.invalidateQueries({ queryKey: [DATASETS_QUERY_KEY] })
  queryClient.invalidateQueries({ queryKey: [LAYERS_QUERY_KEY] })
  queryClient.invalidateQueries({ queryKey: [DATASINKS_QUERY_KEY, datasetId] })
}
