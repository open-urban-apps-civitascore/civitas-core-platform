import { Dataset } from '@/types/datasets'

import { TEST_ENV } from '../../../playwright.config'

export const getMockDatasetData = (overrides: Partial<Dataset> = {}): Dataset => {
  const id = crypto.randomUUID()
  const name = `E2EDatasetName-${TEST_ENV}-${id}`
  return {
    id: id,
    name: name,
    description: 'Dataset Description',
    contact: { id: `test-contact-${crypto.randomUUID()}`, firstName: 'Test', lastName: 'Contact' },
    issued: new Date().toISOString(),
    lastUpdated: new Date().toISOString(),
    dataspace: { id: `test-dataspace-${crypto.randomUUID()}`, name: 'Test Dataspace' },
    access: true,
    status: 'draft',
    ...overrides,
  }
}
