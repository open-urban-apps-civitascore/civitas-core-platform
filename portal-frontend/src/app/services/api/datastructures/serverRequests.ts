import { serverFetch } from '@/lib/serverFetch'
import { Datastructure } from '@/types/datastructures'

/**
 * Server-side data fetching for datastructures
 * Uses serverFetch for direct backend communication with NextAuth's auth()
 * IMPORTANT: Only use in Server Components and Server Actions
 */

export const getDatastructures = async (params?: URLSearchParams) => {
  try {
    return await serverFetch<Datastructure[]>({
      endpoint: '/datastructures',
      method: 'GET',
      params,
    })
  } catch (error) {
    console.error('An error occurred while fetching datastructures.', error)
    throw new Error('An error occurred while fetching datastructures.')
  }
}

export const getDatastructure = async (id: string) => {
  try {
    return await serverFetch<Datastructure[]>({
      endpoint: `/datastructures/${id}`,
      method: 'GET',
    })
  } catch (error) {
    console.error(`An error occurred while fetching datastructure ${id}.`, error)
    throw new Error('An error occurred while fetching datastructure.')
  }
}
