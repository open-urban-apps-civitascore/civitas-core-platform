import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AxiosError } from 'axios'

import { apiRequest, ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { DeleteMutationInput } from '@/types/common'

export const useDeleteMutation = <TResponse, TData>({
  key: mutationKey,
  errorMessage,
  headers,
}: DeleteMutationInput<TData>) => {
  const queryClient = useQueryClient()

  return useMutation<ApiServiceResponse<TResponse>, AxiosError, string>({
    mutationFn: (id: string) =>
      apiRequest<TResponse>({
        method: 'DELETE',
        headers,
        endpoint: `/${mutationKey}/${id}`,
        errorMessage: errorMessage,
      }),
    onSuccess: (_data, id) => {
      queryClient.removeQueries({ queryKey: [mutationKey, id] })
      queryClient.invalidateQueries({
        queryKey: [mutationKey],
      })
    },
    onError: error => {
      console.error(errorMessage, (error as AxiosError).message)
    },
  })
}
