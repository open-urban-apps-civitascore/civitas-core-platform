import { Dataset } from '@/types/datasets'

import { apiRequest } from '../request/apiRequest'
import { getServerRequestHeaders } from '../request/getServerRequestHeaders'

export const getDataset = async (id: string) =>
  apiRequest<Dataset>({
    endpoint: `/datasets/${id}`,
    method: 'GET',
    headers: await getServerRequestHeaders(),
    errorMessage: 'An error occurred while fetching dataset.',
  })
