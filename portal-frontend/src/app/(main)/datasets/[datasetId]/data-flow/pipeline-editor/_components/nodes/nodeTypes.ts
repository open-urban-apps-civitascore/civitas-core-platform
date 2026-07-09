/**
 * Node Types Registry (derived)
 *
 * Maps pipeline node type identifiers to their React component. Every pipeline node
 * now renders through the single registry-driven `ActivityNode`, so this map is
 * generated from the node registry — there is no longer a file per node type.
 */

import type { NodeTypes } from '@xyflow/react'

import { PIPELINE_NODE_DEFS } from '../../_config/nodeRegistry'
import { createActivityNode } from './ActivityNode'

/**
 * Node types mapping for React Flow, derived from the node registry.
 * Keys match the PipelineNodeType values.
 */
export const pipelineNodeTypes: NodeTypes = Object.fromEntries(
  PIPELINE_NODE_DEFS.map(def => [def.type, createActivityNode(def.type)]),
)

export { BasePipelineNode } from './base/BasePipelineNode'
