/**
 * Shared graph fixtures for the Pipeline editor conformance tests — one builder per editor node type.
 *
 * Every builder returns a configured node; `overrides.data` strips a field again by setting it to
 * `undefined`, which is how the draft cases (unconfigured nodes) are expressed.
 */

import { buildPipelinePayload } from '@/app/(main)/datasets/[datasetId]/data-flow/pipeline-editor/_services/payloadBuilderService'
import type {
  Pipeline,
  PipelineEdge,
  PipelineNode,
  PipelineNodeType,
  PipelineOutputDTO,
} from '@/app/(main)/datasets/[datasetId]/data-flow/pipeline-editor/_types/pipeline'
import { PIPELINE_NODE_TYPES } from '@/app/(main)/datasets/[datasetId]/data-flow/pipeline-editor/_types/pipeline'

export const DATA_SOURCE_URN = 'urn:core:platform:civitas:datasource:common:Mqtt:abcdef1234:1.0.0'
export const POSTGIS_SINK_URN = 'urn:core:platform:civitas:datasink:common:Postgis:abcdef1234:1.0.0'
export const FROST_SINK_URN = 'urn:core:platform:civitas:datasink:common:Frost:abcdef1234:1.0.0'
export const MAPPING_URN = 'urn:core:platform:civitas:mapping:common:SrcToTgt:abcdef1234:1.0.0'
export const MAPPING_LOGICAL_URN = 'urn:core:platform:civitas:mapping:common:SrcToTgt:abcdef1234'
export const SOURCE_STRUCTURE_URN = 'urn:core:platform:civitas:datastructure:common:Src:abcdef1234:1.0.0'
export const TARGET_STRUCTURE_URN = 'urn:core:platform:civitas:datastructure:common:Tgt:abcdef1234:1.0.0'

export interface FixtureNode {
  id: string
  type: PipelineNodeType
  position?: { x: number; y: number }
  data: Record<string, unknown>
}

export interface FixtureEdge {
  source: string
  target: string
  label?: string
}

interface NodeOverrides {
  id?: string
  position?: { x: number; y: number }
  data?: Record<string, unknown>
}

const node = (
  type: PipelineNodeType,
  id: string,
  position: { x: number; y: number },
  data: Record<string, unknown>,
  overrides: NodeOverrides,
): FixtureNode => ({
  id: overrides.id ?? id,
  type,
  position: overrides.position ?? position,
  data: { ...data, ...overrides.data },
})

export const startNode = (overrides: NodeOverrides = {}): FixtureNode =>
  node(
    PIPELINE_NODE_TYPES.Start,
    'start-1',
    { x: 0, y: 0 },
    { nodeType: PIPELINE_NODE_TYPES.Start, label: 'Start', configured: true },
    overrides,
  )

export const endNode = (overrides: NodeOverrides = {}): FixtureNode =>
  node(
    PIPELINE_NODE_TYPES.End,
    'end-1',
    { x: 600, y: 0 },
    { nodeType: PIPELINE_NODE_TYPES.End, label: 'End', configured: true },
    overrides,
  )

export const cronNode = (overrides: NodeOverrides = {}): FixtureNode =>
  node(
    PIPELINE_NODE_TYPES.Cron,
    'cron-1',
    { x: 0, y: 120 },
    { label: 'Scheduled Trigger', configured: true, cronExpression: '0 0 * * * ?' },
    overrides,
  )

export const dataSourceNode = (overrides: NodeOverrides = {}): FixtureNode =>
  node(
    PIPELINE_NODE_TYPES.DataSource,
    'src-1',
    { x: 10, y: 20 },
    {
      label: 'MQTT',
      configured: true,
      entityType: 'datasource',
      entityId: 'ds-guid-1',
      configurationUrn: DATA_SOURCE_URN,
    },
    overrides,
  )

export const mappingNode = (overrides: NodeOverrides = {}): FixtureNode =>
  node(
    PIPELINE_NODE_TYPES.Mapping,
    'map-1',
    { x: 50, y: 60 },
    {
      label: 'Mapping',
      configured: true,
      sourceName: 'Src',
      targetName: 'Tgt',
      mappingRef: MAPPING_URN,
      mappingLogicalUrn: MAPPING_LOGICAL_URN,
      mappingConfig: {
        source: SOURCE_STRUCTURE_URN,
        target: TARGET_STRUCTURE_URN,
        // eslint-disable-next-line @typescript-eslint/naming-convention -- JSONPath field key
        fields: { '$.target': '$.source' },
        positions: {},
      },
      targetRequiredFields: [],
      staMatchKeys: { thing: [], datastream: [], isFallback: false, thingBag: [], datastreamBag: [] },
    },
    overrides,
  )

