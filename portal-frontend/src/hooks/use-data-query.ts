import { useQuery, UseQueryResult } from '@tanstack/react-query'

import { apiRequest, ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { DataQueryInput as DataQueryInput } from '@/types/common'

export const useDataQuery = <TResponse>({
  id,
  key: queryKey,
  params,
  isEnabled,
  errorMessage,
}: DataQueryInput): UseQueryResult<ApiServiceResponse<TResponse>> => {
  return useQuery({
    queryKey: [queryKey, id || params?.toString()],
    queryFn: () =>
      apiRequest<TResponse>({
        endpoint: id ? `/${queryKey}/${id}` : `/${queryKey}`,
        method: 'GET',
        params: params,
        errorMessage: errorMessage,
      }),
    placeholderData: previousData => previousData,
    enabled: isEnabled,
  })
}
