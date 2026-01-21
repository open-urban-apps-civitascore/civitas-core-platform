// mapDatasetToFormData.test.ts
import { describe, expect, it } from 'vitest'

import { type Dataset, DATASET_STATUS } from '@/types/datasets'

import { mapDatasetToFormData } from './mappers'

const dataset: Dataset = {
  id: 'd1',
  name: 'Dataset 1',
  description: 'Test description',
  contact: null,
  issued: '2024-01-01',
  lastUpdated: '2024-01-02',
  access: true,
  status: DATASET_STATUS.DRAFT,
  dataspace: { id: 'ds1', name: 'dataspace1' },
  tags: ['tag1', 'tag2'],
}

describe('mapDatasetToFormData', () => {
  it('maps dataset fields to form data', () => {
    const result = mapDatasetToFormData(dataset)

    expect(result).toEqual({
      id: 'd1',
      name: 'Dataset 1',
      dataspace: 'ds1',
      description: 'Test description',
      tags: ['tag1', 'tag2'],
    })
  })

  it('maps empty dataspace id when dataspace is null', () => {
    const result = mapDatasetToFormData({ ...dataset, dataspace: null })

    expect(result.dataspace).toBe('')
  })
})
