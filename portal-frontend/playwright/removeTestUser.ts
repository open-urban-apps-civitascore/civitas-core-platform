import { request } from '@playwright/test'

import { JSON_SERVER_HOST, JSON_SERVER_PORT } from '../playwright.config'

const URL = `${JSON_SERVER_HOST}:${JSON_SERVER_PORT}`

export const removeTestUser = async (userId: string) => {
  console.log('deleting test user: ', userId)
  const api = await request.newContext({
    baseURL: URL,
  })

  const res = await api.delete(`/users/${userId}`)
  if (!res.ok()) {
    console.error('Failed to delete user')
  }
  await api.dispose()
}
