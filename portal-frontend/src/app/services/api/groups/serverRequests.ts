import { Group } from '@/types/groups'

import { apiRequest } from '../request/apiRequest'
import { getServerRequestHeaders } from '../request/getServerRequestHeaders'

export const getGroups = async (params?: URLSearchParams) =>
  apiRequest<Group[]>({
    method: 'GET',
    endpoint: `/groups`,
    params,
    // eslint-disable-next-line @typescript-eslint/naming-convention
    headers: { ...(await getServerRequestHeaders()), 'x-api-request': 'true' },
    errorMessage: 'An error occurred while fetching groups data.',
  })
