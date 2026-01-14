import { DatasetResponse } from '@/types/datasets'

export const mapDatasetToFormData = (dataset: DatasetResponse) => {
  return {
    id: dataset.id,
    name: dataset.name,
    dataspace: dataset.dataspace?.id || '',
    description: dataset.description,
    tags: dataset.tags,
  }
}
