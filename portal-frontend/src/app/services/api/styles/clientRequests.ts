import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AxiosError } from 'axios'

import { apiRequest, ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { useDataQuery } from '@/hooks/use-data-query'
import { Stil, StilInput } from '@/types/styles'

const key = 'stiles'

export const useGetStyles = (datasetId: string, isEnabled = true) =>
  useDataQuery<Stil[]>({
    key: `datasets/${datasetId}/stiles`,
    queryKey: key,
    isEnabled,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while loading styles.',
  })

type CreateStyleInput = {
  datasetId: string
  style: StilInput
}

export const useCreateStyle = () => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<Stil>, AxiosError, CreateStyleInput>({
    mutationFn: ({ datasetId, style }) =>
      apiRequest<Stil>({
        method: 'POST',
        endpoint: `/datasets/${datasetId}/stiles`,
        headers: { 'x-api-request': 'true' },
        data: style,
        errorMessage: 'An error occurred while creating the style.',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [key] })
    },
    onError: error => {
      console.error('Error creating style:', error.message)
    },
  })
}

type UpdateStyleInput = {
  datasetId: string
  stilId: string
  style: StilInput
}

export const useUpdateStyle = () => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<Stil>, AxiosError, UpdateStyleInput>({
    mutationFn: ({ datasetId, stilId, style }) =>
      apiRequest<Stil>({
        method: 'PUT',
        endpoint: `/datasets/${datasetId}/stiles/${stilId}`,
        headers: { 'x-api-request': 'true' },
        data: style,
        errorMessage: 'An error occurred while updating the style.',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [key] })
    },
    onError: error => {
      console.error('Error updating style:', error.message)
    },
  })
}

type DeleteStyleInput = {
  datasetId: string
  stilId: string
}

export const useDeleteStyle = () => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<Stil>, AxiosError, DeleteStyleInput>({
    mutationFn: ({ datasetId, stilId }) =>
      apiRequest<Stil>({
        method: 'DELETE',
        endpoint: `/datasets/${datasetId}/stiles/${stilId}`,
        headers: { 'x-api-request': 'true' },
        errorMessage: 'An error occurred while deleting the style.',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [key] })
    },
    onError: error => {
      console.error('Error deleting style:', error.message)
    },
  })
}
