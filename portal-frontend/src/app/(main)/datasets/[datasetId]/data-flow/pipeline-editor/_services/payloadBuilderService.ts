/**
 * Payload Builder Service
 *
 * Assembles the complete PipelinePayload from a Pipeline graph for backend API submission.
 *
 * The backend is a thin shell that stores the pipeline `model` verbatim and never parses it;
 * Model Forge validates it against `pipeline.schema.json`. So the FRONTEND emits a clean, URN-native
 * CORE Pipeline document (nodes keyed by `kind` with `*Ref` URNs — no React-Flow `type`/`data`) and
 * validates its own payload with the generated zod schema before sending. `styles` still carries the
 * full React-Flow graph for editor round-tripping (the LOAD path hydrates from `styles`).
 */

import type { MappingArtifactBody } from '@/app/services/api/mappings/clientRequests'
import { DataSinkDraftSchema, MappingDraftSchema, PipelineDraftSchema } from '@/generated/core'
import { DATASINK_TYPES, type DataSinkPayload } from '@/types/datasinks'

import type { DataSourceNodeData, PipelineNodeData } from '../_types/nodes'
import {
  isCronNodeData,
  isDataSourceNodeData,
  isFrostNodeData,
  isGeoPersistenceNodeData,
  isMappingNodeData,
} from '../_types/nodes'
import type {
  CorePipelineEdge,
  CorePipelineModel,
  CorePipelineNode,
  CorePipelineNodeKind,
  Pipeline,
  PipelineNode,
  PipelineNodeType,
  PipelinePayload,
  PipelineStylesPayload,
} from '../_types/pipeline'

/** Maps the React-Flow node `type` to the CORE document `kind`. */
const KIND_BY_NODE_TYPE: Record<PipelineNodeType, CorePipelineNodeKind> = {
  dataSource: 'source',
  frost: 'sink',
  geoPersistence: 'sink',
  mapping: 'mapping',
  cron: 'cron',
  start: 'start',
  end: 'end',
}

/**
 * Thrown when the assembled CORE Pipeline document fails the generated schema. Carries the zod
 * issues (already logged to the console) so the caller can block the save and surface a toast.
 */
export class PipelineModelValidationError extends Error {
  constructor(public readonly issues: unknown) {
    super('Pipeline model failed CORE schema validation')
    this.name = 'PipelineModelValidationError'
  }
}

/**
 * Thrown when a mapping document (for the given node) fails the generated schema. Carries the zod
 * issues (already logged to the console) so the caller can block the save and surface a toast.
 */
export class MappingDocumentValidationError extends Error {
  constructor(
    public readonly nodeId: string,
    public readonly issues: unknown,
  ) {
    super('Mapping document failed CORE schema validation')
    this.name = 'MappingDocumentValidationError'
  }
}

/**
 * Thrown when a data sink's CORE document (for the given node) fails the generated schema. Carries
 * the zod issues (already logged to the console) so the caller can block the save and surface a toast.
 */
export class DataSinkDocumentValidationError extends Error {
  constructor(
    public readonly nodeId: string,
    public readonly issues: unknown,
  ) {
    super('DataSink document failed CORE schema validation')
    this.name = 'DataSinkDocumentValidationError'
  }
}

/**
 * Builds the clean, URN-native CORE Pipeline document (the payload `model`). References are read from
 * node data (resolved from the pickers / stashed after datasink & mapping saves) and omitted when a
 * node is not yet configured — draft pipelines are valid, refs are optional in the schema.
 * `$schema`/`id` are intentionally left out (Model Forge stamps those on ingest).
 */
export const buildPipelineModel = (pipeline: Pipeline): CorePipelineModel => {
  const nodes: CorePipelineNode[] = pipeline.nodes.map(node => {
    const coreNode: CorePipelineNode = {
      id: node.id,
      kind: KIND_BY_NODE_TYPE[node.type],
      'x-ui-position': { x: node.position.x, y: node.position.y },
    }
    if (node.data.label) coreNode.label = node.data.label

    if (isDataSourceNodeData(node.data)) {
      if (node.data.configurationUrn) coreNode.sourceRef = node.data.configurationUrn
    } else if (isFrostNodeData(node.data) || isGeoPersistenceNodeData(node.data)) {
      if (node.data.configurationUrn) coreNode.sinkRef = node.data.configurationUrn
    } else if (isMappingNodeData(node.data)) {
      if (node.data.mappingRef) coreNode.mappingRef = node.data.mappingRef
    } else if (isCronNodeData(node.data)) {
      if (node.data.cronExpression) coreNode.cronExpression = node.data.cronExpression
    }

    return coreNode
  })

  const edges: CorePipelineEdge[] = pipeline.edges.map(edge => {
    const coreEdge: CorePipelineEdge = { id: edge.id, source: edge.source, target: edge.target }
    if (edge.data?.label) coreEdge.label = edge.data.label
    return coreEdge
  })

  return { nodes, edges }
}

