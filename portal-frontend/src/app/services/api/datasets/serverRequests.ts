import { serverFetch } from '@/lib/serverFetch'
import { Assignment } from '@/types/assignments'
import { Dataset } from '@/types/datasets'

/**
 * Server-side data fetching for datasources
 * These functions use serverFetch which directly communicates with the backend
 * using NextAuth's auth() to get the access token.
 *
 * IMPORTANT: Only use these in Server Components and Server Actions
 * For client-side requests, use the clientRequests.ts functions instead
 */

export const getDatasets = async (params?: URLSearchParams) => {
  try {
    return await serverFetch<Dataset[]>({
      endpoint: '/datasets',
      method: 'GET',
      params,
      isApiBackend: true, // Use real API backend instead of JSON Server
    })
  } catch (error) {
    console.error('An error occurred while fetching datasets.', error)
    throw new Error('An error occurred while fetching datasets.')
  }
}

export const getDataset = async (id: string) => {
  try {
    return await serverFetch<Dataset>({
      endpoint: `/datasets/${id}`,
      method: 'GET',
      isApiBackend: true, // Use real API backend instead of JSON Server
    })
  } catch (error) {
    console.error(`An error occurred while fetching dataset ${id}.`, error)
    throw new Error('An error occurred while fetching dataset.')
  }
}

export const getDatasetAssignments = async (datasetId: string) => {
  try {
    return await serverFetch<Assignment[]>({
      endpoint: `/datasets/${datasetId}/assignments`,
      method: 'GET',
      isApiBackend: true, // Use real API backend instead of JSON Server
    })
  } catch (error) {
    console.error(`An error occurred while fetching assignments for dataset ${datasetId}.`, error)
    throw new Error('An error occurred while fetching dataset assignments.')
  }
}
