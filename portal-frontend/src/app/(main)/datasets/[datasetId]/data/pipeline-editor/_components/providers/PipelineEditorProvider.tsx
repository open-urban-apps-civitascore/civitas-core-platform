'use client'

/**
 * PipelineEditorProvider Component
 *
 * Main context provider for the pipeline editor.
 * Manages session state and provides pipeline operations to child components.
 * Adapted from UML modeler's ActiveDiagramProvider.
 *
 */

import type { Connection } from '@xyflow/react'
import { type ReactNode, useCallback, useEffect, useMemo, useState } from 'react'

import { ActivePipelineProvider } from '../../_hooks/use-active-pipeline'
import {
  createEmptyPipeline,
  getPipelineStats,
  getSelectedEdges,
  getSelectedNodes,
  type PipelineReducerAction,
  pipelineReducerWithReactFlow,
  validateConnection,
} from '../../_services/pipelineService'
import {
  getNodeValidationSeverity as getNodeValidationSeverityFn,
  validatePipelineWithNodeStatus,
  type ValidationResultWithNodeStatus,
} from '../../_services/validationService'
import type { ActivePipelineContextValue, PipelineStats } from '../../_types/context'
import { createDefaultNodeData, type PipelineNodeData } from '../../_types/nodes'
import type { NodeCreationContext, Pipeline, PipelineEdge, PipelineNode, PipelineNodeType } from '../../_types/pipeline'
import type { UsePipelineSessionReturn } from '../../_types/session'

// ============================================================================
// Props
// ============================================================================

interface PipelineEditorProviderComponentProps {
  children: ReactNode
  /** Session manager passed from parent - ensures single source of truth */
  sessionManager: UsePipelineSessionReturn
}

// ============================================================================
// Component
// ============================================================================

/**
 * Provider component that wraps the pipeline editor.
 * Combines session management with pipeline operations.
 *
 * IMPORTANT: sessionManager is passed as prop (not called as hook)
 * to ensure single source of truth for session state.
 * This follows the same pattern as UML modeler's ActiveDiagramProvider.
 *
 */
