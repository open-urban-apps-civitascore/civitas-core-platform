/**
 * Context Types for Pipeline Editor
 *
 * Defines the context value type for the active pipeline provider.
 *
 */

import type { Connection } from '@xyflow/react'

import type { PipelineReducerAction } from '../_services/pipelineService'
import type { ValidationResultWithNodeStatus } from '../_services/validationService'
import type { PipelineNodeData } from './nodes'
import type { NodeCreationContext, Pipeline, PipelineEdge, PipelineNode } from './pipeline'

// ============================================================================
// Active Pipeline Context
// ============================================================================

/**
 * Pipeline statistics.
 *
 */
export interface PipelineStats {
  nodeCount: number
  edgeCount: number
  hasStartNode: boolean
  hasEndNode: boolean
}

/**
 * Context value type for useActivePipeline.
 * Provides access to the active pipeline state and operations.
 *
 */
export interface ActivePipelineContextValue {
  // ===== State =====
  /** The current pipeline being edited */
  pipeline: Pipeline | null
  /** Pipeline statistics */
  stats: PipelineStats
  /** Whether the pipeline has unsaved changes */
  isDirty: boolean

  // ===== Selected Elements =====
  /** Currently selected node (first selected if multiple) */
  selectedNode: PipelineNode | null
  /** Currently selected edge (first selected if multiple) */
  selectedEdge: PipelineEdge | null

  // ===== Dispatch =====
  /** Dispatch function for pipeline actions */
  dispatch: (action: PipelineReducerAction) => void

  // ===== Node Operations =====
  /** Add a new node to the pipeline */
  addNode: (context: NodeCreationContext) => void
  /** Update a node's data */
  updateNode: (nodeId: string, updates: Partial<PipelineNodeData>) => void
  /** Delete multiple nodes by ID */
  deleteNodes: (nodeIds: string[]) => void
  /** Select a node (optionally multi-select) */
  selectNode: (nodeId: string, isMultiSelect?: boolean) => void

  // ===== Edge Operations =====
  /** Add a new edge/connection */
  addEdge: (connection: Connection) => void
  /** Delete multiple edges by ID */
  deleteEdges: (edgeIds: string[]) => void
  /** Select an edge (optionally multi-select) */
  selectEdge: (edgeId: string, isMultiSelect?: boolean) => void

  // ===== Selection Operations =====
  /** Clear all selections */
  clearSelection: () => void
  /** Get all selected nodes */
  getSelectedNodes: () => PipelineNode[]
  /** Get all selected edges */
  getSelectedEdges: () => PipelineEdge[]
  /** Delete all selected items */
  deleteSelected: () => void

  // ===== Validation =====
  /** Validate if a connection is allowed */
  validateConnection: (connection: Connection) => boolean
  /** Current validation result (null if not validated) */
  validationResult: ValidationResultWithNodeStatus | null
  /** Run pipeline validation and update state */
  runValidation: () => ValidationResultWithNodeStatus
  /** Clear validation state */
  clearValidation: () => void
  /** Get validation severity for a specific node */
  getNodeValidationSeverity: (nodeId: string) => 'error' | 'warning' | 'none'
  /** Whether validation is required before save (true when pipeline changed since last validation) */
  isValidationRequired: boolean
  /** Whether the pipeline can be saved (isDirty && !hasErrors && !isValidationRequired) */
  canSave: boolean
  /** Whether to show the validation panel in inspector (true after clicking validate) */
  shouldShowValidationPanel: boolean
  /** Hide the validation panel (called when user selects a node/edge) */
  hideValidationPanel: () => void

  // ===== Pipeline Operations =====
  /** Save pipeline to backend API */
  savePipeline: () => Promise<boolean>
  /** Whether a save operation is currently in progress */
  isSaving: boolean
  /** Delete the active pipeline from backend and remove its tab */
  deletePipeline: () => void
  /** Whether a delete operation is currently in progress */
  isDeleting: boolean
  /** Whether pipelines are being loaded from the backend */
  isLoadingPipelines: boolean

  // ===== Session Info =====
  /** ID of the active session */
  activeSessionId: string | null
}

// ============================================================================
// Provider Props
// ============================================================================

/**
 * Props for PipelineEditorProvider component.
 *
 */
export interface PipelineEditorProviderProps {
  children: React.ReactNode
}
