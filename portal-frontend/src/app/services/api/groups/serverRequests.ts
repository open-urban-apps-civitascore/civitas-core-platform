/* eslint-disable @typescript-eslint/naming-convention */
import { Group } from '@/types/groups'

import { apiRequest } from '../request/apiRequest'
import { getServerRequestHeaders } from '../request/getServerRequestHeaders'

export const getGroups = async (params?: URLSearchParams) =>
  apiRequest<Group[]>({
    method: 'GET',
    endpoint: `/groups`,
    params,
    headers: { ...(await getServerRequestHeaders()), 'x-api-request': 'true' },
    errorMessage: 'An error occurred while fetching groups data.',
  })

export const getGroup = async (id: string) =>
  apiRequest<Group>({
    method: 'GET',
    endpoint: `/groups/${id}`,
    headers: { ...(await getServerRequestHeaders()), 'x-api-request': 'true' },
    errorMessage: 'An error occurred while fetching user data.',
  })
