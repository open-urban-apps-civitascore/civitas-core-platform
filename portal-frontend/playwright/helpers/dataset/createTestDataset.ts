import { TEST_PASSWORD, TEST_USERNAME } from '../../../playwright.config'
import { ApiClient } from '../api/apiClient'
import { getMockDatasetData } from './datasetFactory'

export type TestDataset = { id: string; name: string; description: string }

/** Creates a dataset through the API (APISIX), as the E2E user, like the portal itself does. */
export const createTestDataset = async (overrides: Partial<Pick<TestDataset, 'name' | 'description'>> = {}) => {
  const { name, description } = { ...getMockDatasetData(), ...overrides }
  const api = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
  const created = await api.createDataset({ name, description })
  return { id: created.id, name, description } satisfies TestDataset
}
