import { TEST_ENV } from '../../../playwright.config'

/** Unique name and description for a test dataset. The description is limited to 150 characters. */
export const getMockDatasetData = (overrides: Partial<{ name: string; description: string }> = {}) => {
  const id = crypto.randomUUID()
  return {
    name: `E2EDatasetName-${TEST_ENV}-${id}`,
    description: 'Dataset Description',
    ...overrides,
  }
}
