/**
 * Payload Builder Service
 *
 * Assembles the complete PipelinePayload from a Pipeline graph
 * for backend API submission.
 *
 * Extracts:
 * - Entity IDs from configured nodes
 * - React Flow styles (viewport + positions) for frontend reload
 * - The engine-neutral pipeline graph (React-Flow nodes/edges) forwarded to the config-adapter as-is
 */

import { DATASINK_TYPES, type DataSinkPayload } from '@/types/datasinks'

import type { DataSourceNodeData } from '../_types/nodes'
import { isDataSourceNodeData, isFrostNodeData, isGeoPersistenceNodeData } from '../_types/nodes'
import type { Pipeline, PipelinePayload, PipelineStylesPayload } from '../_types/pipeline'

/**
 * Builds the complete PipelinePayload for backend API submission.
 *
 * @param pipeline - The pipeline with nodes and edges
 * @returns The payload ready to be sent to `POST /pipeline`
 */
export const buildPipelinePayload = (pipeline: Pipeline): PipelinePayload => {
  // 1. Extract styles (viewport + node positions + full graph for round-tripping)
  const styles: PipelineStylesPayload = {
    viewport: pipeline.viewport,
    nodePositions: Object.fromEntries(pipeline.nodes.map(node => [node.id, node.position])),
    nodes: pipeline.nodes,
    edges: pipeline.edges,
  }

  // 2. Extract entity data by node type

  // DataSources: entity IDs from configured DataSource nodes
  const dataSourceIds: string[] = pipeline.nodes
    .filter(n => isDataSourceNodeData(n.data) && n.data.entityId != null)
    .map(n => (n.data as DataSourceNodeData).entityId as string)

  // DataSinks: only IDs (config is saved separately via data sink API)
  const dataSinkIds: string[] = pipeline.nodes
    .filter(n => (isGeoPersistenceNodeData(n.data) || isFrostNodeData(n.data)) && n.data.entityId != null)
    .map(n => n.data.entityId as string)

  // 3. Assemble payload. `model` is the engine-neutral pipeline graph (React-Flow nodes/edges +
  //    inline mappingConfig) that the backend forwards to the config-adapter as-is; the
  //    config-adapter (NiFi) is the only place engine specifics appear. `styles` carries the same
  //    React-Flow layout for editor round-tripping. No engine-specific (RedPanda) model is built
  //    on the frontend anymore.
  return {
    name: pipeline.name,
    description: pipeline.description || '-',
    styles: styles,
    model: styles,
    dataSourceIds,
    dataSinkIds,
  }
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
    if (isGeoPersistenceNodeData(node.data) && node.data.dataStructureVersionId != null) {
      return [
        {
          nodeId: node.id,
          entityId: node.data.entityId ?? null,
          payload: {
            id: node.data.entityId ?? null,
            dataSinkType: DATASINK_TYPES.POSTGIS,
            configuration: {
              tableName: node.data.tableName,
              dataStructureVersionId: node.data.dataStructureVersionId.split('/')[1],
            },
          },
        },
      ]
    }
    if (isFrostNodeData(node.data)) {
      return [
        {
          nodeId: node.id,
          entityId: node.data.entityId ?? null,
          payload: {
            id: node.data.entityId ?? null,
            dataSinkType: DATASINK_TYPES.FROST,
            configuration: {} as Record<string, never>,
          },
        },
      ]
    }
    return []
  })
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
 * Finds data sink IDs that were in the snapshot but no longer exist in the current pipeline.
 */
export const getRemovedDataSinkIds = (pipeline: Pipeline, snapshot: DataSinkSnapshot): string[] => {
  const currentNodeIds = new Set(pipeline.nodes.map(n => n.id))
  return Object.entries(snapshot)
    .filter(([nodeId, entry]) => !currentNodeIds.has(nodeId) && entry.entityId != null)
    .map(([, entry]) => entry.entityId as string)
}

/**
 * Updates a single node's entityId in the pipeline. Returns the updated pipeline.
 */
export const updateNodeEntityId = (pipeline: Pipeline, nodeId: string, entityId: string): Pipeline => {
  return {
    ...pipeline,
    nodes: pipeline.nodes.map(node => (node.id === nodeId ? { ...node, data: { ...node.data, entityId } } : node)),
  }
}
