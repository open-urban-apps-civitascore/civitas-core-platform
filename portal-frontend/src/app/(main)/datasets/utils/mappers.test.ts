// mapDatasetToFormData.test.ts
import { describe, expect, it } from 'vitest'

import { type Dataset, DATASET_STATUS_TYPES } from '@/types/datasets'

import { mapDatasetToFormData } from './mappers'

const dataset: Dataset = {
  id: 'd1',
  name: 'Dataset 1',
  description: 'Test description',
  createdAt: '2024-01-01T00:00:00Z',
  modifiedAt: '2024-01-02T00:00:00Z',
  openDataAccess: true,
  dataSetStatus: DATASET_STATUS_TYPES.DRAFT,
  pipelines: [],
}

describe('mapDatasetToFormData', () => {
  it('maps dataset fields to form data', () => {
    const result = mapDatasetToFormData(dataset)

    expect(result).toEqual({
      id: 'd1',
      name: 'Dataset 1',
      description: 'Test description',
      openDataAccess: true,
      dataSetStatus: DATASET_STATUS_TYPES.DRAFT,
    })
  })

  it('handles empty descriptions', () => {
    const result = mapDatasetToFormData({ ...dataset, description: '' })

    expect(result.description).toBe('')
  })
})
