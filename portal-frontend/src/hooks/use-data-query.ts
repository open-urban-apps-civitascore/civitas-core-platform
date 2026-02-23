import { useQuery, UseQueryResult } from '@tanstack/react-query'

import { apiRequest, ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { DataQueryInput as DataQueryInput } from '@/types/common'

export const useDataQuery = <TResponse>({
  id,
  key,
  queryKey,
  params,
  headers,
  isEnabled,
  errorMessage,
}: DataQueryInput): UseQueryResult<ApiServiceResponse<TResponse>> => {
  return useQuery({
    queryKey: [queryKey || key, id || params?.toString()],
    queryFn: () =>
      apiRequest<TResponse>({
        endpoint: id ? `/${key}/${id}` : `/${key}`,
        method: 'GET',
        headers,
        params: params,
        errorMessage: errorMessage,
      }),
    placeholderData: previousData => previousData,
    enabled: isEnabled,
  })
}
