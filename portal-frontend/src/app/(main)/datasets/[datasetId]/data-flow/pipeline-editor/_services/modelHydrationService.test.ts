import { describe, expect, it } from 'vitest'

import { buildDataStructureUrn } from '@/utils/urn'

import type { PipelineNodeData } from '../_types/nodes'
import type { Pipeline, PipelineNode, PipelineNodeType, PipelineOutputDTO } from '../_types/pipeline'
import {
  hydrateFromCoreModel,
  type HydrationDataSink,
  type HydrationDataSource,
  type HydrationDataStructure,
  type HydrationMappingDocument,
  mappingRefsToHydrate,
  type ModelHydrationContext,
  needsModelHydration,
} from './modelHydrationService'
import {
  buildDataSinkPayloads,
  buildMappingArtifacts,
  buildPipelineModel,
  buildPipelinePayload,
  createMappingSnapshot,
  hasMappingChanged,
  updateNodeData,
} from './payloadBuilderService'
import { createSessionFromBackendDTO } from './sessionService'

/**
 * Pins the graph the editor derives from a CORE model. The derived graph is what a save sends
 * back, so an error here does not only draw the wrong picture: it can remove the link to a data
 * source or make a second copy of a data sink.
 */

const SOURCE_URN = 'urn:core:platform:civitas:datasource:common:CounterFeed:aa11bb22cc'
const SINK_URN = 'urn:core:platform:civitas:datasink:common:TrafficTable:dd33ee44ff'
const MAPPING_URN = 'urn:core:platform:civitas:mapping:common:CountToTraffic:5566778899'
const STRUCTURE_URN = 'urn:core:platform:civitas:datastructure:common:Traffic:0011223344:1.0.0'

const versioned = (urn: string, version = '1.0.0'): string => `${urn}:${version}`

/** The default data of the node registry, which the provider supplies in the editor. */
const DEFAULT_DATA: Record<PipelineNodeType, Record<string, unknown>> = {
  start: { nodeType: 'start', label: 'Start', configured: true, description: 'Entry point' },
  end: { nodeType: 'end', label: 'End', configured: true, description: 'Exit point' },
  dataSource: { label: 'DataSource', configured: false, entityType: 'datasource' },
  cron: { label: 'Scheduled Trigger', configured: false, cronExpression: '' },
  frost: {
    label: 'Sensor Data Storage',
    configured: false,
    entityType: 'frost',
    serverName: 'Sensor Data Storage',
    serverUrl: '',
    version: '1.1',
  },
  geoPersistence: { label: 'Geospatial Data Storage', configured: false, entityType: 'persistence', tableName: '' },
  mapping: { label: 'Mapping', configured: false, mappingConfig: { fields: {}, positions: {} } },
}

const createDefaultData = (type: PipelineNodeType): PipelineNodeData =>
  structuredClone(DEFAULT_DATA[type]) as PipelineNodeData

const postgisSink: HydrationDataSink = {
  id: 'sink-1',
  dataSinkType: 'POSTGIS',
  configurationUrn: versioned(SINK_URN),
  configuration: {
    tableName: 'traffic',
    element: STRUCTURE_URN,
    dataStructureVersion: { id: 'version-1', dataStructureId: 'structure-1' },
  },
}

const frostSink: HydrationDataSink = {
  id: 'sink-1',
  dataSinkType: 'FROST',
  configurationUrn: versioned(SINK_URN),
  configuration: { port: 'ThingTree', element: STRUCTURE_URN },
}

const mqttSource: HydrationDataSource = {
  id: 'source-1',
  name: 'Counter feed',
  connectorType: 'MQTT',
  configurationUrn: versioned(SOURCE_URN),
}

const contextWith = (overrides: Partial<ModelHydrationContext> = {}): ModelHydrationContext => ({
  dataSinks: [postgisSink],
  dataSources: [mqttSource],
  createDefaultData,
  ...overrides,
})

