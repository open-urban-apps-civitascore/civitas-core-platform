import type { Edge, Node, Viewport } from '@xyflow/react'

import { DataSinkPayload } from '@/types/datasinks'

import type { PipelineNodeData } from './nodes'

// ============================================================================
// Pipeline Node Types
// ============================================================================

/**
 * All available pipeline node types.
 * Used to identify node categories and render appropriate components.
 *
 */
export const PIPELINE_NODE_TYPES = {
  // Control nodes
  Start: 'start',
  End: 'end',
  // Trigger nodes
  Cron: 'cron',
  // Source nodes
  DataSource: 'dataSource',
  // Storage nodes
  Frost: 'frost',
  GeoPersistence: 'geoPersistence',
  // Transform nodes
  Mapping: 'mapping',
} as const

export type PipelineNodeType = (typeof PIPELINE_NODE_TYPES)[keyof typeof PIPELINE_NODE_TYPES]

// ============================================================================
// Pipeline Node (React Flow Extended)
// ============================================================================

/**
 * Extended React Flow Node with pipeline-specific data.
 * This is the primary node type used throughout the pipeline editor.
 *
 */
export interface PipelineNode extends Node<PipelineNodeData, PipelineNodeType> {
  type: PipelineNodeType
  data: PipelineNodeData
}

// ============================================================================
// Pipeline Edge
// ============================================================================

/**
 * Pipeline edge data structure.
 * Defines the data attached to each edge/connection.
 */
export interface PipelineEdgeData extends Record<string, unknown> {
  label?: string
  guard?: string // Optional guard expression for conditional transitions
  isSelected?: boolean
}

/**
 * Extended React Flow Edge for pipeline connections.
 *
 */
export interface PipelineEdge extends Edge<PipelineEdgeData> {
  data?: PipelineEdgeData
}

// ============================================================================
// Pipeline Model
// ============================================================================

/**
 * Complete pipeline model representing a single pipeline diagram.
 * Contains all nodes, edges, and metadata.
 *
 */
export interface Pipeline {
  /** Backend UUID. Undefined for new (unsaved) pipelines, set after first save. */
  id?: string
  name: string
  description: string
  nodes: PipelineNode[]
  edges: PipelineEdge[]
  viewport?: Viewport
  createdAt: Date
  updatedAt: Date
  isDirty: boolean
}

// ============================================================================
// Pipeline Backend Payload
// ============================================================================

/**
 * Styles object for backend payload.
 * Stores React Flow visual configuration.
 */
export interface PipelineStylesPayload {
  viewport?: Viewport
  nodePositions: Record<string, { x: number; y: number }>
  /** Full React Flow nodes for round-tripping (Option A) */
  nodes: PipelineNode[]
  /** Full React Flow edges for round-tripping (Option A) */
  edges: PipelineEdge[]
}

// ============================================================================
// CORE Pipeline Document (the clean, URN-native `model`)
// ============================================================================

/** The `kind` discriminator of a CORE pipeline node, derived from the React-Flow node `type`. */
export type CorePipelineNodeKind = 'source' | 'sink' | 'mapping' | 'cron' | 'start' | 'end'

/**
 * A single node of the clean CORE Pipeline document. References are versioned CORE URNs
 * (`sourceRef`/`sinkRef`/`mappingRef`) and are omitted when the node is not yet configured.
 * `x-ui-position` carries the canvas position; `id`/`kind`/`label` are the stable identity.
 */
export interface CorePipelineNode {
  id: string
  kind: CorePipelineNodeKind
  label?: string
  /** Versioned CORE URN of the referenced DataSource (source nodes). */
  sourceRef?: string
  /** Versioned CORE URN of the referenced DataSink (sink nodes). */
  sinkRef?: string
  /** Versioned CORE URN of the referenced Mapping (mapping nodes). */
  mappingRef?: string
  /** Quartz/NiFi cron expression (cron nodes). */
  cronExpression?: string
  /** Editor-only layout hint carried under the CORE `x-ui-*` extension namespace. */
  // eslint-disable-next-line @typescript-eslint/naming-convention -- CORE spec kebab-case extension key, cannot be camelCased
  'x-ui-position'?: { x: number; y: number }
}

/** A directed connection between two CORE pipeline nodes. */
export interface CorePipelineEdge {
  id: string
  source: string
  target: string
  label?: string
}

/**
 * The clean CORE Pipeline document emitted in the payload `model` field. It contains NO React-Flow
 * specifics (no `type`/`data`) — only URN-native nodes/edges. `$schema`/`id` are omitted here and
 * stamped by Model Forge on ingest. Validated against the generated `PipelineDraftSchema` before send.
 */
export interface CorePipelineModel {
  nodes: CorePipelineNode[]
  edges: CorePipelineEdge[]
}

/**
 * Backend API payload structure for saving pipelines.
 * This is the format expected by `POST /backend/pipeline`.
 *
 */
export interface PipelinePayload {
  name: string
  description: string
  /** Full React-Flow graph (viewport + nodes/edges) for editor round-tripping. */
  styles: PipelineStylesPayload
  dataSourceIds: string[] // IDs extracted from DataSource nodes
  dataSinkIds: string[] // IDs of data sinks
  /** The clean, URN-native CORE Pipeline document (schema-valid; validated on the frontend). */
  model: CorePipelineModel
}

// ============================================================================
// Pipeline Backend DTOs
// ============================================================================

/**
 * Backend response DTO for a pipeline.
 * Returned by GET/POST/PUT /datasets/{datasetId}/pipelines[/{id}]
 */
export interface PipelineOutputDTO {
  id: string
  createdAt: string
  modifiedAt: string
  name: string
  description: string
  styles: PipelineStylesPayload
  dataSources: number[]
  apis: string[]
  dataSinks: DataSinkPayload[]
  model: object
}

// ============================================================================
// Pipeline Actions (Redux-style)
// ============================================================================

/**
 * Pipeline diagram actions for state management.
 *
 */
export type PipelineAction =
  | { type: 'SET_NODES'; payload: PipelineNode[] }
  | { type: 'SET_EDGES'; payload: PipelineEdge[] }
  | { type: 'ADD_NODE'; payload: PipelineNode }
  | { type: 'ADD_EDGE'; payload: PipelineEdge }
  | { type: 'UPDATE_NODE'; payload: { id: string; updates: Partial<PipelineNodeData> } }
  | { type: 'UPDATE_EDGE'; payload: { id: string; updates: Partial<PipelineEdgeData> } }
  | { type: 'DELETE_NODES'; payload: string[] }
  | { type: 'DELETE_EDGES'; payload: string[] }
  | { type: 'SET_VIEWPORT'; payload: Viewport }
  | { type: 'MARK_CLEAN' }
  | { type: 'MARK_DIRTY' }
  | { type: 'LOAD_PIPELINE'; payload: Pipeline }
  | { type: 'RESET_PIPELINE' }

// ============================================================================
// Node Creation Context
// ============================================================================

/**
 * Context for creating new pipeline nodes.
 * Used when dragging from palette to canvas.
 *
 */
export interface NodeCreationContext {
  nodeType: PipelineNodeType
  position: { x: number; y: number }
  name?: string
}
