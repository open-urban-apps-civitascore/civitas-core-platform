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

import { DATASINK_TYPES, type PipelineDatasink } from '@/types/datasinks'

import type { DataSourceNodeData } from '../_types/nodes'
import { isDataSourceNodeData, isGeoPersistenceNodeData } from '../_types/nodes'
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

  // DataSources: numeric entity IDs from configured DataSource nodes
  const dataSourceIds: string[] = pipeline.nodes
    .filter(n => isDataSourceNodeData(n.data) && n.data.entityId != null)
    .map(n => (n.data as DataSourceNodeData).entityId as string)
  const dataSinks: PipelineDatasink[] = pipeline.nodes.flatMap<PipelineDatasink>(n => {
    if (isGeoPersistenceNodeData(n.data) && n.data.dataStructureVersionId != null) {
      return [
        {
          id: n.data.entityId ?? null,
          dataSinkType: DATASINK_TYPES.POSTGIS,
          configuration: {
            tableName: n.data.tableName,
            dataStructureVersionId: n.data.dataStructureVersionId.split('/')[1],
          },
        },
      ]
    }
    return []
  })

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
    dataSinks: dataSinks,
  }
}

/**
 * Writes datasink IDs from a save response back into the matching pipeline nodes.
 * POSTGIS datasinks are matched by tableName.
 * Returns the updated pipeline and whether any entityId changed.
 */
export const syncDatasinkIds = (
  pipeline: Pipeline,
  responseDatasinks: PipelineDatasink[],
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
