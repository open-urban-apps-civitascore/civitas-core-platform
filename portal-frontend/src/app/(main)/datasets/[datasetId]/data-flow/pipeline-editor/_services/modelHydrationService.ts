/**
 * Model Hydration Service
 *
 * Builds the editor graph of a pipeline from its CORE model.
 *
 * The editor stores its own graph in `styles` and reads only that on load. A pipeline that did not
 * come from this editor (an installed package, a direct API call) has a model but no `styles`. The
 * canvas then shows nothing, although the pipeline is complete and can run. This service derives
 * the graph from the model, so that such a pipeline opens like any other.
 *
 * The model holds its references as CORE URNs. The editor also needs the backend ids of the data
 * source and the data sinks, because a save sends them. The caller supplies the data sources and
 * data sinks of the dataset, and this service resolves each reference against them.
 *
 * Nothing is written on load. The derived graph becomes `styles` when the user saves the pipeline.
 *
 * Kept React-free: the default data of a node comes from the caller, because the node registry
 * imports the inspector panels.
 */

import { DATASINK_TYPES, type DataSinkType } from '@/types/datasinks'
import type { Datasource } from '@/types/datasources'
import { toLogicalUrn } from '@/utils/urn'

import { isFrostSinkPort } from '../_constants/frostPorts'
import { isFrostNodeData, isMappingNodeData, type PipelineNodeData } from '../_types/nodes'
import {
  type Pipeline,
  PIPELINE_NODE_TYPES,
  type PipelineEdge,
  type PipelineNode,
  type PipelineNodeType,
  type PipelineOutputDTO,
} from '../_types/pipeline'

// ============================================================================
// Types
// ============================================================================

/** What the hydration reads from a data sink of the dataset. */
export interface HydrationDataSink {
  id: string
  dataSinkType: DataSinkType
  configurationUrn?: string | null
  configuration?: {
    tableName?: string | null
    element?: string | null
    port?: string | null
    dataStructureVersion?: { id: string; dataStructureId: string } | null
  } | null
}

/** What the hydration reads from a data source that the dataset can use. */
export type HydrationDataSource = Pick<Datasource, 'id' | 'name' | 'connectorType' | 'configurationUrn'>

export interface ModelHydrationContext {
  dataSinks: readonly HydrationDataSink[]
  dataSources: readonly HydrationDataSource[]
  /** The data a new node of this type starts with. The node registry is the source. */
  createDefaultData: (type: PipelineNodeType) => PipelineNodeData | undefined
}

type ModelRecord = Record<string, unknown>

interface NodeDraft {
  type: PipelineNodeType
  data: Record<string, unknown>
}

interface Lookups {
  sourcesByUrn: Map<string, HydrationDataSource>
  sinksByUrn: Map<string, HydrationDataSink>
  sinksById: Map<string, HydrationDataSink>
  /** The id of the linked data source, when the pipeline has one source node and one linked source. */
  soleSourceId: string | undefined
  /** The linked data sink, when the pipeline has one sink node and one linked sink. */
  soleSink: HydrationDataSink | undefined
}

// ============================================================================
// Constants
// ============================================================================

const PIPELINE_EDGE_TYPE = 'smoothstep'

/** Layout for a model that has no positions: one row, in the order of the node list. */
const FALLBACK_NODE_SPACING_X = 260
const FALLBACK_NODE_Y = 120

const MODEL_KINDS = {
  start: 'start',
  end: 'end',
  cron: 'cron',
  source: 'source',
  mapping: 'mapping',
  sink: 'sink',
} as const

// ============================================================================
// Model access
// ============================================================================

const isRecord = (value: unknown): value is ModelRecord =>
  typeof value === 'object' && value !== null && !Array.isArray(value)

const recordsOf = (value: unknown): ModelRecord[] => (Array.isArray(value) ? value.filter(isRecord) : [])

const textOf = (value: unknown): string | undefined =>
  typeof value === 'string' && value.trim() !== '' ? value : undefined

const modelNodesOf = (dto: Pick<PipelineOutputDTO, 'model'>): ModelRecord[] =>
  isRecord(dto.model) ? recordsOf(dto.model.nodes) : []

const modelEdgesOf = (dto: Pick<PipelineOutputDTO, 'model'>): ModelRecord[] =>
  isRecord(dto.model) ? recordsOf(dto.model.edges) : []

const soleEntry = <T>(entries: readonly T[]): T | undefined => (entries.length === 1 ? entries[0] : undefined)

/** Removes the entries without a value, so that they do not replace a default. */
const withoutUndefined = (data: Record<string, unknown>): Record<string, unknown> => {
  const entries = Object.entries(data)
  const definedEntries = entries.filter(([, value]) => value !== undefined)
  return Object.fromEntries(definedEntries)
}

// ============================================================================
// Public checks
// ============================================================================

/** True for a pipeline that has a graph stored by the editor. */
export const hasStoredGraph = (dto: Pick<PipelineOutputDTO, 'styles'>): boolean => {
  const storedNodes = dto.styles?.nodes ?? []
  return storedNodes.length > 0
}

