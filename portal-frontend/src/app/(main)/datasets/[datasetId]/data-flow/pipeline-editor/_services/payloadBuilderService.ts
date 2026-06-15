/**
 * Payload Builder Service
 *
 * Assembles the complete PipelinePayload from a Pipeline graph
 * for backend API submission.
 *
 * Extracts:
 * - Entity IDs from configured nodes
 * - React Flow styles (viewport + positions) for frontend reload
 * - RedPandaConnect model from the graph
 */

import { DATASINK_TYPES, type DataSinkPayload } from '@/types/datasinks'

import type { DataSourceNodeData } from '../_types/nodes'
import { isDataSourceNodeData, isFrostNodeData, isGeoPersistenceNodeData } from '../_types/nodes'
import type { Pipeline, PipelinePayload, PipelineStylesPayload } from '../_types/pipeline'
import { buildRedPandaConnectModel } from './modelBuilderService'

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

  // DataSinks: only IDs (config is saved separately via datasink API)
  const dataSinkIds: string[] = pipeline.nodes
    .filter(n => (isGeoPersistenceNodeData(n.data) || isFrostNodeData(n.data)) && n.data.entityId != null)
    .map(n => n.data.entityId as string)

  // 3. Build RedPandaConnect model
  const model = buildRedPandaConnectModel(pipeline)

  // 4. Assemble payload — styles is JSON-stringified for the backend
  return {
    name: pipeline.name,
    description: pipeline.description || '-',
    styles: styles,
    dataSourceIds,
    dataSinkIds,
    model: model || {},
  }
}

/**
 * Writes datasink IDs from a save response back into the matching pipeline nodes.
 * POSTGIS datasinks are matched by tableName.
 * Returns the updated pipeline and whether any entityId changed.
 */
export const syncDatasinkIds = (
  pipeline: Pipeline,
  responseDatasinks: DataSinkPayload[],
): { pipeline: Pipeline; hasChanges: boolean } => {
  let hasChanges = false

  const updatedNodes = pipeline.nodes.map(node => {
    if (!isGeoPersistenceNodeData(node.data)) return node

    const match = responseDatasinks.find(
      d =>
        d.dataSinkType === DATASINK_TYPES.POSTGIS && d.id != null && d.configuration.tableName === node.data.tableName,
    )
    if (match?.id != null && match.id !== node.data.entityId) {
      hasChanges = true
      return { ...node, data: { ...node.data, entityId: match.id } }
    }
    return node
  })

  return {
    pipeline: hasChanges ? { ...pipeline, nodes: updatedNodes } : pipeline,
    hasChanges,
  }
}

// ============================================================================
// Datasink Payload Extraction & Change Detection
// ============================================================================

/**
 * Represents an extracted datasink payload tied to a specific pipeline node.
 */
export interface DatasinkNodePayload {
  /** The pipeline node ID this datasink belongs to */
  nodeId: string
  /** The existing backend datasink ID, or null for new datasinks */
  entityId: string | null
  /** The payload ready for POST/PUT to the datasinks API */
  payload: DataSinkPayload
}

/**
 * Extracts datasink payloads from all datasink nodes in the pipeline.
 * Returns an array of payloads with their associated node IDs and entity IDs.
 */
export const buildDatasinkPayloads = (pipeline: Pipeline): DatasinkNodePayload[] => {
  return pipeline.nodes.flatMap<DatasinkNodePayload>(node => {
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
 * Snapshot entry for a single datasink node: tracks both the entityId and configuration.
 */
export interface DatasinkSnapshotEntry {
  /** The backend datasink ID at snapshot time, or null for unsaved nodes */
  entityId: string | null
  /** JSON-stringified payload config (dataSinkType + configuration, excluding `id`) */
  configJson: string
}

/**
 * Snapshot type for change detection: maps nodeId → snapshot entry.
 */
export type DatasinkSnapshot = Record<string, DatasinkSnapshotEntry>

/**
 * Creates a snapshot of the current datasink payloads for later change detection.
 * The snapshot stores the entityId and a JSON string of the payload configuration
 */
export const createDatasinkSnapshot = (pipeline: Pipeline): DatasinkSnapshot => {
  const payloads = buildDatasinkPayloads(pipeline)
  const snapshot: DatasinkSnapshot = {}
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
 * Checks whether a single datasink payload has changed compared to the saved snapshot.
 * Returns true if the datasink is new (not in snapshot) or its configuration differs.
 */
export const hasDatasinkChanged = (nodeId: string, payload: DataSinkPayload, snapshot: DatasinkSnapshot): boolean => {
  const entry = snapshot[nodeId]
  if (!entry) return true // new node, not in snapshot

  const { id: _id, ...comparable } = payload
  return JSON.stringify(comparable) !== entry.configJson
}

/**
 * Finds datasink IDs that were in the snapshot but no longer exist in the current pipeline.
 */
export const getRemovedDatasinkIds = (pipeline: Pipeline, snapshot: DatasinkSnapshot): string[] => {
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
