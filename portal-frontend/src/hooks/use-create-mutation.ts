import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AxiosError } from 'axios'

import { apiRequest, ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { CreateMutationInput } from '@/types/common'
import { getRequestEndpoint, isFn } from '@/utils/common'

export const useCreateMutation = <TResponse, TData>({
  key: mutationKey,
  errorMessage,
  endpoint,
  headers,
}: CreateMutationInput<TData>) => {
  const queryClient = useQueryClient()

  return useMutation<ApiServiceResponse<TResponse>, unknown, TData>({
    mutationFn: (data: TData) => {
      const url = isFn(endpoint) ? getRequestEndpoint(endpoint, data) : endpoint

      return apiRequest<TResponse>({
        method: 'POST',
        endpoint: url || `/${mutationKey}`,
        headers,
        data: data,
        errorMessage: errorMessage,
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: [mutationKey],
      })
    },
    onError: error => {
      console.error(errorMessage, (error as AxiosError).message)
    },
  })
}