/** True for a pipeline that has a model to draw but no graph stored by the editor. */
export const needsModelHydration = (dto: Pick<PipelineOutputDTO, 'styles' | 'model'>): boolean =>
  !hasStoredGraph(dto) && modelNodesOf(dto).length > 0

// ============================================================================
// Lookups
// ============================================================================

const byLogicalUrn = <T extends { configurationUrn?: string | null }>(entities: readonly T[]): Map<string, T> => {
  const lookup = new Map<string, T>()
  for (const entity of entities) {
    if (entity.configurationUrn) lookup.set(toLogicalUrn(entity.configurationUrn), entity)
  }
  return lookup
}

const buildLookups = (dto: PipelineOutputDTO, modelNodes: ModelRecord[], context: ModelHydrationContext): Lookups => {
  const sinksById = new Map(context.dataSinks.map(sink => [sink.id, sink]))
  const sourceNodes = modelNodes.filter(node => node.kind === MODEL_KINDS.source)
  const sinkNodes = modelNodes.filter(node => node.kind === MODEL_KINDS.sink)

  // A reference can fail to resolve, for example when the user cannot read the data source. The
  // link the backend holds is then the answer, but only when it is not ambiguous.
  const soleSourceId = sourceNodes.length === 1 ? soleEntry(dto.dataSourceIds ?? []) : undefined
  const soleSinkId = sinkNodes.length === 1 ? soleEntry(dto.dataSinkIds ?? []) : undefined

  return {
    sourcesByUrn: byLogicalUrn(context.dataSources),
    sinksByUrn: byLogicalUrn(context.dataSinks),
    sinksById,
    soleSourceId,
    soleSink: soleSinkId ? sinksById.get(soleSinkId) : undefined,
  }
}

// ============================================================================
// Nodes
// ============================================================================

const positionOf = (node: ModelRecord, index: number): { x: number; y: number } => {
  const stored = node['x-ui-position']
  if (isRecord(stored) && typeof stored.x === 'number' && typeof stored.y === 'number') {
    return { x: stored.x, y: stored.y }
  }
  return { x: index * FALLBACK_NODE_SPACING_X, y: FALLBACK_NODE_Y }
}

const sourceDraft = (node: ModelRecord, lookups: Lookups): NodeDraft => {
  const reference = textOf(node.sourceRef)
  const source = reference ? lookups.sourcesByUrn.get(toLogicalUrn(reference)) : undefined
  const entityId = source?.id ?? lookups.soleSourceId

  return {
    type: PIPELINE_NODE_TYPES.DataSource,
    data: {
      configurationUrn: reference,
      entityId,
      entityName: source?.name,
      entityMetadata: source?.connectorType ? { connector: source.connectorType } : undefined,
      // Without the backend id a save would remove the link to the data source.
      configured: entityId !== undefined,
    },
  }
}

const frostDraft = (reference: string | undefined, sink: HydrationDataSink): NodeDraft => {
  const storedPort = sink.configuration?.port
  const port = isFrostSinkPort(storedPort) ? storedPort : undefined

  return {
    type: PIPELINE_NODE_TYPES.Frost,
    data: { entityId: sink.id, configurationUrn: reference, port, configured: port !== undefined },
  }
}

const geoPersistenceDraft = (reference: string | undefined, sink: HydrationDataSink): NodeDraft => {
  const tableName = sink.configuration?.tableName ?? ''
  const version = sink.configuration?.dataStructureVersion
  const element = sink.configuration?.element
  const data: Record<string, unknown> = {
    entityId: sink.id,
    configurationUrn: reference,
    tableName,
    configured: false,
  }

  // The version id and the URN go together: the payload builder refuses one without the other.
  if (version && element) {
    data.dataStructureVersionId = `${version.dataStructureId}/${version.id}`
    data.dataStructureUrn = element
    data.configured = tableName !== ''
  }

  return { type: PIPELINE_NODE_TYPES.GeoPersistence, data }
}

const sinkDraft = (node: ModelRecord, lookups: Lookups): NodeDraft | undefined => {
  const reference = textOf(node.sinkRef)
  const referenced = reference ? lookups.sinksByUrn.get(toLogicalUrn(reference)) : undefined
  const sink = referenced ?? lookups.soleSink

  // Only the data sink tells a FROST node from a geo persistence node. A guess would show the
  // wrong node, so a sink that does not resolve is not drawn.
  if (!sink) return undefined
  if (sink.dataSinkType === DATASINK_TYPES.FROST) return frostDraft(reference, sink)
  return geoPersistenceDraft(reference, sink)
}

const mappingDraft = (node: ModelRecord): NodeDraft => {
  const reference = textOf(node.mappingRef)

  return {
    type: PIPELINE_NODE_TYPES.Mapping,
    data: {
      mappingRef: reference,
      mappingLogicalUrn: reference ? toLogicalUrn(reference) : undefined,
      configured: reference !== undefined,
    },
  }
}

