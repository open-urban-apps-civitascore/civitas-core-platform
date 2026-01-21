import { DataSpace } from '@/types/dataspaces'

import { apiRequest } from '../request/apiRequest'
import { getServerRequestHeaders } from '../request/getServerRequestHeaders'

export const getDataspaces = async () =>
  apiRequest<DataSpace[]>({
    endpoint: `/dataspaces`,
    method: 'GET',
    headers: await getServerRequestHeaders(),
    errorMessage: 'An error occurred while fetching dataspaces.',
  })
