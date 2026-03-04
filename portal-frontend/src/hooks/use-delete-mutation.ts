import { useMutation, useQueryClient } from '@tanstack/react-query'

import { apiRequest, ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { DeleteMutationInput } from '@/types/common'

type DeleteMutationOptions<TData> = Omit<DeleteMutationInput<TData>, 'id'>

export const useDeleteMutation = <TResponse, TData>({
  key: mutationKey,
  errorMessage,
  headers,
}: DeleteMutationOptions<TData>) => {
  const queryClient = useQueryClient()

  return useMutation<ApiServiceResponse<TResponse>, Error, string>({
    mutationFn: (id: string) =>
      apiRequest<TResponse>({
        method: 'DELETE',
        endpoint: `/${mutationKey}/${id}`,
        errorMessage: errorMessage,
        headers,
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
