import { useQuery } from '@tanstack/react-query'

import { apiRequest, type ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { GetListInput } from '@/types/common'
import { DatasourceSummary } from '@/types/datasources'

const key = 'usable-datasources'

/**
 * Fetches the data sources this dataset's pipelines may be built from.
 * GET /datasets/{datasetId}/usable-datasources
 *
 * Authorized on the dataset (DATASET_UPDATE), not on the data sources, so a datapool-scoped caller
 * reaches it without DATASOURCE_READ. The datapool and status filtering happens server-side.
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
    // The route param is empty on the first render; without this the request would go to
    // /datasets//usable-datasources.
    enabled: isEnabled && !!datasetId,
  })
