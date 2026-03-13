import { serverFetch } from '@/lib/serverFetch'
import { CurrentUser } from '@/types/currentUser'
import { User } from '@/types/users'

/**
 * Server-side data fetching for users
 * Uses serverFetch for direct backend communication with NextAuth's auth()
 * IMPORTANT: Only use in Server Components and Server Actions
 */

export const getUsers = async (params: URLSearchParams) => {
  try {
    return await serverFetch<User[]>({
      endpoint: '/users',
      method: 'GET',
      params,
      isApiBackend: true,
    })
  } catch (error) {
    console.error('An error occurred while fetching users.', error)
    throw new Error('An error occurred while fetching users.')
  }
}

export const getUser = async (id: string) => {
  try {
    return await serverFetch<User>({
      endpoint: `/users/${id}`,
      method: 'GET',
      isApiBackend: true,
    })
  } catch (error) {
    console.error(`An error occurred while fetching user data for ${id}.`, error)
    throw new Error('An error occurred while fetching user data.')
  }
}

export const getCurrentUser = async (): Promise<CurrentUser> => {
  return serverFetch<CurrentUser>({
    endpoint: '/users/me',
    method: 'GET',
    isApiBackend: true,
  }).then(response => response.data)
}