/**
 * Builds the complete PipelinePayload for backend API submission and validates the CORE `model`
 * against the generated schema before returning.
 *
 * @param pipeline - The pipeline with nodes and edges (with URNs already stashed onto node data)
 * @returns The payload ready to be sent to `POST /pipeline`
 * @throws PipelineModelValidationError when the assembled CORE document is not schema-valid
 */
export const buildPipelinePayload = (pipeline: Pipeline): PipelinePayload => {
  // 1. styles — the full React-Flow graph for editor round-tripping (LOAD path hydrates from this).
  const styles: PipelineStylesPayload = {
    viewport: pipeline.viewport,
    nodePositions: Object.fromEntries(pipeline.nodes.map(node => [node.id, node.position])),
    nodes: pipeline.nodes,
    edges: pipeline.edges,
  }

  // 2. model — the clean, URN-native CORE Pipeline document; validate it before sending so the
  //    frontend guarantees a schema-valid payload to the (schema-agnostic) backend.
  const model = buildPipelineModel(pipeline)
  const parsed = PipelineDraftSchema.safeParse(model)
  if (!parsed.success) {
    console.error('Pipeline model failed CORE schema validation:', parsed.error.issues, model)
    throw new PipelineModelValidationError(parsed.error.issues)
  }

  // 3. FK wiring ids (portal GUIDs) — the backend uses these for foreign-key wiring.
  const dataSourceIds: string[] = pipeline.nodes
    .filter(n => isDataSourceNodeData(n.data) && n.data.entityId != null)
    .map(n => (n.data as DataSourceNodeData).entityId as string)

  const dataSinkIds: string[] = pipeline.nodes
    .filter(n => (isGeoPersistenceNodeData(n.data) || isFrostNodeData(n.data)) && n.data.entityId != null)
    .map(n => n.data.entityId as string)

  return {
    name: pipeline.name,
    description: pipeline.description || '-',
    styles,
    model,
    dataSourceIds,
    dataSinkIds,
  }
}

// ============================================================================
// Mapping artifact extraction (POST/PUT /v1/mappings)
// ============================================================================

/**
 * A mapping artifact to create/version for a single mapping node, extracted on save (before the
 * pipeline itself is saved). `logicalUrn` is present when the node was saved before — the caller then
 * PUT-versions that logical mapping instead of POSTing a new one.
 */
export interface MappingArtifactRequest {
  /** The pipeline node this mapping belongs to. */
  nodeId: string
  /** Prior logical URN (PUT-version) or undefined (POST a new mapping). */
  logicalUrn?: string
  /** The validated CORE Mapping document body to send. */
  body: MappingArtifactBody
}

/** A readable mapping title, e.g. `Source-to-Target`. */
const mappingTitle = (data: PipelineNodeData): string => {
  const source = (isMappingNodeData(data) && data.sourceName) || 'source'
  const target = (isMappingNodeData(data) && data.targetName) || 'target'
  return `${source}-to-${target}`
}

/**
 * Extracts the mapping artifacts to create/version from all configured mapping nodes and validates
 * each document against the generated schema before it is sent. A mapping node is "configured" once
 * it has both a source and target DataStructure URN; unconfigured nodes are skipped (no `mappingRef`).
 *
 * @throws MappingDocumentValidationError when a mapping document is not schema-valid
 */
export const buildMappingArtifacts = (pipeline: Pipeline): MappingArtifactRequest[] => {
  return pipeline.nodes.flatMap<MappingArtifactRequest>(node => {
    if (!isMappingNodeData(node.data)) return []
    const config = node.data.mappingConfig
    if (!config?.source || !config?.target) return []

    const body: MappingArtifactBody = {
      source: config.source,
      target: config.target,
      fields: config.fields,
      title: mappingTitle(node.data),
      positions: config.positions,
    }

    const parsed = MappingDraftSchema.safeParse(body)
    if (!parsed.success) {
      console.error('Mapping document failed CORE schema validation:', node.id, parsed.error.issues, body)
      throw new MappingDocumentValidationError(node.id, parsed.error.issues)
    }

    return [{ nodeId: node.id, logicalUrn: node.data.mappingLogicalUrn, body }]
  })
}

