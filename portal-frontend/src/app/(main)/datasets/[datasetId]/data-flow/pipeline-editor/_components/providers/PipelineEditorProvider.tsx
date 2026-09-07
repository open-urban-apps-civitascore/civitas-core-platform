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

import { useGetDataset } from '@/app/services/api/datasets/clientRequests'
import {
  useCreateDataSink,
  useDeleteDataSink,
  useGetDataSinks,
  useUpdateDataSink,
} from '@/app/services/api/datasets/datasinks/clientRequests'
import { useCreateMapping, useUpdateMapping } from '@/app/services/api/mappings/clientRequests'
import {
  useCreatePipeline,
  useDeletePipeline,
  useGetPipelines,
  useUpdatePipeline,
} from '@/app/services/api/pipelines/clientRequests'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { useRegisterUnsavedChanges } from '@/hooks/use-register-unsaved-changes'
import { isDatapoolScopeViolationError, isTableNameConflictError } from '@/utils/errors'

import { getNodeDef } from '../../_config/nodeRegistry'
import { ActivePipelineProvider } from '../../_hooks/use-active-pipeline'
import { tableNameOwnerOutsideNode, tableNameOwnersOutsideSession } from '../../_services/dataSinkNameService'
import {
  buildDataSinkPayloads,
  buildMappingArtifacts,
  buildPipelinePayload,
  createDataSinkSnapshot,
  type DataSinkSnapshot,
  getRemovedDataSinkIds,
  hasDataSinkChanged,
  isDestructiveDataSinkChange,
  updateNodeData,
  updateNodeEntityId,
} from '../../_services/payloadBuilderService'
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
  type PipelineValidationContext,
  validatePipelineWithNodeStatus,
  type ValidationResultWithNodeStatus,
} from '../../_services/validationService'
import type { ActivePipelineContextValue, PipelineStats } from '../../_types/context'
import type { PipelineNodeData } from '../../_types/nodes'
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
  // The dataset's sinks feed the CORE-model hydration: a styles-less pipeline (bundle import)
  // resolves each sink node's real type (frost vs geoPersistence) and entity through them.
  const dataSinksQuery = useGetDataSinks(datasetId)
  const datasetQuery = useGetDataset({ id: datasetId })
  const createPipelineMutation = useCreatePipeline(datasetId)
  const updatePipelineMutation = useUpdatePipeline(datasetId)
  const deletePipelineMutation = useDeletePipeline(datasetId)
  const createDataSinkMutation = useCreateDataSink()
  const deleteDataSinkMutation = useDeleteDataSink()
  const updateDataSinkMutation = useUpdateDataSink()
  const createMappingMutation = useCreateMapping()
  const updateMappingMutation = useUpdateMapping()

  // ===== Data sink snapshot for change detection =====
  const dataSinkSnapshotsRef = useRef<Record<string, DataSinkSnapshot>>({})

  // ===== Data-loss confirmation dialog =====
  // A destructive sink change (tableName / referenced element) on an already-provisioned
  // dataset discards its stored data (the sink's storage is rebuilt on the next release).
  // Save-All pauses on such a change and awaits an explicit confirmation via this promise before
  // sending confirmDataLoss to the backend.
  const [isDataLossDialogOpen, setIsDataLossDialogOpen] = useState(false)
  const dataLossResolveRef = useRef<((confirmed: boolean) => void) | null>(null)

  const resolveDataLossDialog = useCallback((isConfirmed: boolean) => {
    setIsDataLossDialogOpen(false)
    dataLossResolveRef.current?.(isConfirmed)
    dataLossResolveRef.current = null
  }, [])

  const confirmDataLoss = useCallback((): Promise<boolean> => {
    setIsDataLossDialogOpen(true)
    return new Promise<boolean>(resolve => {
      dataLossResolveRef.current = resolve
    })
  }, [])

  // ===== Load pipelines from backend on mount =====
  useEffect(() => {
    if (hasLoadedRef.current) return
    if (!pipelinesQuery.data?.data) return
    // Wait for the sinks while they load so the hydration can type its sink nodes; a failed
    // sinks query (isPending false, no data) degrades to hydration without sink details.
    if (dataSinksQuery.isPending) return

    hasLoadedRef.current = true
    const pipelineDTOs = pipelinesQuery.data.data

    if (pipelineDTOs.length === 0) {
      // No pipelines in backend — keep the default empty session
      return
    }

    // Convert backend DTOs to sessions
    const dataSinks = dataSinksQuery.data?.data ?? []
    const sessions = pipelineDTOs.map(dto => createSessionFromBackendDTO(dto, dataSinks))
    // Preselect the session whose backend pipeline id matches the ?pipeline= search param,
    // so deep-links from the dataset overview open the right tab.
    const matchedSession = requestedPipelineId ? sessions.find(s => s.pipeline.id === requestedPipelineId) : undefined
    const activeSessionId = matchedSession?.id ?? sessions[0]?.id ?? null

    // Load all sessions into the session manager
    sessionManager.loadSessions(sessions, activeSessionId)

    // Create initial data sink snapshots for change detection
    for (const session of sessions) {
      dataSinkSnapshotsRef.current[session.id] = createDataSinkSnapshot(session.pipeline)
    }
  }, [pipelinesQuery.data, dataSinksQuery.isPending, dataSinksQuery.data, sessionManager, requestedPipelineId])

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
    const hasErrors = (validationResult?.errors?.length ?? 0) > 0
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

      // Apply the reducer against the latest pipeline via an updater rather than the
      // closure-captured value. This lets multiple synchronous dispatches compose — e.g.
      // deleting a connected node makes React Flow emit an edge-removal change followed by a
      // node-removal change, and both must build on each other instead of overwriting.
      sessionManager.updateSessionPipeline(activeSession.id, previous => pipelineReducerWithReactFlow(previous, action))

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
      const def = getNodeDef(context.nodeType)
      if (!def) return
      const nodeData = def.createDefaultData()
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

  // ===== Geo persistence table names =====
  // The dataset's POSTGIS sinks share one schema, so a table name may be used only once per dataset.
  const validationContextFor = useCallback(
    (sessionId: string): PipelineValidationContext => ({
      tableNameOwners: tableNameOwnersOutsideSession(sessionManager.sessions, sessionId),
    }),
    [sessionManager.sessions],
  )

  const pipelineUsingTableName = useCallback(
    (nodeId: string, tableName: string) => tableNameOwnerOutsideNode(sessionManager.sessions, nodeId, tableName),
    [sessionManager.sessions],
  )

  // ===== Validation Operations =====
  const runValidation = useCallback(() => {
    const result = validatePipelineWithNodeStatus(pipeline, activeSession ? validationContextFor(activeSession.id) : {})
    setValidationResult(result)
    // Mark validation as no longer required (it was just run)
    setIsValidationRequired(false)
    // Show validation panel in inspector
    setShouldShowValidationPanel(true)
    return result
  }, [pipeline, activeSession, validationContextFor])

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
      const result = validatePipelineWithNodeStatus(session.pipeline, validationContextFor(session.id))
      if (!result.isValid) {
        failedNames.push(session.name)
      }
    }

    if (failedNames.length > 0) {
      toast.error(t('header.validationFailed', { names: failedNames.join(', ') }))
      return false
    }

    // A destructive sink change only risks data once the dataset's table physically exists.
    const isProvisioned = datasetQuery.data?.data?.provisioned ?? false
    const hasDestructiveChange =
      isProvisioned &&
      dirtySessions.some(session => {
        const snapshot = dataSinkSnapshotsRef.current[session.id] ?? {}
        return buildDataSinkPayloads(session.pipeline).some(({ nodeId, payload }) =>
          isDestructiveDataSinkChange(nodeId, payload, snapshot),
        )
      })

    // Claim the guard before awaiting the dialog: the confirmation is async, so without this a
    // second trigger (double-click, shortcut) would slip past the isSavingAll check above, reopen
    // the dialog and overwrite the pending resolve — stranding the first save's promise forever.
    setIsSavingAll(true)
    const saveFailedNames: string[] = []
    const scopeViolationNames: string[] = []
    const tableNameConflictNames: string[] = []
    try {
      if (hasDestructiveChange) {
        const isConfirmed = await confirmDataLoss()
        if (!isConfirmed) return false
      }

      // Serialize saves to avoid concurrent mutation state issues
      for (const session of dirtySessions) {
        try {
          let currentPipeline = session.pipeline
          const snapshot = dataSinkSnapshotsRef.current[session.id] ?? {}

          // Step 1: Delete data sinks for removed persistence nodes
          const removedDataSinkIds = getRemovedDataSinkIds(currentPipeline, snapshot)
          for (const dataSinkId of removedDataSinkIds) {
            await deleteDataSinkMutation.mutateAsync({ datasetId, dataSinkId: dataSinkId })
          }

          // Step 2: Save data sinks (create new / update changed). Stash the sink's CORE
          // configurationUrn back onto the node so it is emitted as the CORE model's `sinkRef`.
          const dataSinkPayloads = buildDataSinkPayloads(currentPipeline)
          for (const { nodeId, entityId, payload } of dataSinkPayloads) {
            if (!entityId) {
              const response = await createDataSinkMutation.mutateAsync({ datasetId, data: payload })
              currentPipeline = updateNodeEntityId(currentPipeline, nodeId, response.data.id)
              currentPipeline = updateNodeData(currentPipeline, nodeId, {
                configurationUrn: response.data?.configurationUrn,
              })
            } else if (hasDataSinkChanged(nodeId, payload, snapshot)) {
              // The destructive-change confirmation was obtained up front; flag the payload so the
              // backend permits the table rebuild. Only the sinks that are actually destructive
              // carry the flag — a harmless change in the same save batch does not.
              const data =
                hasDestructiveChange && isDestructiveDataSinkChange(nodeId, payload, snapshot)
                  ? { ...payload, confirmDataLoss: true }
                  : payload
              const response = await updateDataSinkMutation.mutateAsync({
                datasetId,
                dataSinkId: entityId,
                data,
              })
              currentPipeline = updateNodeData(currentPipeline, nodeId, {
                configurationUrn: response?.data?.configurationUrn,
              })
            }
          }

          // Step 2.5: Create/version mapping artifacts for configured mapping nodes (POST/PUT
          // /v1/mappings). Stash the returned versioned URN (→ CORE model `mappingRef`) and logical
          // URN (→ future PUT-versioning) back onto the node. Validation happens inside
          // buildMappingArtifacts, which throws on a schema-invalid mapping document.
          const mappingArtifacts = buildMappingArtifacts(currentPipeline)
          for (const { nodeId, logicalUrn, body } of mappingArtifacts) {
            const response = logicalUrn
              ? await updateMappingMutation.mutateAsync({ logicalUrn, ...body })
              : await createMappingMutation.mutateAsync(body)
            currentPipeline = updateNodeData(currentPipeline, nodeId, {
              mappingRef: response.data.versionedUrn,
              mappingLogicalUrn: response.data.logicalUrn,
            })
          }

          // Step 3: Save pipeline (with updated entityIds/URNs from the steps above). The CORE
          // `model` is validated against the generated schema inside buildPipelinePayload.
          const pipelinePayload = buildPipelinePayload(currentPipeline)
          const pipelineId = currentPipeline.id

          if (pipelineId) {
            await updatePipelineMutation.mutateAsync({ pipelineId, data: pipelinePayload })
          } else {
            const response = await createPipelineMutation.mutateAsync(pipelinePayload)
            currentPipeline = { ...currentPipeline, id: response.data.id }
          }

          // Step 4: Update session state and snapshot
          sessionManager.updateSessionPipeline(session.id, { ...currentPipeline, isDirty: false })
          sessionManager.markSessionClean(session.id)
          dataSinkSnapshotsRef.current[session.id] = createDataSinkSnapshot(currentPipeline)
          toast.success(t('header.saveSuccess', { name: currentPipeline.name }))
        } catch (error) {
          if (isDatapoolScopeViolationError(error)) {
            scopeViolationNames.push(session.name)
          } else if (isTableNameConflictError(error)) {
            tableNameConflictNames.push(session.name)
          } else {
            saveFailedNames.push(session.name)
          }
        }
      }

      if (scopeViolationNames.length > 0) {
        toast.error(t('header.datasourceScopeViolation', { name: scopeViolationNames.join(', ') }))
      }
      if (tableNameConflictNames.length > 0) {
        toast.error(t('header.tableNameConflict', { names: tableNameConflictNames.join(', ') }))
      }
      if (saveFailedNames.length > 0) {
        toast.error(t('header.saveFailed', { names: saveFailedNames.join(', ') }))
      }
      if (scopeViolationNames.length > 0 || tableNameConflictNames.length > 0 || saveFailedNames.length > 0) {
        return false
      }

      return true
    } finally {
      setIsSavingAll(false)
    }
  }, [
    isSavingAll,
    sessionManager,
    createPipelineMutation,
    updatePipelineMutation,
    createDataSinkMutation,
    deleteDataSinkMutation,
    updateDataSinkMutation,
    createMappingMutation,
    updateMappingMutation,
    datasetId,
    datasetQuery.data,
    confirmDataLoss,
    validationContextFor,
    t,
  ])

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
      pipelineUsingTableName,
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
      pipelineUsingTableName,
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

  return (
    <ActivePipelineProvider value={contextValue}>
      {children}
      <WarningModal
        open={isDataLossDialogOpen}
        onOpenChange={open => {
          if (!open) resolveDataLossDialog(false)
        }}
        title={t('dataLoss.title')}
        description={t('dataLoss.description')}
        confirmButtonTitle={t('dataLoss.confirm')}
        onConfirm={() => resolveDataLossDialog(true)}
        onDiscard={() => resolveDataLossDialog(false)}
      />
    </ActivePipelineProvider>
  )
}