/** The two data structures the mapping of the model maps between, as the structure list returns them. */
const COUNT_STRUCTURE: HydrationDataStructure = {
  id: '0b3c5e8a-1f2d-4c6b-9a7e-3d5f8c1b2a40',
  name: 'Counter reading',
  dataStructureVersions: [{ id: 'count-version-1', version: '1.0.0' }],
}

const TRAFFIC_STRUCTURE: HydrationDataStructure = {
  id: '7e9f1a2b-3c4d-4e5f-8a9b-0c1d2e3f4a5b',
  name: 'Traffic',
  dataStructureVersions: [
    { id: 'traffic-version-1', version: '1.0.0' },
    { id: 'traffic-version-2', version: '2.0.0' },
  ],
}

const COUNT_URN = buildDataStructureUrn(COUNT_STRUCTURE.name, COUNT_STRUCTURE.id, '1.0.0')
const TRAFFIC_URN = buildDataStructureUrn(TRAFFIC_STRUCTURE.name, TRAFFIC_STRUCTURE.id, '2.0.0')

/** The mapping of the model as the backend returns it: the registry stamps `$schema` and `id`. */
const STORED_MAPPING: HydrationMappingDocument = {
  $schema: 'https://civitasconnect.digital/core/mapping/v1',
  id: versioned(MAPPING_URN),
  title: 'Count to traffic',
  source: COUNT_URN,
  target: TRAFFIC_URN,
  fields: { '$.vehicles': { op: 'copy', sourcePath: '$.count' } },
}

const withStoredMapping = (
  document: HydrationMappingDocument = STORED_MAPPING,
  overrides: Partial<ModelHydrationContext> = {},
): ModelHydrationContext =>
  contextWith({
    mappings: new Map([[versioned(MAPPING_URN), document]]),
    dataStructures: [COUNT_STRUCTURE, TRAFFIC_STRUCTURE],
    ...overrides,
  })

/** The flow of an installed package: start, source, mapping, sink, end. */
const MODEL = {
  nodes: [
    { id: 'n-start', kind: 'start', label: 'Start', 'x-ui-position': { x: -260, y: 120 } },
    {
      id: 'n-source',
      kind: 'source',
      label: 'Counter feed',
      sourceRef: versioned(SOURCE_URN),
      'x-ui-position': { x: 0, y: 120 },
    },
    {
      id: 'n-mapping',
      kind: 'mapping',
      label: 'Count to traffic',
      mappingRef: versioned(MAPPING_URN),
      'x-ui-position': { x: 260, y: 120 },
    },
    {
      id: 'n-sink',
      kind: 'sink',
      label: 'Traffic table',
      sinkRef: versioned(SINK_URN),
      'x-ui-position': { x: 520, y: 120 },
    },
    { id: 'n-end', kind: 'end', label: 'End', 'x-ui-position': { x: 780, y: 120 } },
  ],
  edges: [
    { id: 'e1', source: 'n-start', target: 'n-source' },
    { id: 'e2', source: 'n-source', target: 'n-mapping' },
    { id: 'e3', source: 'n-mapping', target: 'n-sink' },
    { id: 'e4', source: 'n-sink', target: 'n-end' },
  ],
}

type ModelNode = Record<string, unknown>

const dtoWith = (model: { nodes: ModelNode[]; edges: ModelNode[] }, overrides: object = {}): PipelineOutputDTO =>
  ({
    id: 'pipeline-1',
    name: 'Installed pipeline',
    description: 'Came with a package',
    styles: null,
    dataSources: [],
    apis: [],
    dataSinks: [],
    dataSourceIds: ['source-1'],
    dataSinkIds: ['sink-1'],
    model,
    createdAt: '2026-09-29T10:00:00Z',
    modifiedAt: '2026-09-29T10:00:00Z',
    ...overrides,
  }) as unknown as PipelineOutputDTO

const installedPipeline = (): PipelineOutputDTO => dtoWith(structuredClone(MODEL))

