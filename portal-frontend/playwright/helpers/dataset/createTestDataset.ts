import { request } from '@playwright/test'

import { DatasetResponse } from '@/types/datasets'

import { getMockDatasetData } from './datasetFactory'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

export const createTestDataset = async (dataset?: DatasetResponse) => {
  const datasetData: DatasetResponse = dataset || getMockDatasetData()
  console.log('creating test DATASET: ', datasetData)
  const api = await request.newContext({
    baseURL: URL,
  })

  const res = await api.post('/datasets', { data: datasetData })
  if (!res.ok()) {
    console.error('Failed to create new dataset')
  }
  const newDataset = await res.json()

  console.log('NEW DATASET: ', newDataset)

  await api.dispose()

  return newDataset
}
