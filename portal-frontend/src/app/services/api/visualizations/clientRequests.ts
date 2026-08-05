import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AxiosError } from 'axios'

import { apiRequest, ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { useDataQuery } from '@/hooks/use-data-query'
import {
  CreateVisualizationInput,
  DeleteVisualizationInput,
  UpdateVisualizationInput,
  VisualizationApiResponse,
} from '@/types/visualizations'

const key = 'visualizations'

export const useGetVisualizations = (datasetId: string, isEnabled = true) =>
  useDataQuery<VisualizationApiResponse[]>({
    key: `datasets/${datasetId}/visualizations`,
    queryKey: key,
    isEnabled,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while loading visualizations.',
  })

export const useCreateVisualization = () => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<VisualizationApiResponse>, AxiosError, CreateVisualizationInput>({
    mutationFn: ({ datasetId, visualization }) =>
      apiRequest<VisualizationApiResponse>({
        method: 'POST',
        endpoint: `/datasets/${datasetId}/visualizations`,
        headers: { 'x-api-request': 'true' },
        data: visualization,
        errorMessage: 'An error occurred while creating the visualization.',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [key] })
    },
    onError: error => {
      console.error('Error creating visualization:', error.message)
    },
  })
}

export const useUpdateVisualization = () => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<VisualizationApiResponse>, AxiosError, UpdateVisualizationInput>({
    mutationFn: ({ datasetId, visualizationId, visualization }) =>
      apiRequest<VisualizationApiResponse>({
        method: 'PUT',
        endpoint: `/datasets/${datasetId}/visualizations/${visualizationId}`,
        headers: { 'x-api-request': 'true' },
        data: visualization,
        errorMessage: 'An error occurred while updating the visualization.',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [key] })
    },
    onError: error => {
      console.error('Error updating visualization:', error.message)
    },
  })
}

export const useDeleteVisualization = () => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<VisualizationApiResponse>, AxiosError, DeleteVisualizationInput>({
    mutationFn: ({ datasetId, visualizationId }) =>
      apiRequest<VisualizationApiResponse>({
        method: 'DELETE',
        endpoint: `/datasets/${datasetId}/visualizations/${visualizationId}`,
        headers: { 'x-api-request': 'true' },
        errorMessage: 'An error occurred while deleting the visualization.',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [key] })
    },
    onError: error => {
      console.error('Error deleting visualization:', error.message)
    },
  })
}