const withoutNode = (nodeId: string) => MODEL.nodes.filter(node => node.id !== nodeId)

const nodeOf = (nodes: PipelineNode[], id: string): PipelineNode => {
  const node = nodes.find(candidate => candidate.id === id)
  if (!node) throw new Error(`no node '${id}' in the derived graph`)
  return node
}

const asPipeline = (dto: PipelineOutputDTO, context = contextWith()): Pipeline => ({
  id: dto.id,
  name: dto.name,
  description: dto.description,
  ...hydrateFromCoreModel(dto, context),
  viewport: { x: 0, y: 0, zoom: 1 },
  createdAt: new Date(0),
  updatedAt: new Date(0),
  isDirty: false,
})

describe('needsModelHydration', () => {
  it('is true for a pipeline with a model and no stored graph', () => {
    expect(needsModelHydration(installedPipeline())).toBe(true)
  })

  it('is false for a pipeline the editor stored a graph for', () => {
    const stored = dtoWith(structuredClone(MODEL), {
      styles: { nodes: [{ id: 'n-start' }], edges: [], nodePositions: {} },
    })
    expect(needsModelHydration(stored)).toBe(false)
  })

  it('is false when the model has no nodes', () => {
    expect(needsModelHydration(dtoWith({ nodes: [], edges: [] }))).toBe(false)
    expect(needsModelHydration(dtoWith(structuredClone(MODEL), { model: null }))).toBe(false)
  })
})

describe('mappingRefsToHydrate', () => {
  it('collects the mapping references of the pipelines without a stored graph, each one time', () => {
    const second = dtoWith(structuredClone(MODEL), { id: 'pipeline-2' })
    expect(mappingRefsToHydrate([installedPipeline(), second])).toEqual([versioned(MAPPING_URN)])
  })

  it('collects nothing for a pipeline the editor stored a graph for', () => {
    const stored = dtoWith(structuredClone(MODEL), {
      styles: { nodes: [{ id: 'n-start' }], edges: [], nodePositions: {} },
    })
    expect(mappingRefsToHydrate([stored])).toEqual([])
  })
})

