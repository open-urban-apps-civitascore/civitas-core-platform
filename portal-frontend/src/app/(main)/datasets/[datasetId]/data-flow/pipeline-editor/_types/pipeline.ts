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

/**
 * Backend API payload structure for saving pipelines.
 * This is the format expected by `POST /backend/pipeline`.
 *
 */
export interface PipelinePayload {
  name: string
  description: string
  /** JSON-stringified PipelineStylesPayload — backend stores as opaque string */
  styles: PipelineStylesPayload
  dataSourceIds: string[] // IDs extracted from DataSource nodes
  dataSinkIds: string[] // IDs of datasinks
  model: object // Pipeline graph serialized in RedPandaConnect syntax
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
