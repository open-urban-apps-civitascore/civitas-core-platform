import { describe, expect, it, vi } from 'vitest'

import type {
  Datastructure,
  DatastructuresListData,
  DatastructureVersion,
  DatastructureVersionFormData,
  DatastructureVersionSummary,
} from '@/types/datastructures'

import {
  mapDatastructuresApiToListData,
  mapDatastructureVersionApiToFormData,
  mapDatastructureVersionFormToApiData,
  mapDatastructureVersionsApiToListData,
  parseDatastructureVersionFormData,
} from './datastructures'

type Version = Datastructure['dataStructureVersions'][number]

const createVersion = (versionNumber: string): Version => ({
  id: versionNumber,
  version: versionNumber,
  description: `Test Description ${versionNumber}`,
  dataStructureVersionStatus: 'DRAFT',
  dataStructureVersionSource: 'OWN',
  createdAt: new Date().toISOString(),
  modifiedAt: new Date().toISOString(),
})

const createDatastructure = (versions: string[], datastructureId = 'ds1'): Datastructure => ({
  id: datastructureId,
  name: `Datastructure ${datastructureId}`,
  description: 'Datastructure Description',
  createdFromDataSource: false,
  dataStructureStatus: 'DRAFT',
  inUse: false,
  dataStructureVersions: versions.map(version => createVersion(version)),
  assignments: [],
  createdAt: new Date().toISOString(),
  modifiedAt: new Date().toISOString(),
})

const createVersionSummary = (overrides?: Partial<DatastructureVersionSummary>): DatastructureVersionSummary => ({
  id: 'v1',
  version: '1.0',
  description: 'Version Description',
  dataStructureVersionStatus: 'DRAFT',
  dataStructureVersionSource: 'OWN',
  createdAt: new Date().toISOString(),
  modifiedAt: new Date().toISOString(),
  ...overrides,
})

const createVersionDetail = (overrides?: Partial<DatastructureVersion>): DatastructureVersion => ({
  id: 'version-id',
  version: '1.0',
  description: 'Version Description',
  dataStructureVersionStatus: 'DRAFT',
  dataStructureVersionSource: 'OWN',
  modelAtlasUri: 'atlas://model',
  modelName: 'Test Model',
  model: '<xmi/>',
  styles: {
    id: 'diagram-id',
    name: 'Diagram Name',
    nodes: [],
    edges: [],
    lastModified: new Date(),
    isDirty: false,
  },
  dataStructure: {
    id: 'ds-id',
    name: 'Datastructure',
  },
  createdAt: new Date().toISOString(),
  modifiedAt: new Date().toISOString(),
  ...overrides,
})

const createVersionFormData = (overrides?: Partial<DatastructureVersionFormData>): DatastructureVersionFormData => ({
  id: 'version-id',
  version: '1.0',
  description: 'Version Description',
  dataStructureVersionStatus: 'DRAFT',
  dataStructureVersionSource: 'OWN',
  modelAtlasUri: 'atlas://model',
  modelName: 'Test Model',
  nodes: [],
  edges: [],
  ...overrides,
})

describe('mapDatastructuresApiToListData', () => {
  it('maps basic fields correctly', () => {
    const datastructure: Datastructure = createDatastructure(['1.0'])

    const result = mapDatastructuresApiToListData([datastructure])

    expect(result).toHaveLength(1)
    expect(result[0]).toMatchObject({
      id: datastructure.id,
      dataStructureId: datastructure.id,
      name: datastructure.name,
      description: datastructure.description,
      status: datastructure.dataStructureStatus,
      source: datastructure.dataStructureVersions[0].dataStructureVersionSource,
      versionNumber: '1.0',
      inUse: datastructure.inUse,
    })
  })

  it('maps versions correctly', () => {
    const datastructure: Datastructure = createDatastructure(['1.0'])

    const { dataStructureVersions } = datastructure
    const expectedVersions = [{ ...dataStructureVersions[0], name: `Version ${dataStructureVersions[0].version}` }]
    const expectedResult: DatastructuresListData = {
      id: datastructure.id,
      name: datastructure.name,
      description: datastructure.description as string,
      source: expectedVersions[0].dataStructureVersionSource,
      status: datastructure.dataStructureStatus,
      versionNumber: expectedVersions[0].version,
      versions: [
        {
          id: expectedVersions[0].id,
          name: `Version ${expectedVersions[0].version}`,
          description: expectedVersions[0].description as string,
          source: expectedVersions[0].dataStructureVersionSource,
          status: expectedVersions[0].dataStructureVersionStatus,
          versionNumber: expectedVersions[0].version,
          versions: [],
        },
      ],
    }

    const result = mapDatastructuresApiToListData([datastructure])

    expect(result).toHaveLength(1)
    expect(result[0]).toMatchObject(expectedResult)
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
    expect(result[0].versions).toEqual([])
  })
})

