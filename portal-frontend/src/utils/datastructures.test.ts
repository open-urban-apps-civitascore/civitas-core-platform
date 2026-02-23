import { describe, expect, it } from 'vitest'

import type { Datastructure } from '@/types/datastructures'

import { mapDatastructuresApiToListData } from './datastructures'

type Version = Datastructure['versions'][number]

const createVersion = (versionNumber: string): Version => ({
  id: versionNumber,
  description: `Test Description ${versionNumber}`,
  status: 'DRAFT',
  versionNumber,
  source: 'OWN',
})

const createDatastructure = (versions: string[], datastructureId = 'ds1'): Datastructure => ({
  id: datastructureId,
  name: `Datastructure ${datastructureId}`,
  description: 'Datastructure Description',
  source: 'OWN',
  status: 'DRAFT',
  inUse: false,
  versions: versions.map(version => createVersion(version)),
})

describe('mapDatastructuresApiToListData', () => {
  it('maps basic fields correctly', () => {
    const datastructure: Datastructure = createDatastructure(['1.0'])

    const result = mapDatastructuresApiToListData([datastructure])

    expect(result).toHaveLength(1)
    // eslint-disable-next-line unused-imports/no-unused-vars
    const { versions, inUse, ...expectedResult } = datastructure
    expect(result[0]).toMatchObject({ ...expectedResult, versionNumber: '1.0' })
  })

  it('maps versions correctly', () => {
    const datastructure: Datastructure = createDatastructure(['1.0'])

    const result = mapDatastructuresApiToListData([datastructure])

    expect(result).toHaveLength(1)
    // eslint-disable-next-line unused-imports/no-unused-vars
    const { versions, inUse, ...expectedResult } = datastructure
    const expectedVersions = [{ ...versions[0], name: `Version ${versions[0].versionNumber}` }]
    expect(result[0]).toMatchObject({ ...expectedResult, versionNumber: '1.0', versions: expectedVersions })
  })

  it('selects the highest numeric version', () => {
    const datastructure: Datastructure = createDatastructure(['1.0', '1.1', '2.0'])

    const result = mapDatastructuresApiToListData([datastructure])

    expect(result[0].versionNumber).toBe('2.0')
  })

  it('adds empty versions array to each version', () => {
    const datastructure: Datastructure = createDatastructure(['1.0'])

    const result = mapDatastructuresApiToListData([datastructure])

    expect(result[0].versions).toHaveLength(1)
    result[0].versions.forEach(version => {
      expect(version.versions).toEqual([])
    })
  })

  it('handles multiple datastructures', () => {
    const datastructures: Datastructure[] = [
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
    const datastructure: Datastructure = createDatastructure([])

    const result = mapDatastructuresApiToListData([datastructure])

    expect(result).toHaveLength(1)
    expect(result[0].versionNumber).toBeNull()
    expect(result[0].source).toBeNull()
    expect(result[0].versions).toEqual([])
  })
})
