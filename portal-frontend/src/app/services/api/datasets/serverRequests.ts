import { serverFetch } from '@/lib/serverFetch'
import { Dataset } from '@/types/datasets'

/**
 * Server-side data fetching for datasets
 * Uses serverFetch for direct backend communication with NextAuth's auth()
 * IMPORTANT: Only use in Server Components and Server Actions
 */

export const getDataset = async (id: string) => {
  try {
    return await serverFetch<Dataset>({
      endpoint: `/datasets/${id}`,
      method: 'GET',
    })
  } catch (error) {
    console.error(`An error occurred while fetching dataset ${id}.`, error)
    throw new Error('An error occurred while fetching dataset.')
  }
}
