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

import type { ApiNodeData, DataSourceNodeData } from '../_types/nodes'
import { isApiNodeData, isDataSourceNodeData, isFrostNodeData } from '../_types/nodes'
import type { Pipeline, PipelinePayload, PipelineStylesPayload } from '../_types/pipeline'
import { buildRedPandaConnectModel } from './modelBuilderService'

/**
 * Builds the complete PipelinePayload for backend API submission.
 *
 * @param pipeline - The pipeline with nodes and edges
 * @returns The payload ready to be sent to `POST /pipeline`
 */
export const buildPipelinePayload = (pipeline: Pipeline): PipelinePayload => {
  // 1. Extract styles (viewport + node positions for reload)
  const styles: PipelineStylesPayload = {
    viewport: pipeline.viewport,
    nodePositions: Object.fromEntries(pipeline.nodes.map(node => [node.id, node.position])),
  }

  // 2. Extract entity data by node type

  // DataSources: numeric entity IDs from configured DataSource nodes
  const dataSources: number[] = pipeline.nodes
    .filter(n => isDataSourceNodeData(n.data) && n.data.entityId != null)
    .map(n => (n.data as DataSourceNodeData).entityId as number)

  // APIs: unique apiPath strings from ApiRequest/ApiResponse nodes
  const apis: string[] = [
    ...new Set(pipeline.nodes.filter(n => isApiNodeData(n.data)).map(n => (n.data as ApiNodeData).apiPath)),
  ]

  // Persistences: hardcoded ["frost"] if any FROST node exists
  const hasFrostNode = pipeline.nodes.some(n => isFrostNodeData(n.data))
  const persistences: string[] = hasFrostNode ? ['frost'] : []

  // 3. Build RedPandaConnect model
  const model = buildRedPandaConnectModel(pipeline)

  // 4. Assemble payload
  return {
    name: pipeline.name,
    description: '-',
    styles,
    dataSources,
    apis,
    persistences,
    model: model ? JSON.stringify(model) : '',
  }
}
