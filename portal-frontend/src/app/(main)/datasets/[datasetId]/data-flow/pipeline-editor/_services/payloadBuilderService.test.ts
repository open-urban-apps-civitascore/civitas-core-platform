import { describe, expect, it, vi } from 'vitest'

import { CONTRACT_URIS, contractErrors, type ContractKind } from '@/test-support/coreContracts'
import { everyNodeTypePipeline, type FixtureNode, pipeline } from '@/test-support/pipelineFixtures'
import { DATASINK_TYPES, type DataSinkPayload } from '@/types/datasinks'

import type { Pipeline } from '../_types/pipeline'
import {
  buildDataSinkPayloads,
  buildMappingArtifacts,
  buildPipelinePayload,
  createMappingSnapshot,
  type DataSinkSnapshot,
  hasMappingChanged,
  isDestructiveDataSinkChange,
  MappingDocumentValidationError,
  PipelineModelValidationError,
} from './payloadBuilderService'

/**
 * Pins the FROST sink payload derivation — the seam that lets the deploy engine resolve the
 * match keys at all: a wrong result here publishes a sink without (or with the wrong) target
 * structure and every mapped deploy fails.
 */

const frostNode: FixtureNode = {
  id: 'frost-1',
  type: 'frost',
  data: { label: 'FROST', configured: true, entityType: 'frost', entityId: 'sink-1' },
}

const mappingNode = (id: string, targetUrn?: string): FixtureNode => ({
  id,
  type: 'mapping',
  data: {
    label: 'Mapping',
    configured: true,
    // The FROST sink references the final mapping's target DataStructure by its versioned CORE URN,
    // taken from the mapping's saved config (`mappingConfig.target`).
    mappingConfig: { fields: {}, positions: {}, ...(targetUrn === undefined ? {} : { target: targetUrn }) },
  },
})

const frostPayload = (p: Pipeline) =>
  buildDataSinkPayloads(p).find(entry => entry.payload.dataSinkType === 'FROST')?.payload

describe('buildDataSinkPayloads — FROST target structure reference', () => {
  // The FROST sink's `element` is validated against the CORE datasink schema before send, so the
  // mapping target must be a real versioned CORE :datastructure: URN (not a placeholder).
  const DSV_URN = 'urn:core:platform:civitas:datastructure:common:Thing:aa11bb22cc:1.0.0'
  const DSV_UPSTREAM_URN = 'urn:core:platform:civitas:datastructure:common:Upstream:aa11bb22cc:1.0.0'
  const DSV_FINAL_URN = 'urn:core:platform:civitas:datastructure:common:Final:aa11bb22cc:1.0.0'

  it('references the final mapping target of a mapped pipeline', () => {
    const p = pipeline([mappingNode('map-1', DSV_URN), frostNode], [{ source: 'map-1', target: 'frost-1' }])
    expect(frostPayload(p)?.configuration).toEqual({ element: DSV_URN })
  })

  it('sends an empty configuration for a passthrough pipeline (no mapping)', () => {
    const source: FixtureNode = {
      id: 'src-1',
      type: 'dataSource',
      data: { label: 'MQTT', configured: true, entityType: 'datasource', entityId: 'ds-1' },
    }
    const p = pipeline([source, frostNode], [{ source: 'src-1', target: 'frost-1' }])
    expect(frostPayload(p)?.configuration).toEqual({})
  })

  it('picks the nearest mapping of a chain (the one whose target the sink consumes)', () => {
    const p = pipeline(
      [mappingNode('map-1', DSV_UPSTREAM_URN), mappingNode('map-2', DSV_FINAL_URN), frostNode],
      [
        { source: 'map-1', target: 'map-2' },
        { source: 'map-2', target: 'frost-1' },
      ],
    )
    expect(frostPayload(p)?.configuration).toEqual({ element: DSV_FINAL_URN })
  })

  it('terminates on a cyclic graph', () => {
    const p = pipeline(
      [mappingNode('map-1', DSV_URN), frostNode],
      [
        { source: 'map-1', target: 'frost-1' },
        { source: 'frost-1', target: 'map-1' },
      ],
    )
    expect(frostPayload(p)?.configuration).toEqual({ element: DSV_URN })
  })

  it('sends an empty configuration when the mapping has no target version yet', () => {
    // The deploy then fails loudly on the engine side; the editor validation forces a re-save
    // before this state can be released.
    const p = pipeline([mappingNode('map-1'), frostNode], [{ source: 'map-1', target: 'frost-1' }])
    expect(frostPayload(p)?.configuration).toEqual({})
  })
})

