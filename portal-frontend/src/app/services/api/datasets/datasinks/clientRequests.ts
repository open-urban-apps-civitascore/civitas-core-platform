import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AxiosError } from 'axios'

import { apiRequest, type ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { GetListInput } from '@/types/common'
import { CreateDatasinkInput, Datasink, DeleteDatasinkInput, UpdateDatasinkInput } from '@/types/datasinks'

const key = 'datasinks'

export const useGetDatasinks = (datasetId: string, { params, isEnabled }: GetListInput = {}) =>
  useQuery<ApiServiceResponse<Datasink[]>>({
    queryKey: [key, datasetId],
    queryFn: () =>
      apiRequest<Datasink[]>({
        method: 'GET',
        endpoint: `/datasets/${datasetId}/datasinks`,
        headers: { 'x-api-request': 'true' },
        params,
        errorMessage: 'An error occurred while loading datasinks.',
      }),
    enabled: isEnabled,
  })

export const useCreateDataSink = () => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<Datasink>, AxiosError, CreateDatasinkInput>({
    mutationFn: ({ datasetId, data }) =>
      apiRequest<Datasink>({
        method: 'POST',
        endpoint: `/datasets/${datasetId}/datasinks`,
        headers: { 'x-api-request': 'true' },
        data,
        errorMessage: 'An error occurred while creating the datasink.',
      }),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: [key, variables.datasetId] })
    },
  })
}

export const useDeleteDataSink = () => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<void>, AxiosError, DeleteDatasinkInput>({
    mutationFn: ({ datasetId, datasinkId }) =>
      apiRequest<void>({
        method: 'DELETE',
        endpoint: `/datasets/${datasetId}/datasinks/${datasinkId}`,
        headers: { 'x-api-request': 'true' },
        errorMessage: 'An error occurred while deleting the datasink.',
      }),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: [key, variables.datasetId] })
    },
  })
}

export const useUpdateDataSink = () => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<Datasink>, AxiosError, UpdateDatasinkInput>({
    mutationFn: ({ datasetId, datasinkId, data }) =>
      apiRequest<Datasink>({
        method: 'PUT',
        endpoint: `/datasets/${datasetId}/datasinks/${datasinkId}`,
        headers: { 'x-api-request': 'true' },
        data,
        errorMessage: 'An error occurred while updating the datasink.',
      }),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: [key, variables.datasetId] })
    },
  })
}
