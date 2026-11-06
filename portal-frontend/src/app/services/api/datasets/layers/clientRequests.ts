import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AxiosError } from 'axios'

import { DATASINKS_QUERY_KEY } from '@/app/services/api/datasets/datasinks/clientRequests'
import { apiRequest, apiRequestAllPages, ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { CreateLayerInput, Layer, UpdateLayerInput } from '@/types/layers'

export const LAYERS_QUERY_KEY = 'layers'

/** Loads every Layer of the Dataset. */
export const useGetLayers = (datasetId: string) =>
  useQuery<ApiServiceResponse<Layer[]>, AxiosError>({
    queryKey: [LAYERS_QUERY_KEY, datasetId],
    queryFn: () =>
      apiRequestAllPages<Layer>({
        endpoint: `/datasets/${datasetId}/layers`,
        headers: { 'x-api-request': 'true' },
        errorMessage: 'An error occurred while loading layers.',
      }),
  })

export const useCreateLayer = () => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<Layer>, AxiosError, CreateLayerInput>({
    mutationFn: ({ datasetId, data }) =>
      apiRequest<Layer>({
        method: 'POST',
        endpoint: `/datasets/${datasetId}/layers`,
        headers: { 'x-api-request': 'true' },
        data,
        errorMessage: 'An error occurred while creating the layer.',
      }),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: [LAYERS_QUERY_KEY] })
      queryClient.invalidateQueries({ queryKey: [DATASINKS_QUERY_KEY, variables.datasetId] })
    },
  })
}

export const useUpdateLayer = () => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<Layer>, AxiosError, UpdateLayerInput>({
    mutationFn: ({ datasetId, layerId, data }) =>
      apiRequest<Layer>({
        method: 'PUT',
        endpoint: `/datasets/${datasetId}/layers/${layerId}`,
        headers: { 'x-api-request': 'true' },
        data,
        errorMessage: 'An error occurred while updating the layer.',
      }),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: [LAYERS_QUERY_KEY] })
      queryClient.invalidateQueries({ queryKey: [DATASINKS_QUERY_KEY, variables.datasetId] })
    },
  })
}

export const useDeleteLayer = () => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<void>, AxiosError, { datasetId: string; layerId: string }>({
    mutationFn: ({ datasetId, layerId }) =>
      apiRequest<void>({
        method: 'DELETE',
        endpoint: `/datasets/${datasetId}/layers/${layerId}`,
        headers: { 'x-api-request': 'true' },
        errorMessage: 'An error occurred while deleting the layer.',
      }),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: [LAYERS_QUERY_KEY] })
      queryClient.invalidateQueries({ queryKey: [DATASINKS_QUERY_KEY, variables.datasetId] })
    },
  })
}
