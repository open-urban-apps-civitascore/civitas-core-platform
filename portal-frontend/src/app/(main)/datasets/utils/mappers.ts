import { Dataset } from '@/types/datasets'

export const mapDatasetToFormData = (dataset: Dataset) => {
  return {
    id: dataset.id,
    name: dataset.name,
    description: dataset.description ?? '',
    openDataAccess: dataset.openDataAccess,
    dataSetStatus: dataset.dataSetStatus,
  }
}
