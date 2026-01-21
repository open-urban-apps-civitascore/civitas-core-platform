// mapDataspacesToFormData.test.ts
import { describe, expect, it } from 'vitest'

import type { DataSpace, DataSpaceFormData } from '@/types/dataspaces'

import { mapDataspacesToFormData } from './mappers'

const dataspace: DataSpace = {
  id: 'ds1',
  name: 'Dataspace 01',
  description: 'Test description',
  protected: true,
}

describe('mapDataspacesToFormData', () => {
  it('maps dataspace fields to form data', () => {
    const result = mapDataspacesToFormData(dataspace)

    const expected: DataSpaceFormData = {
      name: 'Dataspace 01',
      description: 'Test description',
      protected: true,
    }

    expect(result).toEqual(expected)
  })
})
