import { Dataset } from '@/types/datasets'

export const mapDatasetToFormData = (dataset: Dataset) => {
  return {
    id: dataset.id,
    name: dataset.name,
    dataspace: dataset.dataspace?.id || '',
    description: dataset.description,
    tags: dataset.tags,
  }
}
