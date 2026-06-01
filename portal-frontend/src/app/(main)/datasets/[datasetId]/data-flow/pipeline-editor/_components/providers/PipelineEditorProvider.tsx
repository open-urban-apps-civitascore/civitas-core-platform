'use client'

/**
 * PipelineEditorProvider Component
 *
 * Main context provider for the pipeline editor.
 * Manages session state and provides pipeline operations to child components.
 * Integrates with the backend API for CRUD operations on pipelines.
 *
 */

import type { Connection } from '@xyflow/react'
import { useParams, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { toast } from 'sonner'

import {
  useCreatePipeline,
  useDeletePipeline,
  useGetPipelines,
  useUpdatePipeline,
} from '@/app/services/api/pipelines/clientRequests'
import { useRegisterUnsavedChanges } from '@/hooks/use-register-unsaved-changes'

import { ActivePipelineProvider } from '../../_hooks/use-active-pipeline'
import { buildPipelinePayload } from '../../_services/payloadBuilderService'
import {
  createEmptyPipeline,
  getPipelineStats,
  getSelectedEdges,
  getSelectedNodes,
  type PipelineReducerAction,
  pipelineReducerWithReactFlow,
  validateConnection,
} from '../../_services/pipelineService'
import { createSessionFromBackendDTO } from '../../_services/sessionService'
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
 * Fetches pipelines from backend on mount and provides CRUD operations.
 *
 */
export const PipelineEditorProviderComponent: React.FC<PipelineEditorProviderComponentProps> = ({
  children,
  sessionManager,
}) => {
  const params = useParams<{ datasetId: string }>()
  const searchParams = useSearchParams()
  const requestedPipelineId = searchParams.get('pipeline')
  const t = useTranslations('pipelineEditor')
  const datasetId = params.datasetId
  const activeSession = sessionManager.getActiveSession()
  const pipeline = activeSession?.pipeline || createEmptyPipeline()

  // ===== Track whether initial load has been done =====
  const hasLoadedRef = useRef(false)

  // ===== Backend API Hooks =====
  const pipelinesQuery = useGetPipelines(datasetId)
  const createPipelineMutation = useCreatePipeline(datasetId)
  const updatePipelineMutation = useUpdatePipeline(datasetId)
  const deletePipelineMutation = useDeletePipeline(datasetId)

  // ===== Load pipelines from backend on mount =====
  useEffect(() => {
    if (hasLoadedRef.current) return
    if (!pipelinesQuery.data?.data) return

    hasLoadedRef.current = true
    const pipelineDTOs = pipelinesQuery.data.data

    if (pipelineDTOs.length === 0) {
      // No pipelines in backend — keep the default empty session
      return
    }

    // Convert backend DTOs to sessions
    const sessions = pipelineDTOs.map(dto => createSessionFromBackendDTO(dto))
    // Preselect the session whose backend pipeline id matches the ?pipeline= search param,
    // so deep-links from the dataset overview open the right tab.
    const matchedSession = requestedPipelineId ? sessions.find(s => s.pipeline.id === requestedPipelineId) : undefined
    const activeSessionId = matchedSession?.id ?? sessions[0]?.id ?? null

    // Load all sessions into the session manager
    sessionManager.loadSessions(sessions, activeSessionId)
  }, [pipelinesQuery.data, sessionManager, requestedPipelineId])

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

  // ===== Pipeline Operations: Delete =====
  const deletePipeline = useCallback(() => {
    if (!activeSession) return

    const pipelineId = activeSession.pipeline.id

    const removeSession = () => {
      sessionManager.closeSession(activeSession.id)
    }

    if (pipelineId) {
      // Existing pipeline → DELETE from backend, then remove session
      deletePipelineMutation.mutate(pipelineId, {
        onSuccess: () => {
          removeSession()
          console.log('Pipeline deleted successfully')
        },
        onError: error => {
          console.error('Failed to delete pipeline:', error)
        },
      })
    } else {
      // Never-saved pipeline → just remove the session
      removeSession()
    }
  }, [activeSession, sessionManager, deletePipelineMutation])

  const isDeleting = deletePipelineMutation.isPending

  // ===== Loading State =====
  const isLoadingPipelines = pipelinesQuery.isLoading

  // ===== Cross-session state =====
  const hasAnyDirtySession = useMemo(() => sessionManager.sessions.some(s => s.isDirty), [sessionManager.sessions])

  const [isSavingAll, setIsSavingAll] = useState(false)

  const saveAllPipelines = useCallback(async (): Promise<boolean> => {
    if (isSavingAll) return false

    const dirtySessions = sessionManager.sessions.filter(s => s.isDirty)
    if (dirtySessions.length === 0) return true

    // Check for duplicate pipeline names across ALL sessions (not just dirty —
    // a clean session could share a name with a new dirty one)
    const allNames = sessionManager.sessions.map(s => s.pipeline.name.trim().toLowerCase())
    const duplicateNames = new Set<string>()
    const seen = new Set<string>()
    for (const name of allNames) {
      if (seen.has(name)) duplicateNames.add(name)
      seen.add(name)
    }

    if (duplicateNames.size > 0) {
      const displayNames = sessionManager.sessions
        .filter(s => duplicateNames.has(s.pipeline.name.trim().toLowerCase()))
        .map(s => s.pipeline.name)
      toast.error(t('header.duplicateNames', { names: [...new Set(displayNames)].join(', ') }))
      return false
    }

    // Validate all dirty pipelines before saving
    const failedNames: string[] = []
    for (const session of dirtySessions) {
      const result = validatePipelineWithNodeStatus(session.pipeline)
      if (!result.isValid) {
        failedNames.push(session.name)
      }
    }

    if (failedNames.length > 0) {
      toast.error(t('header.validationFailed', { names: failedNames.join(', ') }))
      return false
    }

    setIsSavingAll(true)
    const saveFailedNames: string[] = []
    try {
      // Serialize saves to avoid concurrent mutation state issues
      for (const session of dirtySessions) {
        try {
          const payload = buildPipelinePayload(session.pipeline)
          const pipelineId = session.pipeline.id

          if (pipelineId) {
            await updatePipelineMutation.mutateAsync({ pipelineId, data: payload })
            sessionManager.markSessionClean(session.id)
          } else {
            const response = await createPipelineMutation.mutateAsync(payload)
            const updatedPipeline: Pipeline = {
              ...session.pipeline,
              id: response.data.id,
              isDirty: false,
            }
            sessionManager.updateSessionPipeline(session.id, updatedPipeline)
            sessionManager.markSessionClean(session.id)
          }
          toast.success(t('header.saveSucces'))
        } catch {
          saveFailedNames.push(session.name)
        }
      }

      if (saveFailedNames.length > 0) {
        toast.error(t('header.saveFailed', { names: saveFailedNames.join(', ') }))
        return false
      }

      return true
    } finally {
      setIsSavingAll(false)
    }
  }, [isSavingAll, sessionManager, createPipelineMutation, updatePipelineMutation, t])

  useRegisterUnsavedChanges(hasAnyDirtySession, saveAllPipelines)

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
      deletePipeline,
      isDeleting,
      isLoadingPipelines,

      // Cross-session operations
      saveAllPipelines,
      isSavingAll,
      hasAnyDirtySession,

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
      deletePipeline,
      isDeleting,
      isLoadingPipelines,
      saveAllPipelines,
      isSavingAll,
      hasAnyDirtySession,
    ],
  )

  return <ActivePipelineProvider value={contextValue}>{children}</ActivePipelineProvider>
}
