import { request } from '@playwright/test'

import { UserResponse } from '@/types/users'

import { getMockUserData } from './helpers/userFactory'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

export const createTestUser = async (user?: UserResponse) => {
  const userData: UserResponse = user || getMockUserData()
  console.log('creating test user: ', userData)
  const api = await request.newContext({
    baseURL: URL,
  })

  const res = await api.post('/users', { data: userData })
  if (!res.ok()) {
    console.error('Failed to create new user')
  }
  const newUser = await res.json()

  console.log('NEW USER: ', newUser)

  await api.dispose()

  return newUser
}
