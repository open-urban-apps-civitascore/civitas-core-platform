import { request } from '@playwright/test'

import { JSON_SERVER_HOST, JSON_SERVER_PORT } from '../../../playwright.config'

const URL = `${JSON_SERVER_HOST}:${JSON_SERVER_PORT}`

export const removeTestDataset = async (datasetId: string) => {
  console.log('deleting test dataset: ', datasetId)
  const api = await request.newContext({
    baseURL: URL,
  })

  const res = await api.delete(`/datasets/${datasetId}`)
  if (!res.ok()) {
    console.error('Failed to delete dataset')
  }
  await api.dispose()
}
