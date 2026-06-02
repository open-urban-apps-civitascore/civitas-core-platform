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

import type { DataSourceNodeData, GeoPersistenceNodeData } from '../_types/nodes'
import { isDataSourceNodeData, isGeoPersistenceNodeData } from '../_types/nodes'
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

  // DataSources: numeric entity IDs from configured DataSource nodes
  const dataSourceIds: string[] = pipeline.nodes
    .filter(n => isDataSourceNodeData(n.data) && n.data.entityId != null)
    .map(n => (n.data as DataSourceNodeData).entityId as string)
  // Persistences: dataStructureVersionIds from configured GeoPersistence nodes
  const persistences: number[] = pipeline.nodes
    .filter(n => isGeoPersistenceNodeData(n.data) && n.data.dataStructureVersionId != null)
    .map(n => Number((n.data as GeoPersistenceNodeData).dataStructureVersionId))
    .filter(id => !isNaN(id))

  // 3. Build RedPandaConnect model
  const model = buildRedPandaConnectModel(pipeline)

  // 4. Assemble payload — styles is JSON-stringified for the backend
  return {
    name: pipeline.name,
    description: pipeline.description || '-',
    styles: styles,
    dataSourceIds,
    persistences,
    model: model || {},
  }
}
