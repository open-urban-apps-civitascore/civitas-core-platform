import { assert, describe, expect, it } from 'vitest'

import { buildUMLModelPayload } from '@/components/uml-modeler/services/modelUploadService'
import type { UMLDiagram } from '@/components/uml-modeler/types/diagram'
import { datastructureFixtures } from '@/test-support/datastructureFixtures'
import type {
  Datastructure,
  DatastructuresListData,
  DatastructureVersion,
  DatastructureVersionFormData,
  DatastructureVersionSummary,
} from '@/types/datastructures'
import { DATASTRUCTURE_STATUS_TYPES, DATASTRUCTURE_VERSION_SOURCE } from '@/types/datastructures'

import {
  buildSessionFromVersion,
  getDatastructureFieldOptions,
  mapDatastructuresApiToListData,
  mapDatastructureVersionApiToFormData,
  mapDatastructureVersionFormToApiData,
  mapDatastructureVersionsApiToListData,
  parseDatastructureVersionFormData,
} from './datastructures'

type Version = Datastructure['dataStructureVersions'][number]

const createVersion = (versionNumber: string, dataStructureId = 'ds1'): Version => ({
  id: versionNumber,
  version: versionNumber,
  description: `Test Description ${versionNumber}`,
  dataStructureVersionStatus: 'DRAFT',
  dataStructureVersionSource: 'OWN',
  dataStructureId,
  createdAt: new Date().toISOString(),
  modifiedAt: new Date().toISOString(),
})