describe('buildDataSinkPayloads — PostGIS configuration', () => {
  const postgisNode: FixtureNode = {
    id: 'postgis-1',
    type: 'geoPersistence',
    data: {
      label: 'PostGIS',
      configured: true,
      entityType: 'persistence',
      entityId: 'sink-guid-1',
      dataStructureVersionId: 'dsv-1',
      tableName: 'my_table',
    },
  }

  const postgisPayload = (p: Pipeline) =>
    buildDataSinkPayloads(p).find(entry => entry.payload.dataSinkType === DATASINK_TYPES.POSTGIS)?.payload

  it('omits element for a passthrough pipeline (no upstream mapping)', () => {
    // A passthrough DataSource → PostGIS pipeline is valid and must be saveable — element is only
    // present once a mapping feeds the sink (mirrors the FROST passthrough case above).
    const p = pipeline([postgisNode], [])
    expect(postgisPayload(p)?.configuration).toEqual({ tableName: 'my_table' })
  })
})

// ============================================================================
// buildPipelinePayload — clean CORE Pipeline document
// ============================================================================

const DS_URN = 'urn:core:platform:civitas:datasource:common:Mqtt:abcdef1234:1.0.0'
const SINK_URN = 'urn:core:platform:civitas:datasink:common:Postgis:abcdef1234:1.0.0'
const MAPPING_URN = 'urn:core:platform:civitas:mapping:common:SrcToTgt:abcdef1234:1.0.0'
const SRC_STRUCT_URN = 'urn:core:platform:civitas:datastructure:common:Src:abcdef1234:1.0.0'
const TGT_STRUCT_URN = 'urn:core:platform:civitas:datastructure:common:Tgt:abcdef1234:1.0.0'

const startNode: FixtureNode = { id: 'start-1', type: 'start', data: { label: 'Start', configured: true } }
const endNode: FixtureNode = { id: 'end-1', type: 'end', data: { label: 'End', configured: true } }
const sourceNode = (urn?: string): FixtureNode => ({
  id: 'src-1',
  type: 'dataSource',
  position: { x: 10, y: 20 },
  data: { label: 'MQTT', configured: true, entityType: 'datasource', entityId: 'ds-guid-1', configurationUrn: urn },
})
const sinkNode = (urn?: string): FixtureNode => ({
  id: 'sink-1',
  type: 'geoPersistence',
  position: { x: 30, y: 40 },
  data: {
    label: 'PostGIS',
    configured: true,
    entityType: 'persistence',
    entityId: 'sink-guid-1',
    configurationUrn: urn,
  },
})
const mappingRefNode = (ref?: string): FixtureNode => ({
  id: 'map-1',
  type: 'mapping',
  position: { x: 50, y: 60 },
  data: { label: 'Mapping', configured: true, mappingConfig: { fields: {}, positions: {} }, mappingRef: ref },
})
const cronNode: FixtureNode = {
  id: 'cron-1',
  type: 'cron',
  data: { label: 'Cron', configured: true, cronExpression: '0 0 * * *' },
}

