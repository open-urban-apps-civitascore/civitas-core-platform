import { TEST_PASSWORD, TEST_USERNAME } from '../../../playwright.config'
import { ApiClient } from '../api/apiClient'

export const removeTestDataset = async (datasetId: string) => {
  try {
    const api = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
    await api.deleteDataset(datasetId)
  } catch (error) {
    console.error('Failed to delete dataset', datasetId, error)
  }
}

/** For datasets that a spec created through the UI and so does not know the id of. */
export const removeTestDatasetsByName = async (name: string) => {
  try {
    const api = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
    const { content } = await api.getDatasets(`q=${encodeURIComponent(name)}&size=50`)
    for (const dataset of content.filter(d => d.name === name)) {
      await api.deleteDataset(dataset.id)
    }
  } catch (error) {
    console.error('Failed to delete datasets named', name, error)
  }
}
