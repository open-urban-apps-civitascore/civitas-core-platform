/**
 * Edge Types Registry
 *
 * Registers custom edge types for React Flow.
 * Maps edge type strings to React components.
 *
 */

import type { EdgeTypes } from '@xyflow/react'

import { FlowEdge } from './FlowEdge'

// ============================================================================
// Edge Types Registry
// ============================================================================

/**
 * Pipeline edge types for React Flow.
 * Use this registry in PipelineCanvas.
 *
 */
export const pipelineEdgeTypes: EdgeTypes = {
  default: FlowEdge,
  smoothstep: FlowEdge,
}
