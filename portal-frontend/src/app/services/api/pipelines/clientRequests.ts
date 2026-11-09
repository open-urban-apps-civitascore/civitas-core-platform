import { QueryClient, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { PipelinePayload } from '@/app/(main)/datasets/[datasetId]/data-flow/pipeline-editor/_types'
import { PipelineOutputDTO } from '@/app/(main)/datasets/[datasetId]/data-flow/pipeline-editor/_types/pipeline'
import { DATASETS_QUERY_KEY } from '@/app/services/api/datasets/clientRequests'
import { DATASINKS_QUERY_KEY } from '@/app/services/api/datasets/datasinks/clientRequests'
import { LAYERS_QUERY_KEY } from '@/app/services/api/datasets/layers/clientRequests'
import { apiRequest, type ApiServiceResponse } from '@/app/services/api/request/apiRequest'

const API_REQUEST_HEADER = 'x-api-request'
const PIPELINES_QUERY_KEY = 'pipelines'

type UpdatePipelineInput = { pipelineId: string; data: PipelinePayload; isDataLossConfirmed?: boolean }

type DeletePipelineInput = { pipelineId: string; isDataLossConfirmed?: boolean }

/** Query params that confirm the deletion of stored data; undefined when not confirmed. */
const confirmDataLossParams = (isDataLossConfirmed = false): URLSearchParams | undefined =>
  isDataLossConfirmed ? new URLSearchParams({ confirmDataLoss: 'true' }) : undefined

/** A removal can delete Data sinks, Layers and APIs (the Dataset's namedApis) besides the Pipeline. */
const invalidateRemovalQueries = (queryClient: QueryClient, datasetId: string) => {
  queryClient.invalidateQueries({ queryKey: [DATASETS_QUERY_KEY] })
  queryClient.invalidateQueries({ queryKey: [LAYERS_QUERY_KEY] })
  queryClient.invalidateQueries({ queryKey: [DATASINKS_QUERY_KEY, datasetId] })
}

/**
 * Fetches all pipelines for a dataset.
 * GET /datasets/{datasetId}/pipelines
 */
export const useGetPipelines = (datasetId: string) => {
  return useQuery<ApiServiceResponse<PipelineOutputDTO[]>>({
    queryKey: [PIPELINES_QUERY_KEY, datasetId],
    queryFn: () =>
      apiRequest<PipelineOutputDTO[]>({
        method: 'GET',
        endpoint: `/datasets/${datasetId}/pipelines`,
        headers: { [API_REQUEST_HEADER]: 'true' },
        errorMessage: 'An error occurred while loading pipelines.',
      }),
    enabled: !!datasetId,
  })
}

/**
 * Creates a new pipeline under a dataset.
 * POST /datasets/{datasetId}/pipelines
 */
export const useCreatePipeline = (datasetId: string) => {
  const queryClient = useQueryClient()

  return useMutation<ApiServiceResponse<PipelineOutputDTO>, unknown, PipelinePayload>({
    mutationFn: (data: PipelinePayload) =>
      apiRequest<PipelineOutputDTO>({
        method: 'POST',
        endpoint: `/datasets/${datasetId}/pipelines`,
        headers: { [API_REQUEST_HEADER]: 'true' },
        data,
        errorMessage: 'An error occurred while creating the pipeline.',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [PIPELINES_QUERY_KEY, datasetId] })
      queryClient.invalidateQueries({ queryKey: [DATASINKS_QUERY_KEY, datasetId] })
    },
    onError: error => {
      console.error('Failed to create pipeline:', error)
    },
  })
}

/**
 * Updates an existing pipeline (full replacement).
 * PUT /datasets/{datasetId}/pipelines/{pipelineId}
 */
export const useUpdatePipeline = (datasetId: string) => {
  const queryClient = useQueryClient()

  return useMutation<ApiServiceResponse<PipelineOutputDTO>, unknown, UpdatePipelineInput>({
    mutationFn: ({ pipelineId, data, isDataLossConfirmed }) =>
      apiRequest<PipelineOutputDTO>({
        method: 'PUT',
        endpoint: `/datasets/${datasetId}/pipelines/${pipelineId}`,
        headers: { [API_REQUEST_HEADER]: 'true' },
        params: confirmDataLossParams(isDataLossConfirmed),
        data,
        errorMessage: 'An error occurred while updating the pipeline.',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [PIPELINES_QUERY_KEY, datasetId] })
      invalidateRemovalQueries(queryClient, datasetId)
    },
    onError: error => {
      console.error('Failed to update pipeline:', error)
    },
  })
}

/**
 * Deletes a pipeline.
 * DELETE /datasets/{datasetId}/pipelines/{pipelineId}
 */
export const useDeletePipeline = (datasetId: string) => {
  const queryClient = useQueryClient()

  return useMutation<ApiServiceResponse<void>, unknown, DeletePipelineInput>({
    mutationFn: ({ pipelineId, isDataLossConfirmed }) =>
      apiRequest<void>({
        method: 'DELETE',
        endpoint: `/datasets/${datasetId}/pipelines/${pipelineId}`,
        headers: { [API_REQUEST_HEADER]: 'true' },
        params: confirmDataLossParams(isDataLossConfirmed),
        errorMessage: 'An error occurred while deleting the pipeline.',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [PIPELINES_QUERY_KEY, datasetId] })
      invalidateRemovalQueries(queryClient, datasetId)
    },
    onError: error => {
      console.error('Failed to delete pipeline:', error)
    },
  })
}