describe('buildPipelinePayload — clean CORE Pipeline document', () => {
  it('emits URN-native nodes keyed by kind (no React-Flow type/data)', () => {
    const p = pipeline(
      [startNode, sourceNode(DS_URN), mappingRefNode(MAPPING_URN), sinkNode(SINK_URN), endNode],
      [
        { source: 'start-1', target: 'src-1' },
        { source: 'src-1', target: 'map-1', label: 'flow' },
        { source: 'map-1', target: 'sink-1' },
        { source: 'sink-1', target: 'end-1' },
      ],
    )

    const { model } = buildPipelinePayload(p)

    expect(model.nodes.map(n => n.kind)).toEqual(['start', 'source', 'mapping', 'sink', 'end'])
    // No React-Flow specifics leak into the CORE model.
    for (const node of model.nodes) {
      expect(node).not.toHaveProperty('type')
      expect(node).not.toHaveProperty('data')
    }

    const src = model.nodes.find(n => n.id === 'src-1')
    expect(src).toMatchObject({ kind: 'source', sourceRef: DS_URN, 'x-ui-position': { x: 10, y: 20 } })
    expect(src).not.toHaveProperty('sinkRef')

    expect(model.nodes.find(n => n.id === 'sink-1')).toMatchObject({ kind: 'sink', sinkRef: SINK_URN })
    expect(model.nodes.find(n => n.id === 'map-1')).toMatchObject({ kind: 'mapping', mappingRef: MAPPING_URN })
  })

  it('maps cron nodes to a cron kind carrying the cron expression', () => {
    const p = pipeline([cronNode, sourceNode(DS_URN)], [{ source: 'cron-1', target: 'src-1' }])
    const { model } = buildPipelinePayload(p)
    expect(model.nodes.find(n => n.id === 'cron-1')).toMatchObject({ kind: 'cron', cronExpression: '0 0 * * *' })
  })

  it('maps every editor node type onto its CORE kind, both sink types included', () => {
    const { model } = buildPipelinePayload(everyNodeTypePipeline())
    expect(model.nodes.map(n => [n.id, n.kind])).toEqual([
      ['start-1', 'start'],
      ['cron-1', 'cron'],
      ['src-1', 'source'],
      ['map-1', 'mapping'],
      ['sink-1', 'sink'],
      ['frost-1', 'sink'],
      ['end-1', 'end'],
    ])
  })

  it('emits CORE edges (id/source/target + optional label) mirroring the graph', () => {
    const p = pipeline([sourceNode(DS_URN), sinkNode(SINK_URN)], [{ source: 'src-1', target: 'sink-1', label: 'data' }])
    const { model } = buildPipelinePayload(p)
    expect(model.edges).toEqual([{ id: 'e-0', source: 'src-1', target: 'sink-1', label: 'data' }])
  })

  it('omits a ref when the node is unconfigured (draft pipeline stays valid)', () => {
    const p = pipeline([sourceNode(undefined), sinkNode(undefined)], [])
    const { model } = buildPipelinePayload(p)
    expect(model.nodes.find(n => n.id === 'src-1')).not.toHaveProperty('sourceRef')
    expect(model.nodes.find(n => n.id === 'sink-1')).not.toHaveProperty('sinkRef')
  })

  it('keeps styles as the full React-Flow graph and preserves FK id lists', () => {
    const p = pipeline([sourceNode(DS_URN), sinkNode(SINK_URN)], [{ source: 'src-1', target: 'sink-1' }])
    const payload = buildPipelinePayload(p)

    // styles round-trips the RF graph unchanged (LOAD path hydrates from styles).
    expect(payload.styles.nodes).toBe(p.nodes)
    expect(payload.styles.edges).toBe(p.edges)
    expect(payload.styles.viewport).toEqual({ x: 0, y: 0, zoom: 1 })

    // FK wiring ids (portal GUIDs) are still sent.
    expect(payload.dataSourceIds).toEqual(['ds-guid-1'])
    expect(payload.dataSinkIds).toEqual(['sink-guid-1'])
  })

  it('validates the CORE model and throws on an unknown node kind', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    const bogus = { id: 'x-1', type: 'bogus', data: { label: 'X' } } as unknown as FixtureNode
    expect(() => buildPipelinePayload(pipeline([bogus], []))).toThrow(PipelineModelValidationError)
    consoleError.mockRestore()
  })
})

// ============================================================================
// buildMappingArtifacts — POST/PUT /v1/mappings extraction + validation
// ============================================================================

