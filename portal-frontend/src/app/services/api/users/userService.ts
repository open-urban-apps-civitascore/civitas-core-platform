import { headers } from 'next/headers'

import { UserResponse } from '@/types/users'
import { mapListUsers } from '@/utils/users'

export const getUsers = async (params: URLSearchParams) => {
  try {
    const usersResponse = await fetch(`${process.env.NEXT_SERVER_URL}/api/users?${params.toString()}`, {
      headers: {
        cookie: (await headers()).get('cookie') || '',
      },
      cache: 'no-store',
    })

    if (!usersResponse.ok) {
      console.error('An error occurred while loading user list data')
      throw new Error('Failed to fetch user list')
    }

    const usersData = await usersResponse.json()

    const users = mapListUsers(usersData.content as UserResponse[], [])
    const totalCount = Number(usersData.totalElements) || 0

    return { users, totalCount }
  } catch (error) {
    console.error(error)
    throw new Error(`Failed to fetch user list: ${error}`)
  }
}
