import { request } from '@playwright/test'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

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