const configuredMappingNode = (overrides?: Record<string, unknown>): FixtureNode => ({
  id: 'map-1',
  type: 'mapping',
  data: {
    label: 'Mapping',
    configured: true,
    sourceName: 'Src',
    targetName: 'Tgt',
    mappingConfig: {
      source: SRC_STRUCT_URN,
      target: TGT_STRUCT_URN,
      fields: { '$.name': '$.n' },
      positions: { a: { x: 1, y: 2 } },
    },
    ...overrides,
  },
})

describe('buildMappingArtifacts', () => {
  it('extracts a POST request for a configured mapping node', () => {
    const artifacts = buildMappingArtifacts(pipeline([configuredMappingNode()], []))
    expect(artifacts).toHaveLength(1)
    expect(artifacts[0]).toEqual({
      nodeId: 'map-1',
      logicalUrn: undefined,
      body: {
        source: SRC_STRUCT_URN,
        target: TGT_STRUCT_URN,
        fields: { '$.name': '$.n' },
        title: 'Src-to-Tgt',
        positions: { a: { x: 1, y: 2 } },
      },
    })
  })

  it('carries the prior logical URN so an existing mapping is PUT-versioned', () => {
    const artifacts = buildMappingArtifacts(
      pipeline([configuredMappingNode({ mappingLogicalUrn: 'urn:core:logical:x' })], []),
    )
    expect(artifacts[0].logicalUrn).toBe('urn:core:logical:x')
  })

  it('skips unconfigured mapping nodes (no source/target → no mappingRef)', () => {
    const unconfigured: FixtureNode = {
      id: 'map-2',
      type: 'mapping',
      data: { label: 'Mapping', configured: false, mappingConfig: { fields: {}, positions: {} } },
    }
    expect(buildMappingArtifacts(pipeline([unconfigured], []))).toEqual([])
  })

  it('throws when a mapping document is not schema-valid', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    const invalid = configuredMappingNode({
      mappingConfig: { source: 'not-a-urn', target: TGT_STRUCT_URN, fields: {}, positions: {} },
    })
    expect(() => buildMappingArtifacts(pipeline([invalid], []))).toThrow(MappingDocumentValidationError)
    consoleError.mockRestore()
  })

  it('accepts toUuid and toDateTime field operations from the mapping editor transform palette', () => {
    const node = configuredMappingNode({
      mappingConfig: {
        source: SRC_STRUCT_URN,
        target: TGT_STRUCT_URN,
        fields: {
          '$.id': { op: 'toUuid', input: '$.sourceId' },
          '$.ts': { op: 'toDateTime', input: '$.sourceTs', pattern: "yyyy-MM-dd'T'HH:mm:ssXXX" },
        },
        positions: {},
      },
    })
    expect(() => buildMappingArtifacts(pipeline([node], []))).not.toThrow()
  })
})

describe('createMappingSnapshot / hasMappingChanged', () => {
  const mappingBody = {
    source: SRC_STRUCT_URN,
    target: TGT_STRUCT_URN,
    fields: { '$.name': '$.n' },
    title: 'Src-to-Tgt',
    positions: { a: { x: 1, y: 2 } },
  }

  it('flags a mapping as changed when its body differs from the snapshot', () => {
    const snapshot = createMappingSnapshot(pipeline([configuredMappingNode()], []))
    const changedBody = { ...mappingBody, fields: { '$.name': '$.other' } }
    expect(hasMappingChanged('map-1', changedBody, snapshot)).toBe(true)
  })

  it('does not flag an unchanged mapping', () => {
    const snapshot = createMappingSnapshot(pipeline([configuredMappingNode()], []))
    expect(hasMappingChanged('map-1', mappingBody, snapshot)).toBe(false)
  })

  it('flags a mapping not present in the snapshot as changed (new node)', () => {
    expect(hasMappingChanged('map-1', mappingBody, {})).toBe(true)
  })

  it('omits unconfigured mapping nodes from the snapshot', () => {
    const unconfigured: FixtureNode = {
      id: 'map-2',
      type: 'mapping',
      data: { label: 'Mapping', configured: false, mappingConfig: { fields: {}, positions: {} } },
    }
    expect(createMappingSnapshot(pipeline([unconfigured], []))).toEqual({})
  })
})

