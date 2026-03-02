import { Dataset } from '@/types/datasets'

import { TEST_ENV } from '../../../playwright.config'

export const getMockDatasetData = (overrides: Partial<Dataset> = {}): Dataset => {
  const id = crypto.randomUUID()
  const name = `E2EDatasetName-${TEST_ENV}-${id}`
  return {
    id: id,
    name: name,
    description: 'Dataset Description',
    createdAt: new Date().toISOString(),
    createdBy: { id: crypto.randomUUID(), name: 'E2E Test User' },
    modifiedAt: new Date().toISOString(),
    dataSetStatus: 'DRAFT',
    openDataAccess: false,
    distributions: [],
    pipelines: [],
    ...overrides,
  }
}
