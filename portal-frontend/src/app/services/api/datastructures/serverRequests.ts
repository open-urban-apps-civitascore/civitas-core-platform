import { serverFetch } from '@/lib/serverFetch'
import { Assignment } from '@/types/assignments'
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
      isApiBackend: true,
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
      isApiBackend: true,
    })
  } catch (error) {
    console.error(`An error occurred while fetching datastructure ${id}.`, error)
    throw new Error('An error occurred while fetching datastructure.')
  }
}

export const getDatastructureAssignments = async (datastructureId: string) => {
  try {
    return await serverFetch<Assignment[]>({
      endpoint: `/datastructures/${datastructureId}/assignments`,
      method: 'GET',
      isApiBackend: true,
    })
  } catch (error) {
    console.error(`An error occurred while fetching assignments for datastructure ${datastructureId}.`, error)
    throw new Error('An error occurred while fetching datastructure assignments.')
  }
}
