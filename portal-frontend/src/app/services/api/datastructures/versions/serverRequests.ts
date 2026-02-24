import { DatastructureVersion } from '@/types/datastructures'

import { apiRequest } from '../../request/apiRequest'
import { getServerRequestHeaders } from '../../request/getServerRequestHeaders'

export const getDatastructureVersion = async (datastructureId: string, versionId: string, params?: URLSearchParams) =>
  apiRequest<DatastructureVersion>({
    endpoint: `/datastructures/${datastructureId}/versions/${versionId}`,
    method: 'GET',
    params,
    headers: await getServerRequestHeaders(),
    errorMessage: 'An error occurred while fetching datastructure version.',
  })
