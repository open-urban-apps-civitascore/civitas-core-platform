import { describe, expect, it } from 'vitest'

import mockedDatasetResponse from '@/__mocks__/datasets/datasetsResponse.json'
import { mappedDatasets } from '@/__mocks__/datasets/mappedDatasets.mock'

import { DatasetResponse, mapDatasetsToListData } from './page'

describe('mapDatasets', () => {
  it('maps the datasets response to the correct structure', async () => {
    expect(mapDatasetsToListData(mockedDatasetResponse as DatasetResponse[])).toEqual(mappedDatasets)
  })
})
