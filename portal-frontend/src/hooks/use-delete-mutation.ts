import { useMutation, useQueryClient } from '@tanstack/react-query'

import { apiRequest, ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { DeleteMutationInput } from '@/types/common'

type DeleteMutationOptions = Omit<DeleteMutationInput, 'id'>

export const useDeleteMutation = <TResponse>({ key: mutationKey, errorMessage, headers }: DeleteMutationOptions) => {
  const queryClient = useQueryClient()

  return useMutation<ApiServiceResponse<TResponse>, Error, string>({
    mutationFn: (id: string) =>
      apiRequest<TResponse>({
        method: 'DELETE',
        endpoint: `/${mutationKey}/${id}`,
        errorMessage: errorMessage,
        headers,
      }),
    onSuccess: (_data, id) => {
      queryClient.removeQueries({ queryKey: [mutationKey, id] })
      queryClient.invalidateQueries({
        queryKey: [mutationKey],
      })
    },
    onError: error => {
      console.error(errorMessage, error)
    },
  })
}
