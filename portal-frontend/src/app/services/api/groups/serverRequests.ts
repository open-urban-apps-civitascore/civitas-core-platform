import { Group } from '@/types/groups'

import { apiRequest } from '../request/apiRequest'
import { getServerRequestHeaders } from '../request/getServerRequestHeaders'

export const getGroups = async (params?: URLSearchParams) =>
  apiRequest<Group[]>({
    method: 'GET',
    endpoint: `/groups`,
    params,
    headers: await getServerRequestHeaders(),
    errorMessage: 'An error occurred while fetching groups data.',
  })
