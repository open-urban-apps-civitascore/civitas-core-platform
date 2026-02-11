/* eslint-disable @typescript-eslint/naming-convention */
import { User } from '@/types/users'

import { apiRequest } from '../request/apiRequest'
import { getServerRequestHeaders } from '../request/getServerRequestHeaders'

export const getUsers = async (params: URLSearchParams) =>
  apiRequest<User[]>({
    endpoint: '/users',
    method: 'GET',
    params: params,
    headers: { ...(await getServerRequestHeaders()), 'x-api-request': 'true' },
    errorMessage: 'An error occurred while fetching users.',
  })

export const getUser = async (id: string) =>
  apiRequest<User>({
    method: 'GET',
    endpoint: `/users/${id}`,
    headers: { ...(await getServerRequestHeaders()), 'x-api-request': 'true' },
    errorMessage: 'An error occurred while fetching user data.',
  })
