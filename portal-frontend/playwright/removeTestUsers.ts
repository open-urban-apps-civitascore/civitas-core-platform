// global-teardown.ts
import { request } from '@playwright/test'

import { E2E_MOCK_EMAIL, E2E_MOCK_FIRSTNAME, E2E_MOCK_LASTNAME } from '../playwright.config'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

export const removeTestUsers = async () => {
  console.log('removing test users')
  const api = await request.newContext({
    baseURL: URL,
  })

  // find E2E test mock users
  const params = `firstName_like=${E2E_MOCK_FIRSTNAME}&lastName_like=${E2E_MOCK_LASTNAME}&email_like${E2E_MOCK_EMAIL}`

  const res = await api.get(`/users?${params}`)
  if (!res.ok) {
    console.error('Failed to load previous E2E test users')
  }
  const users = await res.json()

  console.log('USERS: ', users)

  // remove E2E test mock users
  for (const user of users) {
    try {
      const deleteRes = await api.delete(`/users/${user.id}`)
      if (!deleteRes.ok()) {
        console.error('Error while deleting user')
      } else {
        console.log(`Deleted E2E test user: ${user.id}`)
      }
    } catch (err) {
      console.error(`Failed to delete user ${user.id}:`, err)
    }
  }

  await api.dispose()
}
