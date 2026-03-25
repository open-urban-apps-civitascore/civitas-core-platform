import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AxiosError } from 'axios'

import { apiRequest, ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { MutationData, UpdateMutationInput, WithId } from '@/types/common'
import { getRequestEndpoint, isFn } from '@/utils/common'

export const useUpdateMutation = <TResponse, TData extends WithId<string>>({
  method,
  key: mutationKey,
  endpoint,
  errorMessage,
  headers,
}: UpdateMutationInput<TData>) => {
  const queryClient = useQueryClient()

  return useMutation<ApiServiceResponse<TResponse>, AxiosError, MutationData<TData>>({
    mutationFn: (data: MutationData<TData>) => {
      const url = isFn(endpoint) ? getRequestEndpoint(endpoint, data) : endpoint

      return apiRequest<TResponse>({
        method: method,
        endpoint: url || `/${mutationKey}/${data.id}`,
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
