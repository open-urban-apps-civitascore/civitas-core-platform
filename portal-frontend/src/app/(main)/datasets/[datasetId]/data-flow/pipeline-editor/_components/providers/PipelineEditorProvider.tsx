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
import { useDatasetPermissions } from '@/hooks/use-dataset-permissions'
import { useRegisterUnsavedChanges } from '@/hooks/use-register-unsaved-changes'
import {
  isDatapoolScopeViolationError,
  isNotDraftError,
  isResourceInUseError,
  isSagaInFlightError,
  isTableNameConflictError,
  isUnconfirmedDataLossError,
} from '@/utils/errors'

import { getNodeDef } from '../../_config/nodeRegistry'
import { ActivePipelineProvider } from '../../_hooks/use-active-pipeline'
import { useDataSinkLocks } from '../../_hooks/use-datasink-locks'
import { useReadOnly } from '../../_hooks/use-pipeline-read-only'
import { tableNameOwnerOutsideNode, tableNameOwnersOutsideSession } from '../../_services/dataSinkNameService'
import {
  buildDataSinkPayloads,
  buildMappingArtifacts,
  buildPipelinePayload,
  createDataSinkSnapshot,
  createMappingSnapshot,
  type DataSinkSnapshot,
  getRemovedDataSinkIds,
  hasDataSinkChanged,
  hasMappingChanged,
  isDestructiveDataSinkChange,
  type MappingSnapshot,
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
  const datasetQuery = useGetDataset({ id: datasetId })
  const createPipelineMutation = useCreatePipeline(datasetId)
  const updatePipelineMutation = useUpdatePipeline(datasetId)
  const deletePipelineMutation = useDeletePipeline(datasetId)
  const createDataSinkMutation = useCreateDataSink()
  const deleteDataSinkMutation = useDeleteDataSink()
  const updateDataSinkMutation = useUpdateDataSink()
  const createMappingMutation = useCreateMapping(datasetId)
  const updateMappingMutation = useUpdateMapping(datasetId)

  const { isReadOnly } = useReadOnly()

  const { canDeletePipeline: canDelete } = useDatasetPermissions(datasetQuery.data?.data)

  const { getSinkLocks, getSinkLockReason, getSelectionLockReason, isLoading: isLoadingSinkLocks } = useDataSinkLocks()

  // ===== Data sink snapshot for change detection =====
  const dataSinkSnapshotsRef = useRef<Record<string, DataSinkSnapshot>>({})

  // ===== Mapping snapshot for change detection =====
  const mappingSnapshotsRef = useRef<Record<string, MappingSnapshot>>({})

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

    // Create initial data sink and mapping snapshots for change detection. A schema-invalid
    // document here (e.g. stale data predating a schema change) must not crash the whole editor on
    // load — fall back to an empty snapshot, which change detection already treats as "everything
    // is new".
    for (const session of sessions) {
      try {
        dataSinkSnapshotsRef.current[session.id] = createDataSinkSnapshot(session.pipeline)
      } catch (error) {
        console.error('Failed to build data sink snapshot for session:', session.name, error)
        dataSinkSnapshotsRef.current[session.id] = {}
      }
      try {
        mappingSnapshotsRef.current[session.id] = createMappingSnapshot(session.pipeline)
      } catch (error) {
        console.error('Failed to build mapping snapshot for session:', session.name, error)
        mappingSnapshotsRef.current[session.id] = {}
      }
    }
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

    // A never-saved pipeline exists only in the session, so discarding it needs no permission.
    if (!pipelineId) {
      removeSession()
      return
    }

    if (!canDelete) return

    deletePipelineMutation.mutate(pipelineId, {
      onSuccess: () => {
        removeSession()
      },
      onError: error => {
        if (isNotDraftError(error)) {
          toast.error(t('header.notDraftError'))
        } else if (isSagaInFlightError(error)) {
          toast.error(t('header.sagaInFlightError'))
        } else {
          console.error('Failed to delete pipeline:', error)
          toast.error(t('toolbar.deleteFailed'))
        }
      },
    })
  }, [activeSession, sessionManager, deletePipelineMutation, canDelete, t])

  const isDeleting = deletePipelineMutation.isPending

  // ===== Loading State =====
  // Without the locks every node looks deletable, so the editor waits for them too.
  const isLoadingEditor = pipelinesQuery.isLoading || isLoadingSinkLocks

  // ===== Cross-session state =====
  const hasAnyDirtySession = useMemo(() => sessionManager.sessions.some(s => s.isDirty), [sessionManager.sessions])

  const [isSavingAll, setIsSavingAll] = useState(false)

  const saveAllPipelines = useCallback(async (): Promise<boolean> => {
    if (isSavingAll || isReadOnly) return false

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
        try {
          return buildDataSinkPayloads(session.pipeline).some(({ nodeId, payload }) =>
            isDestructiveDataSinkChange(nodeId, payload, snapshot),
          )
        } catch (error) {
          // A schema-invalid sink document is reported properly by the per-session save loop below
          // (step 2), which validates again and surfaces it via the normal saveFailedNames path —
          // this pre-scan only needs a best-effort answer, not to abort the whole save here.
          console.error('Failed to evaluate destructive sink changes for session:', session.name, error)
          return false
        }
      })

    // Claim the guard before awaiting the dialog: the confirmation is async, so without this a
    // second trigger (double-click, shortcut) would slip past the isSavingAll check above, reopen
    // the dialog and overwrite the pending resolve — stranding the first save's promise forever.
    setIsSavingAll(true)
    const saveFailedNames: string[] = []
    const scopeViolationNames: string[] = []
    const tableNameConflictNames: string[] = []
    const sinkInUsePipelineNames: string[] = []
    const dataLossPipelineNames: string[] = []
    const notDraftNames: string[] = []
    const sagaInFlightNames: string[] = []
    const sinkStillInUseNames: string[] = []
    const sinkDeleteFailedNames: string[] = []
    try {
      if (hasDestructiveChange) {
        const isConfirmed = await confirmDataLoss()
        if (!isConfirmed) return false
      }

      // Serialize saves to avoid concurrent mutation state issues
      for (const session of dirtySessions) {
        // Declared outside the try below so the catch can still see whatever refs were stashed
        // onto it before the failing step.
        let currentPipeline = session.pipeline
        try {
          const snapshot = dataSinkSnapshotsRef.current[session.id] ?? {}

          // Step 1: Collect removed sinks. A saved pipeline still references them, so they get deleted in step 3.5.
          const removedDataSinkIds = getRemovedDataSinkIds(currentPipeline, snapshot)

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
          // /v1/mappings). An already-created mapping (has logicalUrn) whose body is unchanged since
          // the last save is skipped — the mapping API creates a new version per request, so sending
          // it on every save (e.g. for an unrelated node edit) would churn versions for nothing.
          // Stash the returned versioned URN (→ CORE model `mappingRef`) and logical URN (→ future
          // PUT-versioning) back onto the node. Validation happens inside buildMappingArtifacts,
          // which throws on a schema-invalid mapping document.
          const mappingArtifacts = buildMappingArtifacts(currentPipeline)
          const mappingSnapshot = mappingSnapshotsRef.current[session.id] ?? {}
          for (const { nodeId, logicalUrn, body } of mappingArtifacts) {
            if (logicalUrn && !hasMappingChanged(nodeId, body, mappingSnapshot)) continue

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

          // Step 3.5: Delete the sinks from step 1. The saved pipeline no longer references them.
          let hasSinkStillInUse = false
          let hasSinkDeleteFailed = false
          for (const dataSinkId of removedDataSinkIds) {
            try {
              await deleteDataSinkMutation.mutateAsync({ datasetId, dataSinkId })
            } catch (error) {
              console.error('Failed to delete data sink:', dataSinkId, error)
              if (isResourceInUseError(error)) hasSinkStillInUse = true
              else hasSinkDeleteFailed = true
            }
          }
          if (hasSinkStillInUse) sinkStillInUseNames.push(session.name)
          if (hasSinkDeleteFailed) sinkDeleteFailedNames.push(session.name)

          // Step 4: Update session state and snapshot
          sessionManager.updateSessionPipeline(session.id, { ...currentPipeline, isDirty: false })
          sessionManager.markSessionClean(session.id)
          dataSinkSnapshotsRef.current[session.id] = createDataSinkSnapshot(currentPipeline)
          mappingSnapshotsRef.current[session.id] = createMappingSnapshot(currentPipeline)
          toast.success(t('header.saveSuccess', { name: currentPipeline.name }))
        } catch (error) {
          // A later step (mapping/pipeline validation or save) may fail after an earlier step
          // already created backend artifacts (a data sink, a mapping) and stashed their refs onto
          // currentPipeline. Persist that partial progress — still dirty — so a retry sees the
          // stashed entityId/logicalUrn and PUT-updates the existing artifact instead of re-POSTing
          // and leaking a duplicate.
          sessionManager.updateSessionPipeline(session.id, currentPipeline)

          if (isNotDraftError(error)) {
            notDraftNames.push(session.name)
          } else if (isSagaInFlightError(error)) {
            sagaInFlightNames.push(session.name)
          } else if (isDatapoolScopeViolationError(error)) {
            scopeViolationNames.push(session.name)
          } else if (isTableNameConflictError(error)) {
            tableNameConflictNames.push(session.name)
          } else if (isResourceInUseError(error)) {
            sinkInUsePipelineNames.push(session.name)
          } else if (isUnconfirmedDataLossError(error)) {
            dataLossPipelineNames.push(session.name)
          } else {
            saveFailedNames.push(session.name)
          }
        }
      }

      if (notDraftNames.length > 0) {
        toast.error(t('header.notDraftError'))
      }
      if (sagaInFlightNames.length > 0) {
        toast.error(t('header.sagaInFlightError'))
      }
      if (scopeViolationNames.length > 0) {
        toast.error(t('header.datasourceScopeViolation', { name: scopeViolationNames.join(', ') }))
      }
      if (tableNameConflictNames.length > 0) {
        toast.error(t('header.tableNameConflict', { names: tableNameConflictNames.join(', ') }))
      }
      if (sinkInUsePipelineNames.length > 0) {
        toast.error(t('header.dataSinkInUseError', { names: sinkInUsePipelineNames.join(', ') }))
      }
      if (dataLossPipelineNames.length > 0) {
        toast.error(t('header.unconfirmedDataLossError', { names: dataLossPipelineNames.join(', ') }))
      }
      if (saveFailedNames.length > 0) {
        toast.error(t('header.saveFailed', { names: saveFailedNames.join(', ') }))
      }
      // A failed sink delete does not fail the save: the pipeline is already stored.
      if (sinkStillInUseNames.length > 0) {
        toast.warning(t('header.sinkStillInUse', { names: sinkStillInUseNames.join(', ') }))
      }
      if (sinkDeleteFailedNames.length > 0) {
        toast.warning(t('header.sinkDeleteFailed', { names: sinkDeleteFailedNames.join(', ') }))
      }
      if (
        notDraftNames.length > 0 ||
        sagaInFlightNames.length > 0 ||
        scopeViolationNames.length > 0 ||
        tableNameConflictNames.length > 0 ||
        sinkInUsePipelineNames.length > 0 ||
        dataLossPipelineNames.length > 0 ||
        saveFailedNames.length > 0
      ) {
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
    isReadOnly,
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
      isLoadingEditor,

      // Cross-session operations
      saveAllPipelines,
      isSavingAll,
      hasAnyDirtySession,

      // Data sink locks
      getSinkLocks,
      getSinkLockReason,
      getSelectionLockReason,

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
      isLoadingEditor,
      saveAllPipelines,
      isSavingAll,
      hasAnyDirtySession,
      getSinkLocks,
      getSinkLockReason,
      getSelectionLockReason,
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
