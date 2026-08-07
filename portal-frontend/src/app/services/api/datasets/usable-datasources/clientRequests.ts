import { useQuery } from '@tanstack/react-query'

import { apiRequest, type ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { GetListInput } from '@/types/common'
import { DatasourceSummary } from '@/types/datasources'

const key = 'usable-datasources'

/**
 * Fetches the data sources this dataset's pipelines may be built from.
 *
 * Requires DATASET_UPDATE on the dataset, not DATASOURCE_READ — enable it accordingly.
 */
export const useGetUsableDatasources = (datasetId: string, { params, isEnabled = true }: GetListInput = {}) =>
  useQuery<ApiServiceResponse<DatasourceSummary[]>>({
    queryKey: [key, datasetId],
    queryFn: () =>
      apiRequest<DatasourceSummary[]>({
        method: 'GET',
        endpoint: `/datasets/${datasetId}/usable-datasources`,
        headers: { 'x-api-request': 'true' },
        params,
        errorMessage: 'An error occurred while loading usable data sources.',
      }),
    enabled: isEnabled && !!datasetId,
  })