/**
 * Snapshot type for mapping change detection: maps nodeId → JSON-stringified artifact body.
 */
export type MappingSnapshot = Record<string, string>

/**
 * Creates a snapshot of the current mapping artifact bodies for later change detection.
 */
export const createMappingSnapshot = (pipeline: Pipeline): MappingSnapshot => {
  const snapshot: MappingSnapshot = {}
  for (const { nodeId, body } of buildMappingArtifacts(pipeline)) {
    snapshot[nodeId] = JSON.stringify(body)
  }
  return snapshot
}

/**
 * Checks whether a single mapping artifact body has changed compared to the saved snapshot.
 * Returns true if the mapping is new (not in snapshot) or its body differs.
 */
export const hasMappingChanged = (nodeId: string, body: MappingArtifactBody, snapshot: MappingSnapshot): boolean => {
  const entry = snapshot[nodeId]
  if (!entry) return true // new node, not in snapshot
  return JSON.stringify(body) !== entry
}

// ============================================================================
// Data sink Payload Extraction & Change Detection
// ============================================================================

/**
 * Represents an extracted data sink payload tied to a specific pipeline node.
 */
export interface DataSinkNodePayload {
  /** The pipeline node ID this data sink belongs to */
  nodeId: string
  /** The existing backend data sink ID, or null for new data sinks */
  entityId: string | null
  /** The payload ready for POST/PUT to the data sinks API */
  payload: DataSinkPayload
}

/**
 * Extracts data sink payloads from all data sink nodes in the pipeline.
 * Returns an array of payloads with their associated node IDs and entity IDs.
 */
export const buildDataSinkPayloads = (pipeline: Pipeline): DataSinkNodePayload[] => {
  return pipeline.nodes.flatMap<DataSinkNodePayload>(node => {
    // `element` is the versioned CORE URN of the sink's target structure (a Model-Forge soft reference,
    // not a raw version id). POSTGIS uses its own data structure, FROST the mapping's target.
    let payload: DataSinkPayload | null = null
    if (isGeoPersistenceNodeData(node.data) && node.data.dataStructureVersionId != null) {
      const { tableName, dataStructureUrn } = node.data
      payload = {
        id: node.data.entityId ?? null,
        dataSinkType: DATASINK_TYPES.POSTGIS,
        configuration: dataStructureUrn ? { tableName, element: dataStructureUrn } : { tableName },
      }
    } else if (isFrostNodeData(node.data)) {
      const elementUrn = mappingTargetElementBefore(pipeline, node.id)
      payload = {
        id: node.data.entityId ?? null,
        dataSinkType: DATASINK_TYPES.FROST,
        configuration: elementUrn ? { element: elementUrn } : {},
      }
    }

    if (payload == null) return []

    // Validate the outbound CORE DataSink document (envelope + connector fields) against the generated
    // schema before sending — mirroring pipeline/mapping. `$schema`/`id` are stamped by Model Forge;
    // the backend adds `connectionType` from `dataSinkType`, so validate that projection here.
    const coreDoc = { connectionType: payload.dataSinkType.toLowerCase(), ...payload.configuration }
    const parsed = DataSinkDraftSchema.safeParse(coreDoc)
    if (!parsed.success) {
      console.error('DataSink document failed CORE schema validation:', node.id, parsed.error.issues, coreDoc)
      throw new DataSinkDocumentValidationError(node.id, parsed.error.issues)
    }

    return [{ nodeId: node.id, entityId: payload.id, payload }]
  })
}

/**
 * The versioned CORE URN of the target DataStructure of the last mapping feeding the given sink
 * node — a backward walk stopping at the first mapping it reaches. The URN comes from the mapping's
 * saved config (`mappingConfig.target`, built via buildDataStructureUrn), which is exactly the
 * soft reference the backend sink stores (FrostConfiguration.element). The flow-shape validation
 * only lets a single linear path deploy, so on a valid graph exactly one final mapping exists; on
 * an invalid multi-branch canvas the pick is arbitrary but the deploy is blocked anyway.
 */
const mappingTargetElementBefore = (pipeline: Pipeline, sinkNodeId: string): string | null => {
  const incoming = new Map<string, string[]>()
  pipeline.edges.forEach(edge => {
    incoming.set(edge.target, [...(incoming.get(edge.target) ?? []), edge.source])
  })
  const nodesById = new Map(pipeline.nodes.map(node => [node.id, node]))

  const queue = [sinkNodeId]
  const visited = new Set(queue)
  while (queue.length > 0) {
    for (const previous of incoming.get(queue.shift() as string) ?? []) {
      if (visited.has(previous)) continue
      visited.add(previous)
      const node = nodesById.get(previous)
      if (node && isMappingNodeData(node.data)) {
        return node.data.mappingConfig?.target ?? null
      }
      queue.push(previous)
    }
  }
  return null
}