export const frostNode = (overrides: NodeOverrides = {}): FixtureNode =>
  node(
    PIPELINE_NODE_TYPES.Frost,
    'frost-1',
    { x: 300, y: 120 },
    {
      label: 'Sensor Data Storage',
      configured: true,
      entityType: 'frost',
      entityId: 'frost-guid-1',
      configurationUrn: FROST_SINK_URN,
      serverName: 'Sensor Data Storage',
      serverUrl: '',
      version: '1.1',
      port: 'Things',
    },
    overrides,
  )

export const geoPersistenceNode = (overrides: NodeOverrides = {}): FixtureNode =>
  node(
    PIPELINE_NODE_TYPES.GeoPersistence,
    'sink-1',
    { x: 300, y: 0 },
    {
      label: 'Geospatial Data Storage',
      configured: true,
      entityType: 'persistence',
      entityId: 'sink-guid-1',
      configurationUrn: POSTGIS_SINK_URN,
      dataStructureVersionId: 'dsv-1',
      dataStructureUrn: TARGET_STRUCTURE_URN,
      tableName: 'my_table',
    },
    overrides,
  )

export const pipeline = (nodes: FixtureNode[], edges: FixtureEdge[], viewport = { x: 0, y: 0, zoom: 1 }): Pipeline => ({
  id: 'p-1',
  name: 'P',
  description: '',
  // buildPipelineModel reads a position from every node.
  nodes: nodes.map(node => ({ position: { x: 0, y: 0 }, ...node })) as unknown as PipelineNode[],
  edges: edges.map((edge, index) => ({
    id: `e-${index}`,
    source: edge.source,
    target: edge.target,
    ...(edge.label ? { data: { label: edge.label } } : {}),
  })) as unknown as PipelineEdge[],
  viewport,
  createdAt: new Date(0),
  updatedAt: new Date(0),
  isDirty: false,
})

/**
 * One node of every editor type, on a viewport that is not the default. The editor refuses to save it:
 * it allows one sink per pipeline.
 */
export const everyNodeTypePipeline = (): Pipeline =>
  pipeline(
    [startNode(), cronNode(), dataSourceNode(), mappingNode(), geoPersistenceNode(), frostNode(), endNode()],
    [
      { source: 'start-1', target: 'src-1' },
      { source: 'cron-1', target: 'src-1' },
      { source: 'src-1', target: 'map-1', label: 'flow' },
      { source: 'map-1', target: 'sink-1' },
      { source: 'map-1', target: 'frost-1' },
      { source: 'sink-1', target: 'end-1' },
      { source: 'frost-1', target: 'end-1' },
    ],
    { x: 12, y: -34, zoom: 1.5 },
  )

/** A pipeline the editor's save validation accepts: one source, one mapping and the given sink. */
export const savablePipeline = (sink: FixtureNode): Pipeline =>
  pipeline(
    [startNode(), cronNode(), dataSourceNode(), mappingNode(), sink, endNode()],
    [
      { source: 'start-1', target: 'cron-1' },
      { source: 'cron-1', target: 'src-1' },
      { source: 'src-1', target: 'map-1' },
      { source: 'map-1', target: sink.id },
      { source: sink.id, target: 'end-1' },
    ],
  )

/** A savable pipeline without a mapping: the source feeds the given sink directly. */
export const passthroughPipeline = (sink: FixtureNode): Pipeline =>
  pipeline(
    [startNode(), cronNode(), dataSourceNode(), sink, endNode()],
    [
      { source: 'start-1', target: 'cron-1' },
      { source: 'cron-1', target: 'src-1' },
      { source: 'src-1', target: sink.id },
      { source: sink.id, target: 'end-1' },
    ],
  )

/** The response the backend returns for a saved pipeline, sent through JSON like a real one. */
export const savedAs = (source: Pipeline): PipelineOutputDTO => {
  const payload = buildPipelinePayload(source)
  return JSON.parse(
    JSON.stringify({
      id: 'backend-pipeline-1',
      name: source.name,
      description: source.description,
      styles: payload.styles,
      dataSources: [],
      apis: [],
      dataSinks: [],
      model: payload.model,
      createdAt: '2024-01-01T00:00:00Z',
      modifiedAt: '2024-01-02T00:00:00Z',
    }),
  )
}
