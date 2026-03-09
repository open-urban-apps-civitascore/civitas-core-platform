import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AxiosError } from 'axios'

import { apiRequest, ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { CreateMutationInput } from '@/types/common'

export const useCreateMutation = <TResponse, TData>({
  key: mutationKey,
  errorMessage,
  headers,
}: CreateMutationInput) => {
  const queryClient = useQueryClient()

  return useMutation<ApiServiceResponse<TResponse>, unknown, TData>({
    mutationFn: (data: TData) =>
      apiRequest<TResponse>({
        method: 'POST',
        endpoint: `/${mutationKey}`,
        headers,
        data: data,
        errorMessage: errorMessage,
      }),
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