const createDatastructure = (versions: string[], datastructureId = 'ds1'): Datastructure => ({
  id: datastructureId,
  name: `Datastructure ${datastructureId}`,
  description: 'Datastructure Description',
  inUse: false,
  dataStructureVersions: versions.map(version => createVersion(version, datastructureId)),
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
  dataStructureId: 'ds1',
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
  modelName: 'Test Model',
  model: { $id: 'http://civitas.org/model/test', type: 'object', properties: {} },
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
  version: '1.0.0',
  description: 'Version Description',
  dataStructureVersionStatus: 'DRAFT',
  dataStructureVersionSource: 'OWN',
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
      status: null,
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
      status: null,
      versionNumber: expectedVersions[0].version,
      versionCountByStatus: { AVAILABLE: 0, DRAFT: 1 },
      inUseByReleasedVersionCount: 0,
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

  it('carries the released-referrer flag onto the structure row and each version row', () => {
    const datastructure: Datastructure = createDatastructure(['1.0', '2.0'])
    datastructure.inUseByReleased = true
    datastructure.dataStructureVersions[0].inUseByReleased = true

    const result = mapDatastructuresApiToListData([datastructure])
    const byVersion = Object.fromEntries(result[0].versions.map(version => [version.versionNumber, version]))

    expect(result[0].inUseByReleased).toBe(true)
    expect(byVersion['1.0'].inUseByReleased).toBe(true)
    expect(byVersion['2.0'].inUseByReleased).toBeUndefined()
  })

  it('counts the versions of each status on the structure row', () => {
    const datastructure: Datastructure = createDatastructure(['1.0', '2.0', '3.0'])
    datastructure.dataStructureVersions[0].dataStructureVersionStatus = DATASTRUCTURE_STATUS_TYPES.AVAILABLE
    datastructure.dataStructureVersions[1].dataStructureVersionStatus = DATASTRUCTURE_STATUS_TYPES.AVAILABLE

    const result = mapDatastructuresApiToListData([datastructure])

    expect(result[0].versionCountByStatus).toEqual({ AVAILABLE: 2, DRAFT: 1 })
  })

  it('counts only the versions that a released entity uses', () => {
    const datastructure: Datastructure = createDatastructure(['1.0', '2.0', '3.0'])
    datastructure.dataStructureVersions[0].inUse = true
    datastructure.dataStructureVersions[0].inUseByReleased = true
    datastructure.dataStructureVersions[1].inUse = true
    datastructure.dataStructureVersions[1].inUseByReleased = false

    const result = mapDatastructuresApiToListData([datastructure])

    expect(result[0].inUseByReleasedVersionCount).toBe(1)
  })

  it('sets zero counts on a structure without versions', () => {
    const datastructure: Datastructure = createDatastructure([])

    const result = mapDatastructuresApiToListData([datastructure])

    expect(result[0].versionCountByStatus).toEqual({ AVAILABLE: 0, DRAFT: 0 })
    expect(result[0].inUseByReleasedVersionCount).toBe(0)
  })

  it('sets no counts on the version rows', () => {
    const datastructure: Datastructure = createDatastructure(['1.0'])

    const result = mapDatastructuresApiToListData([datastructure])

    expect(result[0].versions[0].versionCountByStatus).toBeUndefined()
    expect(result[0].versions[0].inUseByReleasedVersionCount).toBeUndefined()
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

  it('sorts the versions subrows by version number, highest first', () => {
    const datastructure: Datastructure = createDatastructure(['1.0', '2.0', '1.1'])

    const result = mapDatastructuresApiToListData([datastructure])

    expect(result[0].versions.map(version => version.versionNumber)).toEqual(['2.0', '1.1', '1.0'])
  })

  it('puts a version subrow with no stored model on top of the numbered ones', () => {
    const datastructure: Datastructure = createDatastructure(['1.0', '2.0'])
    datastructure.dataStructureVersions.push(createVersionSummary({ id: 'draft', version: null }))

    const result = mapDatastructuresApiToListData([datastructure])

    expect(result[0].versions.map(version => version.id)).toEqual(['draft', '2.0', '1.0'])
  })
})

describe('a version with no stored model', () => {
  it('has no number to list, and is labelled rather than named after one', () => {
    const noModel = createVersionSummary({ version: null })

    const [row] = mapDatastructureVersionsApiToListData([noModel])

    expect(row.versionNumber).toBeNull()
    expect(row.name).toBe('-')
  })

  it('is skipped when picking the number a datastructure shows', () => {
    const structure = createDatastructure(['1.0.0'])
    structure.dataStructureVersions.push(createVersionSummary({ id: 'no-model', version: null }))

    const [row] = mapDatastructuresApiToListData([structure])

    expect(row.versionNumber).toBe('1.0.0')
  })

  it('maps to an empty version on the form, so the placeholder shows', () => {
    const form = mapDatastructureVersionApiToFormData(createVersionDetail({ version: null }))

    expect(form.version).toBe('')
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

  it('sorts by version number, highest first, regardless of input order', () => {
    const low = createVersionSummary({ id: 'v1', version: '1.0' })
    const high = createVersionSummary({ id: 'v3', version: '2.0' })
    const mid = createVersionSummary({ id: 'v2', version: '1.1' })

    const result = mapDatastructureVersionsApiToListData([low, high, mid])

    expect(result.map(version => version.id)).toEqual(['v3', 'v2', 'v1'])
  })

  it('sorts numerically rather than lexicographically', () => {
    const v2 = createVersionSummary({ id: 'v2', version: '1.2.0' })
    const v10 = createVersionSummary({ id: 'v10', version: '1.10.0' })

    const result = mapDatastructureVersionsApiToListData([v2, v10])

    expect(result.map(version => version.id)).toEqual(['v10', 'v2'])
  })

  it('puts a version with no stored model on top, ahead of every numbered version', () => {
    const draft = createVersionSummary({ id: 'draft', version: null, createdAt: '2024-01-01T00:00:00.000Z' })
    const numbered = createVersionSummary({ id: 'v1', version: '2.0', createdAt: '2025-01-01T00:00:00.000Z' })

    const result = mapDatastructureVersionsApiToListData([numbered, draft])

    expect(result.map(version => version.id)).toEqual(['draft', 'v1'])
  })

  it('orders versions with no stored model by createdAt, newest first', () => {
    const olderDraft = createVersionSummary({ id: 'draft1', version: null, createdAt: '2024-01-01T00:00:00.000Z' })
    const newerDraft = createVersionSummary({ id: 'draft2', version: null, createdAt: '2024-02-01T00:00:00.000Z' })

    const result = mapDatastructureVersionsApiToListData([olderDraft, newerDraft])

    expect(result.map(version => version.id)).toEqual(['draft2', 'draft1'])
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
    const model = { $id: 'http://civitas.org/model/test', type: 'object', properties: {} }

    const result = mapDatastructureVersionFormToApiData(formData, diagram, model)

    // The registry owns the version, so the client does not send one.
    expect(result).toEqual({
      id: formData.id,
      description: formData.description,
      dataStructureVersionStatus: formData.dataStructureVersionStatus,
      modelName: formData.modelName,
      model,
      styles: diagram,
      importedStructureUrns: [],
    })
  })

  it('records the published structures the diagram was built from', () => {
    const formData = createVersionFormData()
    const imported = [
      {
        urn: 'urn:core:platform:civitas:datastructure:frost:9cc6ed95-0f8b-8ace-ab6e-83cdee87b3dd:1.0.0',
        name: 'ThingTree',
      },
      {
        urn: 'urn:core:platform:civitas:datastructure:frost:c415dee9-6cad-8a43-9ad3-1e8b3903c57a:2.1.0',
        name: 'Things',
      },
    ]
    const diagram = { ...createVersionDetail().styles!, importedStructures: imported }

    const result = mapDatastructureVersionFormToApiData(formData, diagram, null)

    // Derived from the diagram, like the model document — the pin carries the version, because a
    // later version of a structure must leave this one untouched.
    expect(result.importedStructureUrns).toEqual(imported.map(structure => structure.urn))
  })

  it('keeps the stored pins when the save carries no diagram', () => {
    const pinned = ['urn:core:platform:civitas:datastructure:frost:9cc6ed95-0f8b-8ace-ab6e-83cdee87b3dd:1.0.0']

    const result = mapDatastructureVersionFormToApiData(createVersionFormData(), null, null, pinned)

    expect(result.importedStructureUrns).toEqual(pinned)
  })
})

describe('parseDatastructureVersionFormData', () => {
  it('uses draft schema in draft mode and returns success with data', () => {
    const values = createVersionFormData({
      description: '',
      modelName: null,
      nodes: [],
    })

    const result = parseDatastructureVersionFormData(values, true)

    expect(result.success).toBe(true)
    assert(result.success)
    expect(result.data).toBeDefined()
  })

  it('uses available schema in non-draft mode and returns success with data', () => {
    const values = createVersionFormData({
      description: 'valid description',
      modelName: 'Valid Model',
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

    expect(result.success).toBe(true)
    assert(result.success)
    expect(result.data).toBeDefined()
  })

  it('returns error when available schema validation fails', () => {
    const values = createVersionFormData({
      description: '',
      modelName: null,
      nodes: [],
    })

    const result = parseDatastructureVersionFormData(values, false)

    expect(result.success).toBe(false)
    assert(!result.success)
    expect(result.error).toBeDefined()
  })
})

describe('buildSessionFromVersion', () => {
  it('draws the model when nobody drew a diagram', () => {
    // A version created over the API, or generated from a Data source, carries a model and no
    // diagram. Its content is right there, so the canvas must not open empty.
    const version = createVersionDetail({
      modelName: 'ThingsPortTarget',
      styles: null,
      model: {
        $schema: 'https://json-schema.org/draft/2020-12/schema',
        title: 'ThingsPortTarget',
        type: 'object',
        properties: {
          name: { type: 'string' },
          properties: {
            type: 'object',
            properties: { reference: { type: 'string', 'x-core-primaryKey': true } },
            required: ['reference'],
          },
        },
        required: ['name', 'properties'],
      },
    })

    const result = buildSessionFromVersion(version)

    expect(result.diagram.nodes.map(node => node.data.element.name)).toEqual([
      'ThingsPortTarget',
      'ThingsPortTargetProperties',
    ])
    // Reading a stored model is not a change the modeller made.
    expect(result.isDirty).toBe(false)
  })

  it('keeps the drawing when there is one', () => {
    const drawn = {
      id: 'diagram-id',
      name: 'Drawn',
      nodes: [
        {
          id: 'n1',
          type: 'class',
          position: { x: 5, y: 5 },
          data: { element: { id: 'n1', name: 'Drawn', type: 'class', attributes: [], operations: [] }, label: 'Drawn' },
        },
      ],
      edges: [],
      lastModified: new Date('2024-02-01T10:00:00.000Z'),
      isDirty: false,
    }
    const version = createVersionDetail({ modelName: 'Drawn', styles: drawn as never })

    // The drawing carries the positions the modeller chose; a derived layout would move them.
    expect(buildSessionFromVersion(version).diagram.nodes).toHaveLength(1)
  })

  it('reads the retired Json type of a stored drawing as String, in the session and in the form', () => {
    const element = {
      id: 'n1',
      name: 'Observation',
      type: 'class',
      attributes: [{ id: 'a1', name: 'resultQuality', type: 'Json' }],
      operations: [
        { id: 'o1', name: 'quality', returnType: 'Json', parameters: [{ id: 'p1', name: 'raw', type: 'Json' }] },
      ],
    }
    const drawn = {
      id: 'diagram-id',
      name: 'Drawn',
      nodes: [{ id: 'n1', type: 'class', position: { x: 5, y: 5 }, data: { element, label: 'Observation' } }],
      edges: [],
    }
    const version = createVersionDetail({ styles: drawn as never })

    for (const node of [
      buildSessionFromVersion(version).diagram.nodes[0],
      mapDatastructureVersionApiToFormData(version).nodes[0],
    ]) {
      expect(node.data.element).toMatchObject({
        attributes: [{ name: 'resultQuality', type: 'String' }],
        operations: [{ returnType: 'String', parameters: [{ type: 'String' }] }],
      })
    }
    // The stored data changes on the next save, not on reading it.
    expect(element.attributes[0].type).toBe('Json')
  })

  it('leaves the canvas empty for a model it cannot read', () => {
    const version = createVersionDetail({
      modelName: 'Alien',
      styles: null,
      model: { $defs: { Thing: { type: 'object', title: 'Thing', properties: { when: { type: 'timestamp' } } } } },
    })

    expect(buildSessionFromVersion(version).diagram.nodes).toHaveLength(0)
  })

  it('prefers explicit sessionId and modelName over diagram values', () => {
    const created = new Date('2024-01-01T10:00:00.000Z')
    const version = createVersionDetail({
      modelName: 'Canonical Model Name',
      styles: {
        id: 'diagram-id',
        name: 'Diagram Label',
        nodes: [],
        edges: [],
        lastModified: new Date('2024-02-01T10:00:00.000Z'),
        isDirty: true,
      },
    })

    const result = buildSessionFromVersion(version, 'session-123', created)

    expect(result.id).toBe('session-123')
    expect(result.name).toBe('Canonical Model Name')
    expect(result.diagram).toEqual(version.styles)
    expect(result.diagram).not.toBe(version.styles)
    expect(result.isDirty).toBe(false)
    expect(result.dirtyFields).toEqual(new Set())
    expect(result.lastModified).toEqual(version.styles?.lastModified)
    expect(result.created).toBe(created)
  })

  it('uses diagram id and lastModified when no explicit values are provided', () => {
    const lastModified = new Date('2024-03-01T12:00:00.000Z')
    const version = createVersionDetail({
      modelName: null,
      styles: {
        id: 'diagram-456',
        name: 'Diagram Name',
        nodes: [],
        edges: [],
        lastModified,
        isDirty: true,
      },
    })

    const result = buildSessionFromVersion(version)

    expect(result.id).toBe('diagram-456')
    expect(result.name).toBe('Diagram Name')
    expect(result.lastModified).toBe(lastModified)
    expect(result.created).toBe(lastModified)
  })

  it('falls back to an empty clean session when version data is missing', () => {
    const result = buildSessionFromVersion(null)

    expect(result.id).toBeTruthy()
    expect(result.name).toBe('Untitled Diagram')
    expect(result.diagram.name).toBe('Untitled Diagram')
    expect(result.diagram.nodes).toEqual([])
    expect(result.diagram.edges).toEqual([])
    expect(result.isDirty).toBe(false)
    expect(result.dirtyFields).toEqual(new Set())
    expect(result.lastModified).toBeInstanceOf(Date)
    expect(result.created).toBeInstanceOf(Date)
  })
})

describe('root designation round-trip through persisted styles', () => {
  it('keeps the isRoot flag across save payload and session rebuild', () => {
    const flagged = {
      id: 'node-a',
      type: 'class',
      position: { x: 0, y: 0 },
      data: {
        element: { id: 'a', name: 'Alpha', type: 'class', isRoot: true, attributes: [], operations: [] },
        label: 'Alpha',
      },
    }
    const other = {
      id: 'node-b',
      type: 'class',
      position: { x: 0, y: 0 },
      data: {
        element: { id: 'b', name: 'Beta', type: 'class', attributes: [], operations: [] },
        label: 'Beta',
      },
    }
    const diagram = {
      id: 'diagram-1',
      name: 'Struct',
      nodes: [flagged, other],
      edges: [],
      lastModified: new Date(0),
      isDirty: false,
    } as unknown as UMLDiagram

    const payload = mapDatastructureVersionFormToApiData(
      {
        id: 'v1',
        version: '1.0.0',
        description: '',
        dataStructureVersionSource: DATASTRUCTURE_VERSION_SOURCE.OWN,
        dataStructureVersionStatus: DATASTRUCTURE_STATUS_TYPES.DRAFT,
        modelName: 'Struct',
        nodes: diagram.nodes,
        edges: diagram.edges,
      },
      diagram,
      null,
    )
    // Serialize/deserialize like the persistence boundary does — a designation dropped by a
    // structured clone or JSON round-trip would silently degrade the diagram to ambiguous.
    const persisted = JSON.parse(JSON.stringify(payload)) as { styles: UMLDiagram }
    const session = buildSessionFromVersion({ styles: persisted.styles, modelName: 'Struct' } as DatastructureVersion)

    const rebuilt = session.diagram.nodes.map(node => node.data.element)
    expect(rebuilt.map(element => element.isRoot)).toEqual([true, undefined])
  })
})

describe('getDatastructureFieldOptions', () => {
  const classNode = (id: string, name: string, attributes: string[], isRoot?: boolean) => ({
    id: `node-${id}`,
    type: 'class',
    position: { x: 0, y: 0 },
    data: {
      element: {
        id,
        name,
        type: 'class',
        isRoot,
        attributes: attributes.map(attribute => ({ id: `${id}-${attribute}`, name: attribute, type: 'String' })),
        operations: [],
      },
      label: name,
    },
  })

  const enumNode = (id: string, name: string) => ({
    id: `node-${id}`,
    type: 'enumeration',
    position: { x: 0, y: 0 },
    data: {
      element: { id, name, type: 'enumeration', literals: [{ id: `${id}-l`, name: 'LITERAL' }] },
      label: name,
    },
  })

  const diagramOf = (nodes: unknown[], edges: unknown[] = []) =>
    ({ id: 'diagram-1', name: 'Struct', nodes, edges }) as unknown as UMLDiagram

  const versionOf = (over: Partial<Pick<DatastructureVersion, 'model' | 'styles' | 'modelName'>>) =>
    ({ model: null, styles: null, modelName: 'Struct', ...over }) as DatastructureVersion

  it('takes the attributes of the root class when an enumeration comes first in the diagram', () => {
    const styles = diagramOf([enumNode('e1', 'Colour'), classNode('c1', 'Building', ['name', 'geom'])])

    const options = getDatastructureFieldOptions(versionOf({ styles }))

    expect(options).toEqual([
      { value: 'name', label: 'name' },
      { value: 'geom', label: 'geom' },
    ])
  })

  it('takes the attributes of the designated root when several classes exist', () => {
    const styles = diagramOf([classNode('c1', 'Address', ['street']), classNode('c2', 'Building', ['name'], true)])

    const options = getDatastructureFieldOptions(versionOf({ styles }))

    expect(options).toEqual([{ value: 'name', label: 'name' }])
  })

  it('prefers the persisted model over the diagram', () => {
    const model = {
      title: 'Building',
      type: 'object',
      properties: { modelField: { type: 'string' } },
    }
    const styles = diagramOf([classNode('c1', 'Building', ['diagramField'])])

    const options = getDatastructureFieldOptions(versionOf({ model, styles }))

    expect(options).toEqual([{ value: 'modelField', label: 'modelField' }])
  })

  it('returns no options for a diagram without a unique root', () => {
    const styles = diagramOf([classNode('c1', 'Address', ['street']), classNode('c2', 'Building', ['name'])])

    expect(getDatastructureFieldOptions(versionOf({ styles }))).toEqual([])
  })

  it('returns no options for an empty, absent or missing version instead of throwing', () => {
    expect(getDatastructureFieldOptions(versionOf({ styles: diagramOf([]) }))).toEqual([])
    expect(getDatastructureFieldOptions(versionOf({}))).toEqual([])
    expect(getDatastructureFieldOptions(undefined)).toEqual([])
  })
})

/**
 * The editor rehydrates from `styles`, never from the stored CORE model,
 * so the provable statement is that a rehydrated diagram exports the same model again.
 * This is rehydration, not reload: the payload comes from the test, not from the server.
 */
describe('the exported model survives a save/rehydrate round-trip', () => {
  const formOf = (): DatastructureVersionFormData =>
    ({
      id: 'v1',
      version: '1.0.0',
      description: '',
      dataStructureVersionSource: DATASTRUCTURE_VERSION_SOURCE.OWN,
      dataStructureVersionStatus: DATASTRUCTURE_STATUS_TYPES.DRAFT,
      modelName: 'Struct',
    }) as DatastructureVersionFormData

  const noEdit = (diagram: UMLDiagram) => diagram

  const roundTrip = (diagram: UMLDiagram, edit: (diagram: UMLDiagram) => UMLDiagram = noEdit) => {
    const before = buildUMLModelPayload(diagram).model
    const persisted = JSON.parse(JSON.stringify(mapDatastructureVersionFormToApiData(formOf(), diagram, before)))
    const rehydrated = buildSessionFromVersion(persisted as DatastructureVersion).diagram
    return { before, after: buildUMLModelPayload(edit(rehydrated)).model }
  }

  it.each(datastructureFixtures)('$label exports an equivalent model after rehydration', ({ diagram }) => {
    const { before, after } = roundTrip(diagram)
    expect(after).toEqual(before)
  })

  const idsOf = (model: Record<string, unknown>) =>
    Object.entries((model.$defs ?? {}) as Record<string, { $id?: string }>).map(([key, def]) => [key, def.$id])

  const refsOf = (model: Record<string, unknown>): string[] => {
    const refs: string[] = []
    const walk = (node: unknown) => {
      if (Array.isArray(node)) return node.forEach(walk)
      if (!node || typeof node !== 'object') return
      for (const [key, value] of Object.entries(node)) {
        if (key === '$ref' && typeof value === 'string') refs.push(value)
        else walk(value)
      }
    }
    walk(model.$defs)
    return refs.sort()
  }

  it.each(datastructureFixtures)('$label keeps its Element identities across the round-trip', ({ diagram }) => {
    const { before, after } = roundTrip(diagram)

    expect(idsOf(after)).toEqual(idsOf(before))
    expect(refsOf(after)).toEqual(refsOf(before))
    expect(after.$ref).toBe(before.$ref)
  })

  it('names every member by its key and every cross-reference by a local pointer', () => {
    const { before } = roundTrip(fixtureNamed('four cross-referencing elements under one root'))
    const pointer = (name: string) => `#/$defs/${name}`

    expect(idsOf(before)).toEqual([
      ['Station', undefined],
      ['Measurement', undefined],
      ['Reading', undefined],
      ['Alert', undefined],
    ])
    // Measurement appears twice: both Reading and Alert inherit it.
    expect(refsOf(before)).toEqual([
      pointer('Alert'),
      pointer('Measurement'),
      pointer('Measurement'),
      pointer('Reading'),
    ])
    expect(before.$ref).toBe(pointer('Station'))
  })

  const fixtureNamed = (label: string) => {
    const fixture = datastructureFixtures.find(candidate => candidate.label === label)
    assert(fixture, `no fixture labelled "${label}"`)
    return fixture.diagram
  }

  it('moving a node changes no part of the exported model', () => {
    const moveFirstNode = (diagram: UMLDiagram): UMLDiagram => ({
      ...diagram,
      nodes: diagram.nodes.map((node, index) => (index === 0 ? { ...node, position: { x: 512, y: 64 } } : node)),
      viewport: { x: 10, y: 20, zoom: 2 },
    })

    const { before, after } = roundTrip(fixtureNamed('composition'), moveFirstNode)
    expect(after).toEqual(before)
  })
})
