// global-teardown.ts
import { CreateUserData, UserResponse } from '@/types/users'
import { request } from '@playwright/test'
import { getMockUserData } from './helpers/userFactory'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

export const createTestUser = async (user?: UserResponse) => {
  const userData: UserResponse = user || getMockUserData()
  console.log('creating test user')
  const api = await request.newContext({
    baseURL: URL,
  })

  // find E2E test mock users

  const res = await api.post('/users', { data: userData })
  if (!res.ok()) {
    console.error('Failed to create new user')
  }
  const newUser = await res.json()

  console.log('NEW USER: ', newUser)

  await api.dispose()

  return newUser
}
