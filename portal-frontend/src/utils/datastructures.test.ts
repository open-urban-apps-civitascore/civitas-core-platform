import { describe, expect, it } from 'vitest'

import type { DatastructuresResponse } from '@/types/datastructures'

import { mapDatastructuresApiToListData } from './datastructures'

type Version = DatastructuresResponse['versions'][number]

const createVersion = (versionNumber: string, dataStructureId = 'ds1'): Version => ({
  id: versionNumber,
  dataStructureId,
  description: `Test Description ${versionNumber}`,
  status: 'DRAFT',
  versionNumber,
  createdFromDataSource: 'OWN',
})

const createDatastructure = (versions: string[], datastructureId = 'ds1'): DatastructuresResponse => ({
  id: datastructureId,
  dataStructureId: datastructureId,
  name: `Datastructure ${datastructureId}`,
  description: 'Datastructure Description',
  createdFromDataSource: 'OWN',
  status: 'DRAFT',
  inUse: false,
  versions: versions.map(version => createVersion(version, datastructureId)),
})

describe('mapDatastructuresApiToListData', () => {
  it('maps basic fields correctly', () => {
    const datastructure: DatastructuresResponse = createDatastructure(['1.0'])

    const result = mapDatastructuresApiToListData([datastructure])

    expect(result).toHaveLength(1)
    // eslint-disable-next-line unused-imports/no-unused-vars
    const { versions, inUse, ...expectedResult } = datastructure
    expect(result[0]).toMatchObject({ ...expectedResult, versionNumber: '1.0' })
  })

  it('maps versions correctly', () => {
    const datastructure: DatastructuresResponse = createDatastructure(['1.0'])

    const result = mapDatastructuresApiToListData([datastructure])

    expect(result).toHaveLength(1)
    // eslint-disable-next-line unused-imports/no-unused-vars
    const { versions, inUse, ...expectedResult } = datastructure
    const expectedVersions = [{ ...versions[0], name: `Version ${versions[0].versionNumber}` }]
    expect(result[0]).toMatchObject({ ...expectedResult, versionNumber: '1.0', versions: expectedVersions })
  })

  it('selects the highest numeric version', () => {
    const datastructure: DatastructuresResponse = createDatastructure(['1.0', '1.1', '2.0'])

    const result = mapDatastructuresApiToListData([datastructure])

    expect(result[0].versionNumber).toBe('2.0')
  })

  it('adds empty versions array to each version', () => {
    const datastructure: DatastructuresResponse = createDatastructure(['1.0'])

    const result = mapDatastructuresApiToListData([datastructure])

    expect(result[0].versions).toHaveLength(1)
    result[0].versions.forEach(version => {
      expect(version.versions).toEqual([])
    })
  })

  it('handles multiple datastructures', () => {
    const datastructures: DatastructuresResponse[] = [
      createDatastructure(['1.0', '1.1', '2.0'], 'ds1'),
      createDatastructure(['1.0', '1.1', '1.2'], 'ds2'),
    ]

    const result = mapDatastructuresApiToListData(datastructures)

    expect(result).toHaveLength(2)
    expect(result[0].id).toBe('ds1')
    expect(result[0].versionNumber).toBe('2.0')
    expect(result[1].id).toBe('ds2')
    expect(result[1].versionNumber).toBe('1.2')
  })

  it('returns null for versionNumber and source when versions array is empty', () => {
    const datastructure: DatastructuresResponse = createDatastructure([])

    const result = mapDatastructuresApiToListData([datastructure])

    expect(result).toHaveLength(1)
    expect(result[0].versionNumber).toBeNull()
    expect(result[0].createdFromDataSource).toBeNull()
    expect(result[0].versions).toEqual([])
  })
})