describe('isDestructiveDataSinkChange', () => {
  const postgisPayload = (tableName: string, element: string): DataSinkPayload => ({
    id: 'sink-1',
    dataSinkType: DATASINK_TYPES.POSTGIS,
    configuration: { tableName, element },
  })

  const snapshotFor = (payload: DataSinkPayload, entityId: string | null): DataSinkSnapshot => {
    const { id: _id, ...comparable } = payload
    return { 'node-1': { entityId, configJson: JSON.stringify(comparable) } }
  }

  it('flags a tableName change on an existing sink', () => {
    const snapshot = snapshotFor(postgisPayload('old', 'v1'), 'sink-1')
    expect(isDestructiveDataSinkChange('node-1', postgisPayload('new', 'v1'), snapshot)).toBe(true)
  })

  it('flags an element change on an existing sink', () => {
    const snapshot = snapshotFor(postgisPayload('t', 'v1'), 'sink-1')
    expect(isDestructiveDataSinkChange('node-1', postgisPayload('t', 'v2'), snapshot)).toBe(true)
  })

  it('does not flag an unchanged sink', () => {
    const snapshot = snapshotFor(postgisPayload('t', 'v1'), 'sink-1')
    expect(isDestructiveDataSinkChange('node-1', postgisPayload('t', 'v1'), snapshot)).toBe(false)
  })

  it('does not flag a brand-new sink (no entity, no table to lose)', () => {
    // Not in the snapshot at all → new node.
    expect(isDestructiveDataSinkChange('node-1', postgisPayload('t', 'v1'), {})).toBe(false)
  })

  it('does not flag a sink whose snapshot entry has no entityId', () => {
    const snapshot = snapshotFor(postgisPayload('old', 'v1'), null)
    expect(isDestructiveDataSinkChange('node-1', postgisPayload('new', 'v1'), snapshot)).toBe(false)
  })
})

// ============================================================================
// CORE-IR conformance — AC 1, create half
// ============================================================================

/**
 * The gates inside the builders validate against the generated Zod copy of the CORE contracts.
 * These cases hold the same artifacts against the published contract files instead.
 * `$schema`/`id` are stamped here because Model Forge adds them on ingest and the builders
 * deliberately leave them out.
 */
