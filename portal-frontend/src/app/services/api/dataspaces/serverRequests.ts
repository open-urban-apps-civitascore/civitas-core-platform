import { serverFetch } from '@/lib/serverFetch'
import { DataSpace } from '@/types/dataspaces'

/**
 * Server-side data fetching for dataspaces
 * Uses serverFetch for direct backend communication with NextAuth's auth()
 * IMPORTANT: Only use in Server Components and Server Actions
 */

export const getDataspaces = async () => {
  try {
    return await serverFetch<DataSpace[]>({
      endpoint: '/dataspaces',
      method: 'GET',
    })
  } catch (error) {
    console.error('An error occurred while fetching dataspaces.', error)
    throw new Error('An error occurred while fetching dataspaces.')
  }
}