const cronDraft = (node: ModelRecord): NodeDraft => {
  const cronExpression = textOf(node.cronExpression) ?? ''

  return {
    type: PIPELINE_NODE_TYPES.Cron,
    data: { cronExpression, cronPreview: textOf(node.cronPreview), configured: cronExpression !== '' },
  }
}

const draftOf = (node: ModelRecord, lookups: Lookups): NodeDraft | undefined => {
  switch (node.kind) {
    case MODEL_KINDS.start:
      return { type: PIPELINE_NODE_TYPES.Start, data: {} }
    case MODEL_KINDS.end:
      return { type: PIPELINE_NODE_TYPES.End, data: {} }
    case MODEL_KINDS.cron:
      return cronDraft(node)
    case MODEL_KINDS.source:
      return sourceDraft(node, lookups)
    case MODEL_KINDS.mapping:
      return mappingDraft(node)
    case MODEL_KINDS.sink:
      return sinkDraft(node, lookups)
    default:
      // The model can hold kinds the editor has no node for (filter, enrich, split).
      return undefined
  }
}

const toEditorNode = (
  node: ModelRecord,
  index: number,
  lookups: Lookups,
  context: ModelHydrationContext,
): PipelineNode | undefined => {
  const id = textOf(node.id)
  const draft = draftOf(node, lookups)
  if (!id || !draft) return undefined

  const defaults = context.createDefaultData(draft.type)
  if (!defaults) return undefined

  return {
    id,
    type: draft.type,
    position: positionOf(node, index),
    data: {
      ...defaults,
      ...withoutUndefined(draft.data),
      label: textOf(node.label) ?? defaults.label,
    } as PipelineNodeData,
  }
}

// ============================================================================
// Edges
// ============================================================================

const toEditorEdge = (edge: ModelRecord): PipelineEdge | undefined => {
  const id = textOf(edge.id)
  const source = textOf(edge.source)
  const target = textOf(edge.target)
  if (!id || !source || !target) return undefined

  return { id, source, target, type: PIPELINE_EDGE_TYPE, data: { label: textOf(edge.label) ?? '' } }
}

// ============================================================================
// FROST: the target of the last mapping
// ============================================================================

const nearestUpstreamMappingId = (
  sinkNodeId: string,
  nodesById: Map<string, PipelineNode>,
  edges: readonly PipelineEdge[],
): string | undefined => {
  const queue = [sinkNodeId]
  const visited = new Set(queue)

  while (queue.length > 0) {
    const current = queue.shift()
    const incoming = edges.filter(edge => edge.target === current)
    for (const edge of incoming) {
      if (visited.has(edge.source)) continue
      visited.add(edge.source)
      const node = nodesById.get(edge.source)
      if (node && isMappingNodeData(node.data)) return node.id
      queue.push(edge.source)
    }
  }
  return undefined
}

/**
 * A FROST sink stores the target structure of the mapping in front of it, and the editor sends
 * that value again on each change of the sink. The mapping node thus gets its target from the
 * stored sink. Without it, a change of the port would remove the structure from the sink.
 */
const withFrostMappingTargets = (
  nodes: PipelineNode[],
  edges: readonly PipelineEdge[],
  lookups: Lookups,
): PipelineNode[] => {
  const nodesById = new Map(nodes.map(node => [node.id, node]))
  const targetByMappingId = new Map<string, string>()

  for (const node of nodes) {
    if (!isFrostNodeData(node.data) || !node.data.entityId) continue
    const element = lookups.sinksById.get(node.data.entityId)?.configuration?.element
    const mappingId = nearestUpstreamMappingId(node.id, nodesById, edges)
    if (element && mappingId) targetByMappingId.set(mappingId, element)
  }

  return nodes.map(node => {
    const target = targetByMappingId.get(node.id)
    if (!target || !isMappingNodeData(node.data)) return node
    const data: PipelineNodeData = { ...node.data, mappingConfig: { ...node.data.mappingConfig, target } }
    return { ...node, data }
  })
}

// ============================================================================
// Hydration
// ============================================================================

/**
 * Derives the editor graph of a pipeline from its CORE model.
 *
 * A node the editor cannot draw is left out, but its edges stay. The edge then points at a node
 * that is not there, and the validation refuses to save the pipeline. This is the intent: an
 * incomplete graph must not replace a complete model.
 */
export const hydrateFromCoreModel = (
  dto: PipelineOutputDTO,
  context: ModelHydrationContext,
): Pick<Pipeline, 'nodes' | 'edges'> => {
  const modelNodes = modelNodesOf(dto)
  const lookups = buildLookups(dto, modelNodes, context)

  const nodes: PipelineNode[] = []
  modelNodes.forEach((modelNode, index) => {
    const node = toEditorNode(modelNode, index, lookups, context)
    if (node) nodes.push(node)
  })

  const edges: PipelineEdge[] = []
  for (const modelEdge of modelEdgesOf(dto)) {
    const edge = toEditorEdge(modelEdge)
    if (edge) edges.push(edge)
  }

  return { nodes: withFrostMappingTargets(nodes, edges, lookups), edges }
}