describe('CORE-IR conformance of the artifacts sent to Model Forge', () => {
  const PIPELINE_URN = 'urn:core:platform:civitas:pipeline:common:P:abcdef1234:1.0.0'

  const stamped = (kind: ContractKind, id: string, document: object) => ({
    $schema: CONTRACT_URIS[kind],
    id,
    ...document,
  })

  // The backend derives connectionType from dataSinkType; buildDataSinkPayloads validates that same
  // projection internally without returning it.
  const sinkDocument = (payload: DataSinkPayload) =>
    stamped('datasink', SINK_URN, { connectionType: payload.dataSinkType.toLowerCase(), ...payload.configuration })

  const sinkPayloadOf = (p: Pipeline, type: string) =>
    buildDataSinkPayloads(p).find(entry => entry.payload.dataSinkType === type)!.payload

  const postgisNode: FixtureNode = {
    id: 'sink-1',
    type: 'geoPersistence',
    position: { x: 30, y: 40 },
    data: {
      label: 'PostGIS',
      configured: true,
      entityType: 'persistence',
      entityId: 'sink-guid-1',
      configurationUrn: SINK_URN,
      dataStructureVersionId: 'dsv-1',
      tableName: 'my_table',
    },
  }

  const configuredMapping: FixtureNode = {
    id: 'map-1',
    type: 'mapping',
    position: { x: 50, y: 60 },
    data: {
      label: 'Mapping',
      configured: true,
      mappingRef: MAPPING_URN,
      mappingConfig: { source: SRC_STRUCT_URN, target: TGT_STRUCT_URN, fields: { '$.a': '$.b' }, positions: {} },
    },
  }

  it('a Pipeline covering every node kind, with a copy-only mapping, satisfies the published pipeline contract', () => {
    const model = buildPipelinePayload(everyNodeTypePipeline()).model
    expect(contractErrors('pipeline', stamped('pipeline', PIPELINE_URN, model))).toEqual([])
  })

  it('a Pipeline without a cron node satisfies the published pipeline contract', () => {
    const p = pipeline(
      [startNode, sourceNode(DS_URN), configuredMapping, postgisNode, endNode],
      [
        { source: 'start-1', target: 'src-1' },
        { source: 'src-1', target: 'map-1' },
        { source: 'map-1', target: 'sink-1' },
        { source: 'sink-1', target: 'end-1' },
      ],
    )
    expect(contractErrors('pipeline', stamped('pipeline', PIPELINE_URN, buildPipelinePayload(p).model))).toEqual([])
  })

  it('a Pipeline without a mapping node satisfies the published pipeline contract', () => {
    const p = pipeline(
      [startNode, cronNode, sourceNode(DS_URN), postgisNode, endNode],
      [
        { source: 'start-1', target: 'src-1' },
        { source: 'cron-1', target: 'src-1' },
        { source: 'src-1', target: 'sink-1' },
        { source: 'sink-1', target: 'end-1' },
      ],
    )
    expect(contractErrors('pipeline', stamped('pipeline', PIPELINE_URN, buildPipelinePayload(p).model))).toEqual([])
  })

  // Both sink types, each with and without an upstream mapping
  const sinkCases: [string, FixtureNode, FixtureNode, string][] = [
    ['copy-only mapped PostGIS', configuredMapping, postgisNode, DATASINK_TYPES.POSTGIS],
    ['copy-only mapped FROST', configuredMapping, frostNode, DATASINK_TYPES.FROST],
    ['passthrough PostGIS', sourceNode(DS_URN), postgisNode, DATASINK_TYPES.POSTGIS],
    ['passthrough FROST', sourceNode(DS_URN), frostNode, DATASINK_TYPES.FROST],
  ]

  it.each(sinkCases)('a %s sink satisfies the published datasink contract', (_label, upstream, sink, sinkType) => {
    const p = pipeline([upstream, sink], [{ source: upstream.id, target: sink.id }])
    expect(contractErrors('datasink', sinkDocument(sinkPayloadOf(p, sinkType)))).toEqual([])
  })

  it('a Mapping document with a single copy field satisfies the published mapping contract', () => {
    const [artifact] = buildMappingArtifacts(pipeline([configuredMapping], []))
    expect(contractErrors('mapping', stamped('mapping', MAPPING_URN, artifact.body))).toEqual([])
  })

  // One case per operation the mapping editor's transform palette offers, plus the copy shorthand it
  // writes for a direct edge
  const mappingOperations: [string, unknown][] = [
    ['copy written as a path string', '$.source'],
    ['copy written as an object with sourcePath', { op: 'copy', sourcePath: '$.source' }],
    ['const', { op: 'const', value: 'fixed' }],
    ['concat', { op: 'concat', inputs: ['$.first', '$.second'], separator: ' ' }],
    ['geoPoint', { op: 'geoPoint', lon: '$.lon', lat: '$.lat' }],
    ['toString', { op: 'toString', input: '$.source' }],
    ['toInt', { op: 'toInt', input: '$.source' }],
    ['toFloat', { op: 'toFloat', input: '$.source' }],
    ['toDate', { op: 'toDate', input: '$.source', pattern: 'yyyy-MM-dd' }],
    ['format', { op: 'format', input: '$.source', pattern: '%.2f' }],
    ['toUuid', { op: 'toUuid', input: '$.source' }],
    ['toDateTime', { op: 'toDateTime', input: '$.source', pattern: "yyyy-MM-dd'T'HH:mm:ssXXX" }],
  ]

  it.each(mappingOperations)('a Mapping using %s satisfies the published mapping contract', (_label, operation) => {
    const node: FixtureNode = {
      ...configuredMapping,
      data: {
        ...configuredMapping.data,
        mappingConfig: {
          source: SRC_STRUCT_URN,
          target: TGT_STRUCT_URN,
          fields: { '$.target': operation },
          positions: {},
        },
      },
    }
    const [artifact] = buildMappingArtifacts(pipeline([node], []))
    expect(contractErrors('mapping', stamped('mapping', MAPPING_URN, artifact.body))).toEqual([])
  })
})
