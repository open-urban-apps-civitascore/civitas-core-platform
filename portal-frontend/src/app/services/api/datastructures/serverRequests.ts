import { Datastructure } from '@/types/datastructures'

import { apiRequest } from '../request/apiRequest'
import { getServerRequestHeaders } from '../request/getServerRequestHeaders'

export const getDatastructures = async (params?: URLSearchParams) =>
  apiRequest<Datastructure[]>({
    endpoint: `/datastructures`,
    method: 'GET',
    params,
    headers: await getServerRequestHeaders(),
    errorMessage: 'An error occurred while fetching datastructures.',
  })

export const getDatastructure = async (id: string) =>
  apiRequest<Datastructure[]>({
    endpoint: `/datastructures/${id}`,
    method: 'GET',
    headers: await getServerRequestHeaders(),
    errorMessage: 'An error occurred while fetching datastructure.',
  })
