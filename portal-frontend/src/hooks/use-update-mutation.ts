import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AxiosError } from 'axios'

import { apiRequest, ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { MutationData, UpdateMutationInput, WithId } from '@/types/common'

export const useUpdateMutation = <TResponse, TData extends WithId<string | number>>({
  method,
  key: mutationKey,
  errorMessage,
  headers,
}: UpdateMutationInput) => {
  const queryClient = useQueryClient()

  return useMutation<ApiServiceResponse<TResponse>, unknown, MutationData<TData>>({
    mutationFn: (data: MutationData<TData>) =>
      apiRequest<TResponse>({
        method: method,
        endpoint: `/${mutationKey}/${data.id}`,
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
