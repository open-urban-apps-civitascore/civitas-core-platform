import { useQuery } from '@tanstack/react-query'
import { AxiosError } from 'axios'

import { apiRequest, ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { Style } from '@/types/namedApis'

const key = 'styles'

export const useGetStyles = (datasetId: string) =>
  useQuery<ApiServiceResponse<Style[]>, AxiosError>({
    queryKey: [key, datasetId],
    queryFn: () =>
      apiRequest<Style[]>({
        method: 'GET',
        endpoint: `/datasets/${datasetId}/styles`,
        headers: { 'x-api-request': 'true' },
        errorMessage: 'An error occurred while loading styles.',
      }),
  })
