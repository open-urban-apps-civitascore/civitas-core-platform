import { useMutation, useQueryClient } from '@tanstack/react-query'

import { apiRequest, ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { MutationData, UpdateMutationInput, WithId } from '@/types/common'

export const useUpdateMutation = <TResponse, TData extends WithId>({
  method,
  key: mutationKey,
  errorMessage,
}: UpdateMutationInput) => {
  const queryClient = useQueryClient()

  return useMutation<ApiServiceResponse<TResponse>, unknown, MutationData<TData>>({
    mutationFn: (data: MutationData<TData>) =>
      apiRequest<TResponse>({
        method: method,
        endpoint: `/${mutationKey}/${data.id}`,
        data: data,
        errorMessage: errorMessage,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: [mutationKey],
      })
    },
    onError: error => {
      console.error(errorMessage, error)
    },
  })
}
