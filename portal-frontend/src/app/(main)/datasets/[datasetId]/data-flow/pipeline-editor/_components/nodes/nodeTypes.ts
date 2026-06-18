/**
 * Node Types Registry
 *
 * Maps pipeline node type identifiers to their React components.
 * This registry is used by React Flow to render custom nodes.
 *
 */

import type { NodeTypes } from '@xyflow/react'

import { PIPELINE_NODE_TYPES } from '../../_types/pipeline'
import { EndNode } from './control/EndNode'
import { StartNode } from './control/StartNode'
import { DataSourceNode } from './source/DataSourceNode'
import { FrostNode } from './storage/FrostNode'
import { GeoPersistenceNode } from './storage/GeoPersistenceNode'
import { MappingNode } from './transform/MappingNode'
import { CronNode } from './trigger/CronNode'

// ============================================================================
// Node Types Registry
// ============================================================================

/**
 * Node types mapping for React Flow.
 * Keys must match the PipelineNodeType values.
 *
 */
export const pipelineNodeTypes: NodeTypes = {
  // Control nodes
  [PIPELINE_NODE_TYPES.Start]: StartNode,
  [PIPELINE_NODE_TYPES.End]: EndNode,

  // Trigger nodes
  [PIPELINE_NODE_TYPES.Cron]: CronNode,

  // Source nodes
  [PIPELINE_NODE_TYPES.DataSource]: DataSourceNode,

  // Storage nodes
  [PIPELINE_NODE_TYPES.Frost]: FrostNode,
  [PIPELINE_NODE_TYPES.GeoPersistence]: GeoPersistenceNode,

  // Transform nodes
  [PIPELINE_NODE_TYPES.Mapping]: MappingNode,
}

// ============================================================================
// Re-exports for convenience
// ============================================================================

export { BasePipelineNode } from './base/BasePipelineNode'
export { EndNode } from './control/EndNode'
export { StartNode } from './control/StartNode'
export { DataSourceNode } from './source/DataSourceNode'
export { FrostNode } from './storage/FrostNode'
export { GeoPersistenceNode } from './storage/GeoPersistenceNode'
export { MappingNode } from './transform/MappingNode'
export { CronNode } from './trigger/CronNode'
