import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AxiosError } from 'axios'

import { apiRequest, ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { useDataQuery } from '@/hooks/use-data-query'
import { DeleteStyleInput, Style, StyleInput, UpdateStyleInput } from '@/types/styles'

const key = 'styles'

export const useGetStyles = (datasetId: string, isEnabled = true) =>
  useDataQuery<Style[]>({
    key: `datasets/${datasetId}/styles`,
    queryKey: key,
    isEnabled,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while loading styles.',
  })

type CreateStyleInput = {
  datasetId: string
  style: StyleInput
}

export const useCreateStyle = () => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<Style>, AxiosError, CreateStyleInput>({
    mutationFn: ({ datasetId, style }) =>
      apiRequest<Style>({
        method: 'POST',
        endpoint: `/datasets/${datasetId}/styles`,
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

export const useUpdateStyle = () => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<Style>, AxiosError, UpdateStyleInput>({
    mutationFn: ({ datasetId, stilId, style }) =>
      apiRequest<Style>({
        method: 'PUT',
        endpoint: `/datasets/${datasetId}/styles/${stilId}`,
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

export const useDeleteStyle = () => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<Style>, AxiosError, DeleteStyleInput>({
    mutationFn: ({ datasetId, stilId }) =>
      apiRequest<Style>({
        method: 'DELETE',
        endpoint: `/datasets/${datasetId}/styles/${stilId}`,
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
