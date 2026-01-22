import { useMutation, useQueryClient } from '@tanstack/react-query'

import { apiRequest, ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { DeleteMutationInput } from '@/types/common'

export const useDeleteMutation = <TResponse>({ id, key: mutationKey, errorMessage }: DeleteMutationInput) => {
  const queryClient = useQueryClient()

  return useMutation<ApiServiceResponse<TResponse>>({
    mutationFn: () =>
      apiRequest<TResponse>({
        method: 'DELETE',
        endpoint: `/${mutationKey}/${id}`,
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