/**
 * Snapshot entry for a single data sink node: tracks both the entityId and configuration.
 */
export interface DataSinkSnapshotEntry {
  /** The backend data sink ID at snapshot time, or null for unsaved nodes */
  entityId: string | null
  /** JSON-stringified payload config (dataSinkType + configuration, excluding `id`) */
  configJson: string
}

/**
 * Snapshot type for change detection: maps nodeId → snapshot entry.
 */
export type DataSinkSnapshot = Record<string, DataSinkSnapshotEntry>

/**
 * Creates a snapshot of the current data sink payloads for later change detection.
 * The snapshot stores the entityId and a JSON string of the payload configuration
 */
export const createDataSinkSnapshot = (pipeline: Pipeline): DataSinkSnapshot => {
  const payloads = buildDataSinkPayloads(pipeline)
  const snapshot: DataSinkSnapshot = {}
  for (const { nodeId, entityId, payload } of payloads) {
    const { id: _id, ...comparable } = payload
    snapshot[nodeId] = {
      entityId,
      configJson: JSON.stringify(comparable),
    }
  }
  return snapshot
}

/**
 * Checks whether a single data sink payload has changed compared to the saved snapshot.
 * Returns true if the data sink is new (not in snapshot) or its configuration differs.
 */
export const hasDataSinkChanged = (nodeId: string, payload: DataSinkPayload, snapshot: DataSinkSnapshot): boolean => {
  const entry = snapshot[nodeId]
  if (!entry) return true // new node, not in snapshot

  const { id: _id, ...comparable } = payload
  return JSON.stringify(comparable) !== entry.configJson
}

/**
 * Whether saving this sink would rebuild its backing storage, discarding stored data. For a POSTGIS
 * sink any change to `tableName` or the referenced `element` is destructive (PostGIS has no ALTER
 * TABLE — a column change is drop+recreate); for a FROST sink only `element` applies (there is no
 * `tableName`). Only an UPDATE of an existing sink (present in the snapshot) can lose data — a
 * brand-new sink has no storage yet, so it never triggers the warning.
 */
export const isDestructiveDataSinkChange = (
  nodeId: string,
  payload: DataSinkPayload,
  snapshot: DataSinkSnapshot,
): boolean => {
  const entry = snapshot[nodeId]
  if (!entry || entry.entityId == null) return false // new sink, no table to lose

  const previous = JSON.parse(entry.configJson) as Omit<DataSinkPayload, 'id'>
  return elementOf(payload) !== elementOf(previous) || tableNameOf(payload) !== tableNameOf(previous)
}

const tableNameOf = (payload: Pick<DataSinkPayload, 'configuration'>): string | undefined =>
  'tableName' in payload.configuration ? payload.configuration.tableName : undefined

const elementOf = (payload: Pick<DataSinkPayload, 'configuration'>): string | undefined => payload.configuration.element

/**
 * Finds data sink IDs that were in the snapshot but no longer exist in the current pipeline.
 */
export const getRemovedDataSinkIds = (pipeline: Pipeline, snapshot: DataSinkSnapshot): string[] => {
  const currentNodeIds = new Set(pipeline.nodes.map(n => n.id))
  return Object.entries(snapshot)
    .filter(([nodeId, entry]) => !currentNodeIds.has(nodeId) && entry.entityId != null)
    .map(([, entry]) => entry.entityId as string)
}

/**
 * Merges a partial data patch into a single node's data. Returns the updated pipeline. Used to stash
 * server-assigned URNs (datasink `configurationUrn`, mapping `mappingRef`/`mappingLogicalUrn`) back
 * onto the node so they are emitted in the CORE `model` and round-trip via `styles`.
 */
export const updateNodeData = (pipeline: Pipeline, nodeId: string, patch: Partial<PipelineNodeData>): Pipeline => {
  return {
    ...pipeline,
    nodes: pipeline.nodes.map(node =>
      node.id === nodeId ? ({ ...node, data: { ...node.data, ...patch } } as PipelineNode) : node,
    ),
  }
}

/**
 * Updates a single node's entityId in the pipeline. Returns the updated pipeline.
 */
export const updateNodeEntityId = (pipeline: Pipeline, nodeId: string, entityId: string): Pipeline => {
  return updateNodeData(pipeline, nodeId, { entityId } as Partial<PipelineNodeData>)
}
