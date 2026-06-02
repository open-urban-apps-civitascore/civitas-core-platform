import { useQuery } from '@tanstack/react-query'

import { apiRequest, type ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { GetListInput } from '@/types/common'
import { Datasink } from '@/types/datasinks'

const key = 'datasinks'

export const useGetDatasinks = (datasetId: string, { params, isEnabled }: GetListInput = {}) =>
  useQuery<ApiServiceResponse<Datasink[]>>({
    queryKey: [key, datasetId],
    queryFn: () =>
      apiRequest<Datasink[]>({
        method: 'GET',
        endpoint: `/datasets/${datasetId}/datasinks`,
        headers: { 'x-api-request': 'true' },
        params,
        errorMessage: 'An error occurred while loading datasinks.',
      }),
    enabled: isEnabled,
  })