describe('hydrateFromCoreModel', () => {
  describe('nodes', () => {
    it('gives each node the type the editor draws it with', () => {
      const { nodes } = hydrateFromCoreModel(installedPipeline(), contextWith())
      expect(nodes.map(node => node.type)).toEqual(['start', 'dataSource', 'mapping', 'geoPersistence', 'end'])
    })

    it('keeps the ids, the labels and the positions of the model', () => {
      const { nodes } = hydrateFromCoreModel(installedPipeline(), contextWith())
      const sink = nodeOf(nodes, 'n-sink')
      expect(sink.data.label).toBe('Traffic table')
      expect(sink.position).toEqual({ x: 520, y: 120 })
    })

    it('uses the default label of the node type for a node without a label', () => {
      const model = structuredClone(MODEL)
      delete (model.nodes[0] as ModelNode).label
      const { nodes } = hydrateFromCoreModel(dtoWith(model), contextWith())
      expect(nodeOf(nodes, 'n-start').data.label).toBe('Start')
    })

    it('puts the nodes in one row when the model has no positions', () => {
      const model = structuredClone(MODEL)
      for (const node of model.nodes) delete (node as ModelNode)['x-ui-position']
      const { nodes } = hydrateFromCoreModel(dtoWith(model), contextWith())

      const rows = new Set(nodes.map(node => node.position.y))
      const columns = nodes.map(node => node.position.x)
      expect(rows.size).toBe(1)
      expect(new Set(columns).size).toBe(nodes.length)
      expect(columns).toEqual([...columns].sort((a, b) => a - b))
    })

    it('leaves out a node of a kind the editor cannot draw, but keeps its edges', () => {
      const model = structuredClone(MODEL)
      ;(model.nodes[2] as ModelNode).kind = 'filter'
      const { nodes, edges } = hydrateFromCoreModel(dtoWith(model), contextWith())

      expect(nodes.map(node => node.id)).not.toContain('n-mapping')
      // The edges that point at the missing node make the validation refuse a save.
      expect(edges.map(edge => edge.id)).toEqual(['e1', 'e2', 'e3', 'e4'])
    })
  })

  describe('data source', () => {
    it('resolves the reference to the backend id, the name and the connector', () => {
      const { nodes } = hydrateFromCoreModel(installedPipeline(), contextWith())
      expect(nodeOf(nodes, 'n-source').data).toMatchObject({
        entityType: 'datasource',
        entityId: 'source-1',
        entityName: 'Counter feed',
        configurationUrn: versioned(SOURCE_URN),
        entityMetadata: { connector: 'MQTT' },
        configured: true,
      })
    })

    it('resolves a reference to an older version of the data source', () => {
      const newerSource = { ...mqttSource, configurationUrn: versioned(SOURCE_URN, '2.0.0') }
      const { nodes } = hydrateFromCoreModel(installedPipeline(), contextWith({ dataSources: [newerSource] }))
      const source = nodeOf(nodes, 'n-source')

      expect(source.data).toMatchObject({ entityId: 'source-1' })
      // The reference stays as stored: to open a pipeline must not change what it points at.
      expect(source.data).toMatchObject({ configurationUrn: versioned(SOURCE_URN) })
    })

    it('uses the link of the backend when the user cannot read the data source', () => {
      const { nodes } = hydrateFromCoreModel(installedPipeline(), contextWith({ dataSources: [] }))
      expect(nodeOf(nodes, 'n-source').data).toMatchObject({ entityId: 'source-1', configured: true })
    })

    it('shows the node as not configured when the backend id is not known', () => {
      const dto = dtoWith(structuredClone(MODEL), { dataSourceIds: ['source-1', 'source-2'] })
      const { nodes } = hydrateFromCoreModel(dto, contextWith({ dataSources: [] }))
      const source = nodeOf(nodes, 'n-source')

      expect(source.data.configured).toBe(false)
      expect(source.data).not.toHaveProperty('entityId')
    })
  })

  describe('data sink', () => {
    it('draws a PostGIS sink as a geo persistence node with its table and structure', () => {
      const { nodes } = hydrateFromCoreModel(installedPipeline(), contextWith())
      expect(nodeOf(nodes, 'n-sink').data).toMatchObject({
        entityType: 'persistence',
        entityId: 'sink-1',
        configurationUrn: versioned(SINK_URN),
        tableName: 'traffic',
        dataStructureVersionId: 'structure-1/version-1',
        dataStructureUrn: STRUCTURE_URN,
        configured: true,
      })
    })

    it('draws a FROST sink as a FROST node with its port', () => {
      const { nodes } = hydrateFromCoreModel(installedPipeline(), contextWith({ dataSinks: [frostSink] }))
      const sink = nodeOf(nodes, 'n-sink')

      expect(sink.type).toBe('frost')
      expect(sink.data).toMatchObject({ entityType: 'frost', entityId: 'sink-1', port: 'ThingTree', configured: true })
    })

    it('shows a FROST sink without a port as not configured', () => {
      const withoutPort = { ...frostSink, configuration: { element: STRUCTURE_URN } }
      const { nodes } = hydrateFromCoreModel(installedPipeline(), contextWith({ dataSinks: [withoutPort] }))
      expect(nodeOf(nodes, 'n-sink').data.configured).toBe(false)
    })

    it('shows a PostGIS sink without a structure as not configured and sends nothing for it', () => {
      const withoutStructure = { ...postgisSink, configuration: { tableName: 'traffic' } }
      const dto = installedPipeline()
      const context = contextWith({ dataSinks: [withoutStructure] })
      const { nodes } = hydrateFromCoreModel(dto, context)

      expect(nodeOf(nodes, 'n-sink').data.configured).toBe(false)
      expect(buildDataSinkPayloads(asPipeline(dto, context))).toEqual([])
    })

    it('finds the sink through the link of the backend when the reference has no match', () => {
      const otherUrn = 'urn:core:platform:civitas:datasink:common:Other:0000000000'
      const renamed = { ...postgisSink, configurationUrn: otherUrn }
      const { nodes } = hydrateFromCoreModel(installedPipeline(), contextWith({ dataSinks: [renamed] }))
      expect(nodeOf(nodes, 'n-sink').data).toMatchObject({ entityId: 'sink-1', tableName: 'traffic' })
    })

    it('does not draw a sink it cannot identify, because the node type would be a guess', () => {
      const { nodes, edges } = hydrateFromCoreModel(installedPipeline(), contextWith({ dataSinks: [] }))
      expect(nodes.map(node => node.id)).not.toContain('n-sink')
      expect(edges).toHaveLength(4)
    })
  })

  describe('mapping', () => {
    it('keeps the reference and derives the logical URN a later save versions', () => {
      const { nodes } = hydrateFromCoreModel(installedPipeline(), contextWith())
      expect(nodeOf(nodes, 'n-mapping').data).toMatchObject({
        mappingRef: versioned(MAPPING_URN),
        mappingLogicalUrn: MAPPING_URN,
        configured: true,
        mappingConfig: { fields: {}, positions: {} },
      })
    })

    it('gives the mapping in front of a FROST sink the structure the sink stores', () => {
      const { nodes } = hydrateFromCoreModel(installedPipeline(), contextWith({ dataSinks: [frostSink] }))
      expect(nodeOf(nodes, 'n-mapping').data).toMatchObject({ mappingConfig: { target: STRUCTURE_URN } })
    })

    it('does not give a target to the mapping in front of a PostGIS sink', () => {
      const { nodes } = hydrateFromCoreModel(installedPipeline(), contextWith())
      expect(nodeOf(nodes, 'n-mapping').data).toMatchObject({ mappingConfig: { fields: {}, positions: {} } })
      expect(nodeOf(nodes, 'n-mapping').data.mappingConfig).not.toHaveProperty('target')
    })

    it('marks the node as stored outside the mapping editor', () => {
      const { nodes } = hydrateFromCoreModel(installedPipeline(), contextWith())
      expect(nodeOf(nodes, 'n-mapping').data).toMatchObject({ isStoredOutsideEditor: true })
    })

    describe('with its stored document', () => {
      it('takes over the rules and the title of the stored mapping', () => {
        const { nodes } = hydrateFromCoreModel(installedPipeline(), withStoredMapping())
        expect(nodeOf(nodes, 'n-mapping').data).toMatchObject({
          mappingTitle: 'Count to traffic',
          mappingConfig: { source: COUNT_URN, target: TRAFFIC_URN, fields: STORED_MAPPING.fields, positions: {} },
          configured: true,
        })
      })

      it('binds the source and the target to the versions the mapping names, so the mapping editor opens', () => {
        const { nodes } = hydrateFromCoreModel(installedPipeline(), withStoredMapping())
        expect(nodeOf(nodes, 'n-mapping').data).toMatchObject({
          sourceDatastructureId: COUNT_STRUCTURE.id,
          sourceVersionId: 'count-version-1',
          targetDatastructureId: TRAFFIC_STRUCTURE.id,
          targetVersionId: 'traffic-version-2',
        })
      })

      it('keeps the layout of the mapping editor', () => {
        const positions = { 'concat-0': { x: 10, y: 20 } }
        const { nodes } = hydrateFromCoreModel(installedPipeline(), withStoredMapping({ ...STORED_MAPPING, positions }))
        expect(nodeOf(nodes, 'n-mapping').data).toMatchObject({ mappingConfig: { positions } })
      })

      it('leaves a structure unbound that the user cannot read, and keeps the rules', () => {
        const context = withStoredMapping(STORED_MAPPING, { dataStructures: [TRAFFIC_STRUCTURE] })
        const { data } = nodeOf(hydrateFromCoreModel(installedPipeline(), context).nodes, 'n-mapping')

        expect(data).toMatchObject({
          targetDatastructureId: TRAFFIC_STRUCTURE.id,
          mappingConfig: { source: COUNT_URN },
        })
        expect(data).not.toHaveProperty('sourceDatastructureId')
      })

      it('leaves a structure unbound when it has no version the mapping names', () => {
        const source = buildDataStructureUrn(COUNT_STRUCTURE.name, COUNT_STRUCTURE.id, '3.0.0')
        const context = withStoredMapping({ ...STORED_MAPPING, source })
        const { data } = nodeOf(hydrateFromCoreModel(installedPipeline(), context).nodes, 'n-mapping')

        expect(data).not.toHaveProperty('sourceDatastructureId')
        expect(data).toMatchObject({ targetVersionId: 'traffic-version-2' })
      })

      it('does not take over a document the mapping editor could not send back', () => {
        const context = withStoredMapping({ ...STORED_MAPPING, fields: { '$.vehicles': { op: 'unknown' } } })
        const { data } = nodeOf(hydrateFromCoreModel(installedPipeline(), context).nodes, 'n-mapping')

        expect(data).toMatchObject({ mappingRef: versioned(MAPPING_URN), mappingConfig: { fields: {}, positions: {} } })
        expect(data).not.toHaveProperty('targetDatastructureId')
      })

      it('keeps the target of the stored mapping in front of a FROST sink', () => {
        const context = withStoredMapping(STORED_MAPPING, { dataSinks: [frostSink] })
        const { nodes } = hydrateFromCoreModel(installedPipeline(), context)
        expect(nodeOf(nodes, 'n-mapping').data).toMatchObject({ mappingConfig: { target: TRAFFIC_URN } })
      })
    })
  })

  describe('scheduled trigger', () => {
    const cronNode = {
      id: 'n-cron',
      kind: 'cron',
      label: 'Each minute',
      cronExpression: '0 * * * * ?',
      cronPreview: 'each minute',
    }

    it('keeps the expression and its preview', () => {
      const model = { nodes: [...structuredClone(MODEL.nodes), cronNode], edges: structuredClone(MODEL.edges) }
      const { nodes } = hydrateFromCoreModel(dtoWith(model), contextWith())

      expect(nodeOf(nodes, 'n-cron').type).toBe('cron')
      expect(nodeOf(nodes, 'n-cron').data).toMatchObject({
        cronExpression: '0 * * * * ?',
        cronPreview: 'each minute',
        configured: true,
      })
    })

    it('shows a trigger without an expression as not configured', () => {
      const model = { nodes: [{ id: 'n-cron', kind: 'cron' }], edges: [] }
      const { nodes } = hydrateFromCoreModel(dtoWith(model), contextWith())
      expect(nodeOf(nodes, 'n-cron').data).toMatchObject({ cronExpression: '', configured: false })
    })
  })

  describe('edges', () => {
    it('draws each edge of the model with the edge type of the editor', () => {
      const { edges } = hydrateFromCoreModel(installedPipeline(), contextWith())
      expect(edges[0]).toEqual({
        id: 'e1',
        source: 'n-start',
        target: 'n-source',
        type: 'smoothstep',
        data: { label: '' },
      })
    })

    it('leaves out an edge that names no source or no target', () => {
      const model = { nodes: structuredClone(MODEL.nodes), edges: [{ id: 'broken', source: 'n-start' }] }
      expect(hydrateFromCoreModel(dtoWith(model), contextWith()).edges).toEqual([])
    })
  })

  describe('a save after the load', () => {
    it('sends the model it was derived from', () => {
      const dto = installedPipeline()
      const rebuilt = buildPipelineModel(asPipeline(dto))

      expect(rebuilt.nodes).toEqual(MODEL.nodes)
      expect(rebuilt.edges).toEqual(MODEL.edges)
    })

    it('keeps the links to the data source and the data sink', () => {
      const payload = buildPipelinePayload(asPipeline(installedPipeline()))

      expect(payload.dataSourceIds).toEqual(['source-1'])
      expect(payload.dataSinkIds).toEqual(['sink-1'])
    })

    it('updates the stored data sink and does not make a second one', () => {
      const payloads = buildDataSinkPayloads(asPipeline(installedPipeline()))

      expect(payloads).toHaveLength(1)
      expect(payloads[0].entityId).toBe('sink-1')
      expect(payloads[0].payload.configuration).toEqual({ tableName: 'traffic', element: STRUCTURE_URN })
    })

    it('sends a FROST sink with its port and its structure', () => {
      const dto = installedPipeline()
      const payloads = buildDataSinkPayloads(asPipeline(dto, contextWith({ dataSinks: [frostSink] })))

      expect(payloads[0].entityId).toBe('sink-1')
      expect(payloads[0].payload.configuration).toEqual({ port: 'ThingTree', element: STRUCTURE_URN })
    })

    it('does not version a stored mapping that was not changed', () => {
      const pipeline = asPipeline(installedPipeline(), withStoredMapping())
      const snapshot = createMappingSnapshot(pipeline)
      const [artifact] = buildMappingArtifacts(pipeline)

      expect(artifact.logicalUrn).toBe(MAPPING_URN)
      expect(hasMappingChanged(artifact.nodeId, artifact.body, snapshot)).toBe(false)
    })

    it('versions a changed stored mapping under its logical URN and its title', () => {
      const pipeline = asPipeline(installedPipeline(), withStoredMapping())
      const snapshot = createMappingSnapshot(pipeline)
      const mappingConfig = {
        source: COUNT_URN,
        target: TRAFFIC_URN,
        fields: { '$.vehicles': '$.total' },
        positions: {},
      }
      const [artifact] = buildMappingArtifacts(updateNodeData(pipeline, 'n-mapping', { mappingConfig }))

      expect(hasMappingChanged(artifact.nodeId, artifact.body, snapshot)).toBe(true)
      expect(artifact.logicalUrn).toBe(MAPPING_URN)
      expect(artifact.body.title).toBe('Count to traffic')
    })
  })
})

