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
 * A mapping node also needs the stored mapping document, and the ids of the data structures it maps
 * between, because the mapping editor opens only with both. The caller supplies the documents and
 * the data structures the user can read.
 *
 * Nothing is written on load. The derived graph becomes `styles` when the user saves the pipeline.
 *
 * Kept React-free: the default data of a node comes from the caller, because the node registry
 * imports the inspector panels.
 */

import { MappingDraftSchema } from '@/generated/core'
import { DATASINK_TYPES, type DataSinkType } from '@/types/datasinks'
import type { Datasource } from '@/types/datasources'
import { buildDataStructureLogicalUrn, toLogicalUrn, versionOf } from '@/utils/urn'

import type { MappingConfig } from '../_components/mapping-editor/_types'
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

/** What the hydration reads from a data structure that the user can read. */
export interface HydrationDataStructure {
  id: string
  name: string
  dataStructureVersions: readonly { id: string; version: string | null }[]
}

/** A stored mapping document as the backend returns it: its rules, and the editor's node layout. */
export type HydrationMappingDocument = Record<string, unknown>

export interface ModelHydrationContext {
  dataSinks: readonly HydrationDataSink[]
  dataSources: readonly HydrationDataSource[]
  /** The stored mapping documents, by the reference that a pipeline node holds (`mappingRef`). */
  mappings?: ReadonlyMap<string, HydrationMappingDocument>
  /** The data structures that the user can read, to find the ids behind the URNs of a mapping. */
  dataStructures?: readonly HydrationDataStructure[]
  /** The data a new node of this type starts with. The node registry is the source. */
  createDefaultData: (type: PipelineNodeType) => PipelineNodeData | undefined
}

type ModelRecord = Record<string, unknown>

interface NodeDraft {
  type: PipelineNodeType
  data: Record<string, unknown>
}

/** The ids that the mapping editor needs to load one data structure version. */
interface StructureVersionIds {
  datastructureId: string
  versionId: string
}

interface Lookups {
  sourcesByUrn: Map<string, HydrationDataSource>
  sinksByUrn: Map<string, HydrationDataSink>
  sinksById: Map<string, HydrationDataSink>
  mappingsByRef: ReadonlyMap<string, HydrationMappingDocument>
  structuresByUrn: Map<string, HydrationDataStructure>
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

/**
 * The mapping references of the pipelines that are drawn from their model, each one time. The
 * caller reads these mapping documents before it draws the pipelines.
 */
export const mappingRefsToHydrate = (dtos: readonly Pick<PipelineOutputDTO, 'styles' | 'model'>[]): string[] => {
  const references = new Set<string>()
  for (const dto of dtos) {
    if (!needsModelHydration(dto)) continue
    for (const node of modelNodesOf(dto)) {
      const reference = node.kind === MODEL_KINDS.mapping ? textOf(node.mappingRef) : undefined
      if (reference) references.add(reference)
    }
  }
  return [...references]
}

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

/**
 * The data structures by their logical CORE URN. The list does not carry the URN, so it is made
 * from the name and the id, with the rule the editor uses when it saves a mapping
 * (`buildDataStructureUrn` in `MappingEditorModal`). A renamed structure thus does not match, as it
 * does not match there.
 */
const byStructureUrn = (structures: readonly HydrationDataStructure[]): Map<string, HydrationDataStructure> => {
  const lookup = new Map<string, HydrationDataStructure>()
  for (const structure of structures) {
    try {
      lookup.set(buildDataStructureLogicalUrn(structure.name, structure.id), structure)
    } catch {
      // A name without a URN segment, or an id that is not a UUID, gives no URN to compare.
    }
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
    mappingsByRef: context.mappings ?? new Map(),
    structuresByUrn: byStructureUrn(context.dataStructures ?? []),
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

/**
 * The rules of a stored mapping in the form the mapping editor holds them, or `undefined`. A save
 * sends them back unchanged, so a document that the editor's own schema refuses is not taken over:
 * it would block each save of the pipeline. The node then keeps only its reference.
 */
const mappingConfigOf = (document: HydrationMappingDocument): MappingConfig | undefined => {
  const source = textOf(document.source)
  const target = textOf(document.target)
  const fields = isRecord(document.fields) ? document.fields : {}
  const positions = isRecord(document.positions) ? document.positions : {}
  if (!source || !target) return undefined
  if (!MappingDraftSchema.safeParse({ source, target, fields, positions }).success) return undefined

  return {
    source,
    target,
    fields: fields as MappingConfig['fields'],
    positions: positions as MappingConfig['positions'],
  }
}

/** The ids of the data structure version that a versioned structure URN names, when the user can read it. */
const structureVersionOf = (urn: string, lookups: Lookups): StructureVersionIds | undefined => {
  const structure = lookups.structuresByUrn.get(toLogicalUrn(urn))
  const version = versionOf(urn)
  const match = structure?.dataStructureVersions.find(candidate => candidate.version === version)
  return structure && match ? { datastructureId: structure.id, versionId: match.id } : undefined
}

/**
 * What a stored mapping document adds to the node: its rules, its title, and the data structures
 * that the mapping editor opens with. A structure that the list does not contain (deleted, a
 * structure the platform publishes, no permission to read it) stays unbound. The node then shows
 * the rules, but the mapping editor stays closed, as it is for a node without a document.
 */
const storedMappingData = (document: HydrationMappingDocument, lookups: Lookups): Record<string, unknown> => {
  const mappingConfig = mappingConfigOf(document)
  if (!mappingConfig?.source || !mappingConfig.target) return {}

  const source = structureVersionOf(mappingConfig.source, lookups)
  const target = structureVersionOf(mappingConfig.target, lookups)
  return {
    mappingConfig,
    mappingTitle: textOf(document.title),
    sourceDatastructureId: source?.datastructureId,
    sourceVersionId: source?.versionId,
    targetDatastructureId: target?.datastructureId,
    targetVersionId: target?.versionId,
  }
}

const mappingDraft = (node: ModelRecord, lookups: Lookups): NodeDraft => {
  const reference = textOf(node.mappingRef)
  const document = reference ? lookups.mappingsByRef.get(reference) : undefined

  return {
    type: PIPELINE_NODE_TYPES.Mapping,
    data: {
      mappingRef: reference,
      mappingLogicalUrn: reference ? toLogicalUrn(reference) : undefined,
      configured: reference !== undefined,
      // The registry checked the rules when they were stored. The mapping editor did not.
      isStoredOutsideEditor: reference !== undefined ? true : undefined,
      ...(document ? storedMappingData(document, lookups) : {}),
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
      return mappingDraft(node, lookups)
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
 * stored sink. Without it, a change of the port would remove the structure from the sink. A mapping
 * whose stored document names a target keeps that target.
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
    if (!target || !isMappingNodeData(node.data) || node.data.mappingConfig?.target) return node
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
