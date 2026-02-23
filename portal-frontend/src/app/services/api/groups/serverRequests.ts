import { serverFetch } from '@/lib/serverFetch'
import { Group } from '@/types/groups'

/**
 * Server-side data fetching for groups
 * Uses serverFetch for direct backend communication with NextAuth's auth()
 * IMPORTANT: Only use in Server Components and Server Actions
 */

export const getGroups = async (params?: URLSearchParams) => {
  try {
    return await serverFetch<Group[]>({
      endpoint: '/groups',
      method: 'GET',
      params,
    })
  } catch (error) {
    console.error('An error occurred while fetching groups data.', error)
    throw new Error('An error occurred while fetching groups data.')
  }
}

export const getGroup = async (id: string) => {
  try {
    return await serverFetch<Group>({
      endpoint: `/groups/${id}`,
      method: 'GET',
    })
  } catch (error) {
    console.error('An error occurred while fetching group data.', error)
    throw new Error('An error occurred while fetching group data.')
  }
}
