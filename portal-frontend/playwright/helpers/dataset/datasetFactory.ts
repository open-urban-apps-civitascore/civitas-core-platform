import { DatasetResponse } from '@/types/datasets'
import { TEST_ENV } from '../../../playwright.config'

export const getMockDatasetData = (overrides: Partial<DatasetResponse> = {}): DatasetResponse => {
  const id = crypto.randomUUID()
  const name = `E2EDatasetName-${TEST_ENV}-${id}`
  return {
    id: id,
    name: name,
    description: 'Dataset Description',
    creator: [],
    issued: new Date().toISOString(),
    lastUpdated: new Date().toISOString(),
    status: null,
    distribution: null,
    dataspace: null,
    department: null,
    tags: ['testTag1', 'testTag2'],
    ...overrides,
  }
}
