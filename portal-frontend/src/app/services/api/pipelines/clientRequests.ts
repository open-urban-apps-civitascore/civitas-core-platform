import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import type {
  PipelineOutputDTO,
  PipelinePayload,
} from '@/app/(main)/datasets/[datasetId]/data/pipeline-editor/_types/pipeline'
import { apiRequest, type ApiServiceResponse } from '@/app/services/api/request/apiRequest'

const API_REQUEST_HEADER = 'x-api-request'
const PIPELINES_QUERY_KEY = 'pipelines'

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

  return useMutation<ApiServiceResponse<PipelineOutputDTO>, unknown, { pipelineId: string; data: PipelinePayload }>({
    mutationFn: ({ pipelineId, data }) =>
      apiRequest<PipelineOutputDTO>({
        method: 'PUT',
        endpoint: `/datasets/${datasetId}/pipelines/${pipelineId}`,
        headers: { [API_REQUEST_HEADER]: 'true' },
        data,
        errorMessage: 'An error occurred while updating the pipeline.',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [PIPELINES_QUERY_KEY, datasetId] })
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

  return useMutation<ApiServiceResponse<void>, unknown, string>({
    mutationFn: (pipelineId: string) =>
      apiRequest<void>({
        method: 'DELETE',
        endpoint: `/datasets/${datasetId}/pipelines/${pipelineId}`,
        headers: { [API_REQUEST_HEADER]: 'true' },
        errorMessage: 'An error occurred while deleting the pipeline.',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [PIPELINES_QUERY_KEY, datasetId] })
    },
    onError: error => {
      console.error('Failed to delete pipeline:', error)
    },
  })
}