export const PipelineEditorProviderComponent: React.FC<PipelineEditorProviderComponentProps> = ({
  children,
  sessionManager,
}) => {
  const activeSession = sessionManager.getActiveSession()
  const pipeline = activeSession?.pipeline || createEmptyPipeline()

  // ===== Validation State =====
  const [validationResult, setValidationResult] = useState<ValidationResultWithNodeStatus | null>(null)
  /** True when pipeline changed since last validation - requires validation before save */
  const [isValidationRequired, setIsValidationRequired] = useState(true)
  /** True when validation panel should be shown in inspector */
  const [shouldShowValidationPanel, setShouldShowValidationPanel] = useState(false)

  // ===== Computed: Can Save =====
  // Pipeline can be saved when: isDirty && validation passed (no errors) && validation has been run
  const canSave = useMemo(() => {
    const isDirty = activeSession?.isDirty || false
    const hasErrors = validationResult?.errors?.length ?? 0 > 0
    return isDirty && !isValidationRequired && !hasErrors
  }, [activeSession?.isDirty, validationResult?.errors?.length, isValidationRequired])

  // ===== Track pipeline changes to invalidate validation =====
  useEffect(() => {
    // When pipeline changes (and is dirty), mark validation as required
    if (activeSession?.isDirty) {
      setIsValidationRequired(true)
      // Also clear validation result when pipeline changes
      setValidationResult(null)
      setShouldShowValidationPanel(false)
    }
  }, [activeSession?.isDirty, pipeline.nodes.length, pipeline.edges.length])

  // ===== Memoized Values =====
  const selectedNode = useMemo(() => pipeline.nodes.find(n => n.selected) || null, [pipeline.nodes])

  const selectedEdge = useMemo(() => pipeline.edges.find(e => e.selected) || null, [pipeline.edges])

  const stats: PipelineStats = useMemo(() => getPipelineStats(pipeline), [pipeline])

  // ===== Helper to update pipeline in session =====
  const updatePipeline = useCallback(
    (updater: (pipeline: Pipeline) => Pipeline) => {
      if (!activeSession) return
      const updatedPipeline = updater(activeSession.pipeline)
      sessionManager.updateSessionPipeline(activeSession.id, updatedPipeline)
    },
    [activeSession, sessionManager],
  )

  // ===== Dispatch function =====
  const dispatch = useCallback(
    (action: PipelineReducerAction) => {
      if (!activeSession) return

      const updatedPipeline = pipelineReducerWithReactFlow(activeSession.pipeline, action)
      sessionManager.updateSessionPipeline(activeSession.id, updatedPipeline)

      // Mark as dirty for most actions
      if (action.type !== 'MARK_CLEAN') {
        sessionManager.markSessionDirty(activeSession.id)
      } else {
        sessionManager.markSessionClean(activeSession.id)
      }
    },
    [activeSession, sessionManager],
  )

  // ===== Node Operations =====
  const addNode = useCallback(
    (context: NodeCreationContext) => {
      const nodeData = createDefaultNodeData(context.nodeType)
      const newNode: PipelineNode = {
        id: crypto.randomUUID(),
        type: context.nodeType as PipelineNodeType,
        position: context.position,
        data: nodeData,
      }
      dispatch({ type: 'ADD_NODE', payload: newNode })
    },
    [dispatch],
  )

  const updateNode = useCallback(
    (nodeId: string, updates: Partial<PipelineNodeData>) => {
      dispatch({ type: 'UPDATE_NODE', payload: { id: nodeId, updates } })
    },
    [dispatch],
  )

  const deleteNodes = useCallback(
    (nodeIds: string[]) => {
      dispatch({ type: 'DELETE_NODES', payload: nodeIds })
    },
    [dispatch],
  )

  const selectNode = useCallback(
    (nodeId: string, isMultiSelect = false) => {
      // Reset validation panel when selecting a node (latest action wins)
      setShouldShowValidationPanel(false)

      updatePipeline(p => ({
        ...p,
        nodes: p.nodes.map(node => ({
          ...node,
          selected: isMultiSelect ? (node.id === nodeId ? !node.selected : node.selected) : node.id === nodeId,
        })),
        edges: isMultiSelect ? p.edges : p.edges.map(edge => ({ ...edge, selected: false })),
      }))
    },
    [updatePipeline],
  )

  // ===== Edge Operations =====
  const addEdge = useCallback(
    (connection: Connection) => {
      if (!validateConnection(pipeline, connection)) return

      const newEdge: PipelineEdge = {
        id: crypto.randomUUID(),
        source: connection.source!,
        target: connection.target!,
        type: 'smoothstep',
        data: {
          label: '',
        },
      }

      dispatch({ type: 'ADD_EDGE', payload: newEdge })
    },
    [pipeline, dispatch],
  )

  const deleteEdges = useCallback(
    (edgeIds: string[]) => {
      dispatch({ type: 'DELETE_EDGES', payload: edgeIds })
    },
    [dispatch],
  )

  const selectEdge = useCallback(
    (edgeId: string, isMultiSelect = false) => {
      // Reset validation panel when selecting an edge (latest action wins)
      setShouldShowValidationPanel(false)

      updatePipeline(p => ({
        ...p,
        edges: p.edges.map(edge => ({
          ...edge,
          selected: isMultiSelect ? (edge.id === edgeId ? !edge.selected : edge.selected) : edge.id === edgeId,
        })),
        nodes: isMultiSelect ? p.nodes : p.nodes.map(node => ({ ...node, selected: false })),
      }))
    },
    [updatePipeline],
  )

  // ===== Selection Operations =====
  const clearSelection = useCallback(() => {
    updatePipeline(p => ({
      ...p,
      nodes: p.nodes.map(node => ({ ...node, selected: false })),
      edges: p.edges.map(edge => ({ ...edge, selected: false })),
    }))
  }, [updatePipeline])

  const getSelectedNodesCallback = useCallback(() => getSelectedNodes(pipeline), [pipeline])

  const getSelectedEdgesCallback = useCallback(() => getSelectedEdges(pipeline), [pipeline])

  const deleteSelected = useCallback(() => {
    const selectedNodeIds = getSelectedNodes(pipeline).map(node => node.id)
    const selectedEdgeIds = getSelectedEdges(pipeline).map(edge => edge.id)

    if (selectedNodeIds.length > 0) {
      deleteNodes(selectedNodeIds)
    }
    if (selectedEdgeIds.length > 0) {
      deleteEdges(selectedEdgeIds)
    }
  }, [pipeline, deleteNodes, deleteEdges])

  const validateConnectionCallback = useCallback(
    (connection: Connection) => validateConnection(pipeline, connection),
    [pipeline],
  )

  // ===== Validation Operations =====
  const runValidation = useCallback(() => {
    const result = validatePipelineWithNodeStatus(pipeline)
    setValidationResult(result)
    // Mark validation as no longer required (it was just run)
    setIsValidationRequired(false)
    // Show validation panel in inspector
    setShouldShowValidationPanel(true)
    return result
  }, [pipeline])

  const clearValidation = useCallback(() => {
    setValidationResult(null)
  }, [])

  const getNodeValidationSeverity = useCallback(
    (nodeId: string): 'error' | 'warning' | 'none' => {
      return getNodeValidationSeverityFn(nodeId, validationResult)
    },
    [validationResult],
  )

  const hideValidationPanel = useCallback(() => {
    setShouldShowValidationPanel(false)
  }, [])

  // ===== Pipeline Operations =====
  const savePipeline = useCallback(() => {
    if (!activeSession) return

    // Log the pipeline to console
    console.log('Saving pipeline:', pipeline)

    // Mark session as clean
    sessionManager.markSessionClean(activeSession.id)
  }, [activeSession, pipeline, sessionManager])

  // ===== Context Value =====
  const contextValue: ActivePipelineContextValue = useMemo(
    () => ({
      // State
      pipeline: activeSession ? pipeline : null,
      stats,
      isDirty: activeSession?.isDirty || false,

      // Selected elements
      selectedNode,
      selectedEdge,

      // Dispatch
      dispatch,

      // Node operations
      addNode,
      updateNode,
      deleteNodes,
      selectNode,

      // Edge operations
      addEdge,
      deleteEdges,
      selectEdge,

      // Selection operations
      clearSelection,
      getSelectedNodes: getSelectedNodesCallback,
      getSelectedEdges: getSelectedEdgesCallback,
      deleteSelected,

      // Validation
      validateConnection: validateConnectionCallback,
      validationResult,
      runValidation,
      clearValidation,
      getNodeValidationSeverity,
      isValidationRequired,
      canSave,
      shouldShowValidationPanel,
      hideValidationPanel,

      // Pipeline operations
      savePipeline,

      // Session info
      activeSessionId: activeSession?.id || null,
    }),
    [
      activeSession,
      pipeline,
      stats,
      selectedNode,
      selectedEdge,
      dispatch,
      addNode,
      updateNode,
      deleteNodes,
      selectNode,
      addEdge,
      deleteEdges,
      selectEdge,
      clearSelection,
      getSelectedNodesCallback,
      getSelectedEdgesCallback,
      deleteSelected,
      validateConnectionCallback,
      validationResult,
      runValidation,
      clearValidation,
      getNodeValidationSeverity,
      isValidationRequired,
      canSave,
      shouldShowValidationPanel,
      hideValidationPanel,
      savePipeline,
    ],
  )

  return <ActivePipelineProvider value={contextValue}>{children}</ActivePipelineProvider>
}
