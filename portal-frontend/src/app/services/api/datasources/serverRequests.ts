import { BaseDatasource } from '@/types/datasources'

import { apiRequest } from '../request/apiRequest'
import { getServerRequestHeaders } from '../request/getServerRequestHeaders'

export const getDatasources = async (params?: URLSearchParams) =>
  apiRequest<BaseDatasource[]>({
    endpoint: `/datasources`,
    method: 'GET',
    params,
    headers: await getServerRequestHeaders(),
    errorMessage: 'An error occurred while fetching datasources.',
  })

export const getDatasource = async (id: string) =>
  apiRequest<BaseDatasource>({
    endpoint: `/datasources/${id}`,
    method: 'GET',
    headers: await getServerRequestHeaders(),
    errorMessage: 'An error occurred while fetching datasource.',
  })
