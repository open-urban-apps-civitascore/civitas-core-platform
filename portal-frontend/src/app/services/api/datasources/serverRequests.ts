import { serverFetch } from '@/lib/serverFetch'
import { Datasource } from '@/types/datasources'

/**
 * Server-side data fetching for datasources
 * These functions use serverFetch which directly communicates with the backend
 * using NextAuth's auth() to get the access token.
 *
 * IMPORTANT: Only use these in Server Components and Server Actions
 * For client-side requests, use the clientRequests.ts functions instead
 */

export const getDatasources = async (params?: URLSearchParams) => {
  try {
    return await serverFetch<Datasource[]>({
      endpoint: '/datasources',
      method: 'GET',
      params,
      isApiBackend: true,
    })
  } catch (error) {
    console.error('An error occurred while fetching datasources.', error)
    throw new Error('An error occurred while fetching datasources.')
  }
}

export const getDatasource = async (id: string) => {
  try {
    return await serverFetch<Datasource>({
      endpoint: `/datasources/${id}`,
      method: 'GET',
      isApiBackend: true,
    })
  } catch (error) {
    console.error(`An error occurred while fetching datasource ${id}.`, error)
    throw new Error('An error occurred while fetching datasource.')
  }
}