describe('createSessionFromBackendDTO', () => {
  it('draws a pipeline without a stored graph from its model', () => {
    const session = createSessionFromBackendDTO(installedPipeline(), contextWith())

    expect(session.pipeline.nodes).toHaveLength(MODEL.nodes.length)
    expect(session.pipeline.edges).toHaveLength(MODEL.edges.length)
    expect(session.isDirty).toBe(false)
    expect(session.pipeline.isDirty).toBe(false)
  })

  it('uses the stored graph when there is one', () => {
    const storedNode = { id: 'stored', type: 'start', position: { x: 1, y: 2 }, data: { label: 'Stored' } }
    const dto = dtoWith(structuredClone(MODEL), {
      styles: { nodes: [storedNode], edges: [], nodePositions: {} },
    })

    const session = createSessionFromBackendDTO(dto, contextWith())
    expect(session.pipeline.nodes.map(node => node.id)).toEqual(['stored'])
  })

  it('leaves the canvas empty when the caller supplies no lookups', () => {
    const session = createSessionFromBackendDTO(installedPipeline())
    expect(session.pipeline.nodes).toEqual([])
  })

  it('draws the nodes of a model whose edges name a node that is not there', () => {
    const model = { nodes: withoutNode('n-mapping'), edges: structuredClone(MODEL.edges) }
    const session = createSessionFromBackendDTO(dtoWith(model), contextWith())
    expect(session.pipeline.nodes.map(node => node.id)).toEqual(['n-start', 'n-source', 'n-sink', 'n-end'])
  })
})
