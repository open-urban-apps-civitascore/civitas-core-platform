import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AxiosError } from 'axios'

import { apiRequest, type ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { GetListInput } from '@/types/common'
import { CreateDataSinkInput, DataSink, DeleteDataSinkInput, UpdateDataSinkInput } from '@/types/datasinks'

const key = 'datasinks'

export const useGetDataSinks = (datasetId: string, { params, isEnabled }: GetListInput = {}) =>
  useQuery<ApiServiceResponse<DataSink[]>>({
    queryKey: [key, datasetId],
    queryFn: () =>
      apiRequest<DataSink[]>({
        method: 'GET',
        endpoint: `/datasets/${datasetId}/datasinks`,
        headers: { 'x-api-request': 'true' },
        params,
        errorMessage: 'An error occurred while loading data sinks.',
      }),
    enabled: isEnabled,
  })

export const useCreateDataSink = () => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<DataSink>, AxiosError, CreateDataSinkInput>({
    mutationFn: ({ datasetId, data }) =>
      apiRequest<DataSink>({
        method: 'POST',
        endpoint: `/datasets/${datasetId}/datasinks`,
        headers: { 'x-api-request': 'true' },
        data,
        errorMessage: 'An error occurred while creating the data sink.',
      }),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: [key, variables.datasetId] })
    },
  })
}

export const useDeleteDataSink = () => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<void>, AxiosError, DeleteDataSinkInput>({
    mutationFn: ({ datasetId, dataSinkId }) =>
      apiRequest<void>({
        method: 'DELETE',
        endpoint: `/datasets/${datasetId}/datasinks/${dataSinkId}`,
        headers: { 'x-api-request': 'true' },
        errorMessage: 'An error occurred while deleting the data sink.',
      }),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: [key, variables.datasetId] })
    },
  })
}

export const useUpdateDataSink = () => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<DataSink>, AxiosError, UpdateDataSinkInput>({
    mutationFn: ({ datasetId, dataSinkId, data }) =>
      apiRequest<DataSink>({
        method: 'PUT',
        endpoint: `/datasets/${datasetId}/datasinks/${dataSinkId}`,
        headers: { 'x-api-request': 'true' },
        data,
        errorMessage: 'An error occurred while updating the data sink.',
      }),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: [key, variables.datasetId] })
    },
  })
}
