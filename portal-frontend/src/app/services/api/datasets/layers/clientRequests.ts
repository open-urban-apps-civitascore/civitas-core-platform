import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AxiosError } from 'axios'

import { apiRequest, ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { CreateLayerInput, Layer, UpdateLayerInput } from '@/types/namedApis'

const key = 'layers'

export const useGetLayers = (datasetId: string) =>
  useQuery<ApiServiceResponse<Layer[]>, AxiosError>({
    queryKey: [key, datasetId],
    queryFn: () =>
      apiRequest<Layer[]>({
        method: 'GET',
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
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [key] })
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
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [key] })
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
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [key] })
    },
  })
}
