import { useMutation, useQueryClient } from '@tanstack/react-query'

import { apiRequest, ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { DeleteMutationInput } from '@/types/common'

export const useDeleteMutation = <TResponse>({ key: mutationKey, errorMessage, headers }: DeleteMutationInput) => {
  const queryClient = useQueryClient()

  return useMutation<ApiServiceResponse, Error, string>({
    mutationFn: (id: string) =>
      apiRequest<TResponse>({
        method: 'DELETE',
        headers,
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
