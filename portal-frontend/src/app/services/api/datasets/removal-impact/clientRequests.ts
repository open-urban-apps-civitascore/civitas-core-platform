import { QueryClient } from '@tanstack/react-query'

import { DATASETS_QUERY_KEY } from '@/app/services/api/datasets/clientRequests'
import { DATASINKS_QUERY_KEY } from '@/app/services/api/datasets/datasinks/clientRequests'
import { LAYERS_QUERY_KEY } from '@/app/services/api/datasets/layers/clientRequests'
import { apiRequest } from '@/app/services/api/request/apiRequest'
import { DataSinkRemovalImpact, RemovalImpactQuery } from '@/types/removalImpact'

const appendAll = (params: URLSearchParams, name: string, values: string[]) => {
  for (const value of values) params.append(name, value)
}

/**
 * GET /datasets/{datasetId}/removal-impact
 * Read-only: what the given Pipeline deletes and removed Pipeline-Data sink links would take with them.
 */
export const getRemovalImpact = async (
  datasetId: string,
  { deletedPipelineIds = [], removedLinks = [] }: RemovalImpactQuery,
): Promise<DataSinkRemovalImpact> => {
  const params = new URLSearchParams()
  appendAll(params, 'deletedPipelineIds', deletedPipelineIds)
  appendAll(
    params,
    'removedLinks',
    removedLinks.map(link => `${link.pipelineId}:${link.dataSinkId}`),
  )
  const response = await apiRequest<DataSinkRemovalImpact>({
    method: 'GET',
    endpoint: `/datasets/${datasetId}/removal-impact`,
    headers: { 'x-api-request': 'true' },
    params,
    errorMessage: 'An error occurred while loading what the removal deletes.',
  })
  return response.data
}

/** Query params that confirm a removal; undefined when nothing is confirmed. */
export const confirmedRemovalParams = (confirmedRemovalIds: string[] = []): URLSearchParams | undefined => {
  if (confirmedRemovalIds.length === 0) return undefined
  const params = new URLSearchParams()
  appendAll(params, 'confirmedRemovalIds', confirmedRemovalIds)
  return params
}

/** A removal can delete Data sinks, Layers and APIs (the Dataset's namedApis) besides the Pipeline. */
export const invalidateRemovalQueries = (queryClient: QueryClient, datasetId: string) => {
  queryClient.invalidateQueries({ queryKey: [DATASETS_QUERY_KEY] })
  queryClient.invalidateQueries({ queryKey: [LAYERS_QUERY_KEY] })
  queryClient.invalidateQueries({ queryKey: [DATASINKS_QUERY_KEY, datasetId] })
}
