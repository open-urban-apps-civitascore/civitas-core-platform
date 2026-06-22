import { serverFetch } from '@/lib/serverFetch'
import { Assignment } from '@/types/assignments'
import { Datapool, DatapoolSummary } from '@/types/datapools'

/**
 * Server-side data fetching for datapools
 * These functions use serverFetch which directly communicates with the backend
 * using NextAuth's auth() to get the access token.
 *
 * IMPORTANT: Only use these in Server Components and Server Actions
 * For client-side requests, use the clientRequests.ts functions instead
 */

export const getDatapools = async (params?: URLSearchParams) => {
  try {
    return await serverFetch<DatapoolSummary[]>({
      endpoint: '/datapools',
      method: 'GET',
      params,
      isApiBackend: true,
    })
  } catch (error) {
    console.error('An error occurred while fetching datapools.', error)
    return { data: [] as DatapoolSummary[] }
  }
}

export const getDatapool = async (datapoolId: string) => {
  try {
    return await serverFetch<Datapool>({
      endpoint: `/datapools/${datapoolId}`,
      method: 'GET',
      isApiBackend: true,
    })
  } catch (error) {
    console.error(`An error occurred while fetching datapool with id ${datapoolId}.`, error)
    throw new Error(`An error occurred while fetching datapool with id ${datapoolId}.`)
  }
}

export const getDatapoolAssignments = async (datapoolId: string) => {
  try {
    return await serverFetch<Assignment[]>({
      endpoint: `/datapools/${datapoolId}/assignments`,
      method: 'GET',
      isApiBackend: true,
    })
  } catch (error) {
    console.error(`An error occurred while fetching assignments for datapool ${datapoolId}.`, error)
    return { data: [] as Assignment[] }
  }
}
