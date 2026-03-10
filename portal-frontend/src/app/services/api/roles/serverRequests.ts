import { serverFetch } from '@/lib/serverFetch'
import { Role } from '@/types/roles'

/**
 * Server-side data fetching for roles
 * Uses serverFetch for direct backend communication with NextAuth's auth()
 * IMPORTANT: Only use in Server Components and Server Actions
 */

export const getRoles = async (params?: URLSearchParams) => {
  try {
    return await serverFetch<Role[]>({
      endpoint: '/roles',
      method: 'GET',
      params,
      isApiBackend: true, // Roles endpoint uses real API backend
    })
  } catch (error) {
    console.error('An error occurred while fetching roles data.', error)
    throw new Error('An error occurred while fetching roles data.')
  }
}

export const getRole = async (id: string) => {
  try {
    return await serverFetch<Role>({
      endpoint: `/roles/${id}`,
      method: 'GET',
      isApiBackend: true, // Roles endpoint uses real API backend
    })
  } catch (error) {
    console.error(`An error occurred while fetching role with id ${id}.`, error)
    throw new Error(`An error occurred while fetching role with id ${id}.`)
  }
}