describe('mapDatastructureVersionsApiToListData', () => {
  it('maps version summaries to list rows', () => {
    const versions = [createVersionSummary()]

    const result = mapDatastructureVersionsApiToListData(versions)

    expect(result).toEqual([
      {
        id: versions[0].id,
        versionNumber: versions[0].version,
        name: `Version ${versions[0].version}`,
        description: versions[0].description,
        status: versions[0].dataStructureVersionStatus,
        source: versions[0].dataStructureVersionSource,
      },
    ])
  })

  it('falls back to "-" when description is null', () => {
    const versions = [createVersionSummary({ description: null })]

    const result = mapDatastructureVersionsApiToListData(versions)

    expect(result[0].description).toBe('-')
  })
})

describe('mapDatastructureVersionApiToFormData', () => {
  it('maps api version details to form data', () => {
    const version = createVersionDetail()

    const result = mapDatastructureVersionApiToFormData(version)

    expect(result).toEqual({
      id: version.id,
      version: version.version,
      description: version.description,
      dataStructureVersionStatus: version.dataStructureVersionStatus,
      dataStructureVersionSource: version.dataStructureVersionSource,
      modelAtlasUri: version.modelAtlasUri,
      modelName: version.modelName,
      nodes: version.styles?.nodes ?? [],
      edges: version.styles?.edges ?? [],
    })
  })

  it('maps null description and styles to safe defaults', () => {
    const version = createVersionDetail({ description: null, styles: null })

    const result = mapDatastructureVersionApiToFormData(version)

    expect(result.description).toBe('')
    expect(result.nodes).toEqual([])
    expect(result.edges).toEqual([])
  })
})

describe('mapDatastructureVersionFormToApiData', () => {
  it('maps form data and diagram/model payload to put data', () => {
    const formData = createVersionFormData()
    const diagram = createVersionDetail().styles
    const model = '<uml-model/>'

    const result = mapDatastructureVersionFormToApiData(formData, diagram, model)

    expect(result).toEqual({
      id: formData.id,
      version: formData.version,
      description: formData.description,
      dataStructureVersionSource: formData.dataStructureVersionSource,
      dataStructureVersionStatus: formData.dataStructureVersionStatus,
      modelAtlasUri: formData.modelAtlasUri,
      modelName: formData.modelName,
      model,
      styles: diagram,
    })
  })
})

describe('parseDatastructureVersionFormData', () => {
  it('returns parsed draft values', () => {
    const values = createVersionFormData({ version: ' 1.0 ', description: ' some description ' })

    const result = parseDatastructureVersionFormData(values, true)

    expect(result).toBeDefined()
    expect(result?.version).toBe('1.0')
    expect(result?.description).toBe('some description')
  })

  it('returns parsed available values when required fields are present', () => {
    const values = createVersionFormData({
      description: ' valid description ',
      modelAtlasUri: ' atlas://valid-model ',
      modelName: ' Valid Model ',
      nodes: [
        {
          id: 'node-1',
          type: 'class',
          position: { x: 0, y: 0 },
          data: {
            element: {
              id: 'element-1',
              type: 'class',
              name: 'Customer',
              attributes: [],
              operations: [],
            },
            label: 'Customer',
          },
        },
      ],
    })

    const result = parseDatastructureVersionFormData(values, false)

    expect(result).toBeDefined()
    expect(result?.description).toBe('valid description')
    expect(result?.modelAtlasUri).toBe('atlas://valid-model')
    expect(result?.modelName).toBe('Valid Model')
    expect(result?.nodes).toHaveLength(1)
  })

  it('returns undefined and logs error for invalid available values', () => {
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    try {
      const values = createVersionFormData({
        description: '',
        modelAtlasUri: null,
        modelName: null,
        nodes: [],
      })

      const result = parseDatastructureVersionFormData(values, false)

      expect(result).toBeUndefined()
      expect(consoleErrorSpy).toHaveBeenCalled()
    } finally {
      consoleErrorSpy.mockRestore()
    }
  })
})
