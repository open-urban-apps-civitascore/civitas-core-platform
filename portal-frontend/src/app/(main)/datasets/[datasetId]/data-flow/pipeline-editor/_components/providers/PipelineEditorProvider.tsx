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
import { useCreateMapping, useDeleteMapping, useUpdateMapping } from '@/app/services/api/mappings/clientRequests'
import {
  useCreatePipeline,
  useDeletePipeline,
  useGetPipelines,
  useUpdatePipeline,
} from '@/app/services/api/pipelines/clientRequests'
import { RemovalConfirmModal } from '@/components/modals/removal-confirm-modal/RemovalConfirmModal'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { CONNECTOR_TYPES } from '@/const/connectors'
import { useDatasetPermissions } from '@/hooks/use-dataset-permissions'
import { useRegisterUnsavedChanges } from '@/hooks/use-register-unsaved-changes'
import { useRemovalConfirmation } from '@/hooks/use-removal-confirmation'
import { isConnectorReadable } from '@/types/datasources'
import { type DataSinkRemoval, mergeRemovals, NO_REMOVAL } from '@/utils/dataSinkRemoval'
import {
  isDatapoolScopeViolationError,
  isNotDraftError,
  isResourceInUseError,
  isSagaInFlightError,
  isTableNameConflictError,
  isUnconfirmedDataLossError,
  unconfirmedRemoval,
} from '@/utils/errors'

import { getNodeDef } from '../../_config/nodeRegistry'
import { ActivePipelineProvider } from '../../_hooks/use-active-pipeline'
import { useDataSinkLocks } from '../../_hooks/use-datasink-locks'
import { useNodeName } from '../../_hooks/use-node-name'
import { NodeValidationContext } from '../../_hooks/use-node-validation-severity'
import { usePipelineDatasources } from '../../_hooks/use-pipeline-datasources'
import { useReadOnly } from '../../_hooks/use-pipeline-read-only'
import { tableNameOwnerOutsideNode, tableNameOwnersOutsideSession } from '../../_services/dataSinkNameService'
import { apiCallNamesOf, withCurrentConnectorTypes } from '../../_services/entityService'
import {
  buildDataSinkPayloads,
  buildMappingArtifacts,
  buildPipelinePayload,
  createDataSinkSnapshot,
  createMappingSnapshot,
  type DataSinkSnapshot,
  getRemovedMappingUrns,
  hasDataSinkChanged,
  hasMappingChanged,
  isDestructiveDataSinkChange,
  type MappingSnapshot,
  MissingDataStructureUrnError,
  updateNodeData,
  updateNodeEntityId,
} from '../../_services/payloadBuilderService'
import {
  createEmptyPipeline,
  getPipelineStats,
  getSelectedEdges,
  getSelectedNodes,
  isSemanticPipelineChange,
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
import type { PipelineSession, UsePipelineSessionReturn } from '../../_types/session'
import type { ValidationSeverity } from '../nodes/base/BasePipelineNode'

type CreatedDataSink = { nodeId: string; dataSinkId: string }
type CreatedMapping = { nodeId: string; logicalUrn: string }
type PreparedSession = {
  session: PipelineSession
  pipeline: Pipeline
  createdDataSinks: CreatedDataSink[]
  createdMappings: CreatedMapping[]
}

const withoutSelection = (pipeline: Pipeline): Pipeline => ({
  ...pipeline,
  nodes: pipeline.nodes.map(node => ({ ...node, selected: false })),
  edges: pipeline.edges.map(edge => ({ ...edge, selected: false })),
})

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
  const nodeName = useNodeName()
  const datasetId = params.datasetId
  const activeSession = sessionManager.getActiveSession()
  const activeSessionId = activeSession?.id
  const pipeline = activeSession?.pipeline || createEmptyPipeline()

  // ===== Track whether initial load has been done =====
  const hasLoadedRef = useRef(false)
  const loadedPipelineIdsRef = useRef<string[] | null>(null)

  // ===== Backend API Hooks =====
  const pipelinesQuery = useGetPipelines(datasetId)
  const datasetQuery = useGetDataset({ id: datasetId })
  const createPipelineMutation = useCreatePipeline(datasetId)
  const updatePipelineMutation = useUpdatePipeline(datasetId)
  const deletePipelineMutation = useDeletePipeline(datasetId)
  const createDataSinkMutation = useCreateDataSink()
  const updateDataSinkMutation = useUpdateDataSink()
  const deleteDataSinkMutation = useDeleteDataSink()
  const createMappingMutation = useCreateMapping(datasetId)
  const updateMappingMutation = useUpdateMapping(datasetId)
  const deleteMappingMutation = useDeleteMapping(datasetId)

  const { isReadOnly } = useReadOnly()

  const { entities: datasources, isFetching: isFetchingDatasources } = usePipelineDatasources()

  const [hasSettledDatasourcesOnOpen, setHasSettledDatasourcesOnOpen] = useState(false)
  useEffect(() => {
    if (!isFetchingDatasources) setHasSettledDatasourcesOnOpen(true)
  }, [isFetchingDatasources])

  const connectorTypeByDatasourceId = useMemo(
    () =>
      new Map(
        datasources.flatMap(datasource =>
          datasource.connector ? [[datasource.id, datasource.connector.connectorType] as const] : [],
        ),
      ),
    [datasources],
  )

  const currentPipelineOf = useCallback(
    (sessionPipeline: Pipeline) => withCurrentConnectorTypes(sessionPipeline, connectorTypeByDatasourceId),
    [connectorTypeByDatasourceId],
  )

  const { canDeletePipeline: canDelete } = useDatasetPermissions(datasetQuery.data?.data)

  const { getSinkLocks, getSinkLockReason, isLoading: isLoadingSinkLocks } = useDataSinkLocks()

  const {
    confirmRemoval: confirmDelete,
    sendConfirmingRemoval: sendDeleteConfirmingRemoval,
    dialog: deleteRemovalDialog,
  } = useRemovalConfirmation()
  const { confirmRemoval: confirmSaveRemoval, dialog: saveRemovalDialog } = useRemovalConfirmation()

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
    loadedPipelineIdsRef.current = pipelineDTOs.map(dto => dto.id)

    if (pipelineDTOs.length === 0) {
      // No pipelines in backend — keep the default empty session
      return
    }

    // Convert backend DTOs to sessions
    const sessions = pipelineDTOs.map(dto => createSessionFromBackendDTO(dto))
    // Preselect the session whose backend pipeline id matches the ?pipeline= search param,
    // so deep-links from the dataset overview open the right tab.
    const matchedSession = requestedPipelineId ? sessions.find(s => s.pipeline.id === requestedPipelineId) : undefined
    const initialActiveSessionId = matchedSession?.id ?? sessions[0]?.id ?? null

    // Load all sessions into the session manager
    sessionManager.loadSessions(sessions, initialActiveSessionId)

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
  const [validationResults, setValidationResults] = useState<Record<string, ValidationResultWithNodeStatus>>({})
  const validationResult = activeSessionId ? (validationResults[activeSessionId] ?? null) : null

  const setValidationResultFor = useCallback((sessionId: string, result: ValidationResultWithNodeStatus | null) => {
    setValidationResults(previous => {
      const next = { ...previous }
      if (result) {
        next[sessionId] = result
      } else {
        delete next[sessionId]
      }
      return next
    })
  }, [])

  const sessionIdsWithErrors = useMemo(() => {
    const sessionIds = Object.keys(validationResults)
    return new Set(sessionIds.filter(sessionId => validationResults[sessionId].errors.length > 0))
  }, [validationResults])
  const [sessionIdsWithValidationPanel, setSessionIdsWithValidationPanel] = useState<Set<string>>(new Set())
  const shouldShowValidationPanel = activeSessionId ? sessionIdsWithValidationPanel.has(activeSessionId) : false

  const showValidationPanelFor = useCallback((sessionIds: string[]) => {
    setSessionIdsWithValidationPanel(previous => new Set([...previous, ...sessionIds]))
  }, [])

  const hideValidationPanel = useCallback(() => {
    if (!activeSessionId) return
    setSessionIdsWithValidationPanel(previous => {
      const next = new Set(previous)
      next.delete(activeSessionId)
      return next
    })
  }, [activeSessionId])

  // ===== Track pipeline changes to invalidate validation =====
  const previousActiveSessionIdRef = useRef(activeSessionId)
  useEffect(() => {
    const isSameSession = previousActiveSessionIdRef.current === activeSessionId
    previousActiveSessionIdRef.current = activeSessionId
    // A tab switch keeps the result and the panel state of the new active pipeline.
    if (activeSessionId && activeSession?.isDirty && isSameSession) {
      setValidationResultFor(activeSessionId, null)
      hideValidationPanel()
    }
  }, [
    activeSession?.isDirty,
    activeSessionId,
    pipeline.nodes.length,
    pipeline.edges.length,
    setValidationResultFor,
    hideValidationPanel,
  ])

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

      if (isSemanticPipelineChange(action)) {
        sessionManager.markSessionDirty(activeSession.id)
      } else if (action.type === 'MARK_CLEAN') {
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
      hideValidationPanel()

      updatePipeline(p => ({
        ...p,
        nodes: p.nodes.map(node => ({
          ...node,
          selected: isMultiSelect ? (node.id === nodeId ? !node.selected : node.selected) : node.id === nodeId,
        })),
        edges: isMultiSelect ? p.edges : p.edges.map(edge => ({ ...edge, selected: false })),
      }))
    },
    [updatePipeline, hideValidationPanel],
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
      hideValidationPanel()

      updatePipeline(p => ({
        ...p,
        edges: p.edges.map(edge => ({
          ...edge,
          selected: isMultiSelect ? (edge.id === edgeId ? !edge.selected : edge.selected) : edge.id === edgeId,
        })),
        nodes: isMultiSelect ? p.nodes : p.nodes.map(node => ({ ...node, selected: false })),
      }))
    },
    [updatePipeline, hideValidationPanel],
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
    (sessionId: string): PipelineValidationContext => {
      const httpDatasources = datasources.filter(
        datasource =>
          datasource.connector?.connectorType === CONNECTOR_TYPES.HTTP && isConnectorReadable(datasource.connector),
      )
      return {
        tableNameOwners: tableNameOwnersOutsideSession(sessionManager.sessions, sessionId),
        apiCallNamesByDatasourceId: new Map(
          httpDatasources.map(datasource => [datasource.id, apiCallNamesOf(datasource)]),
        ),
        nodeName,
      }
    },
    [sessionManager.sessions, datasources, nodeName],
  )

  const pipelineUsingTableName = useCallback(
    (nodeId: string, tableName: string) => tableNameOwnerOutsideNode(sessionManager.sessions, nodeId, tableName),
    [sessionManager.sessions],
  )

  // ===== Validation Operations =====
  const runValidation = useCallback(() => {
    const result = validatePipelineWithNodeStatus(
      currentPipelineOf(pipeline),
      activeSessionId ? validationContextFor(activeSessionId) : {},
    )
    if (activeSessionId) setValidationResultFor(activeSessionId, result)
    // Show validation panel in inspector
    if (activeSessionId) showValidationPanelFor([activeSessionId])
    updatePipeline(withoutSelection)
    return result
  }, [
    pipeline,
    activeSessionId,
    validationContextFor,
    updatePipeline,
    currentPipelineOf,
    setValidationResultFor,
    showValidationPanelFor,
  ])

  const clearValidation = useCallback(() => {
    if (activeSessionId) setValidationResultFor(activeSessionId, null)
  }, [activeSessionId, setValidationResultFor])

  const getNodeValidationSeverity = useCallback(
    (nodeId: string): ValidationSeverity => {
      return getNodeValidationSeverityFn(nodeId, validationResult)
    },
    [validationResult],
  )

  // ===== Validate all saved pipelines once after load =====
  const hasValidatedOnLoadRef = useRef(false)
  useEffect(() => {
    const loadedPipelineIds = loadedPipelineIdsRef.current
    if (hasValidatedOnLoadRef.current || !loadedPipelineIds || isFetchingDatasources || isReadOnly) return
    const savedSessions = sessionManager.sessions.filter(session => session.pipeline.id)
    const sessionPipelineIds = new Set(savedSessions.map(session => session.pipeline.id))
    if (!loadedPipelineIds.every(pipelineId => sessionPipelineIds.has(pipelineId))) return
    hasValidatedOnLoadRef.current = true

    const results = savedSessions.map(
      session =>
        [
          session.id,
          validatePipelineWithNodeStatus(currentPipelineOf(session.pipeline), validationContextFor(session.id)),
        ] as const,
    )
    setValidationResults(previous => ({ ...previous, ...Object.fromEntries(results) }))
    showValidationPanelFor(savedSessions.map(session => session.id))
  }, [
    isFetchingDatasources,
    isReadOnly,
    sessionManager,
    sessionManager.sessions,
    currentPipelineOf,
    validationContextFor,
    showValidationPanelFor,
  ])

  // ===== Pipeline Operations: Rename =====
  const renamePipeline = useCallback(
    (newName: string) => {
      if (!activeSession) return
      sessionManager.updateSessionName(activeSession.id, newName.trim() || pipeline.name)
    },
    [activeSession, pipeline.name, sessionManager],
  )

  // ===== Pipeline Operations: Delete =====
  const [isDeleting, setIsDeleting] = useState(false)

  const deletePipeline = useCallback(async () => {
    if (!activeSession || isReadOnly) return

    const sessionId = activeSession.id
    const pipelineId = activeSession.pipeline.id

    // A never-saved pipeline exists only in the session, so discarding it needs no permission.
    if (!pipelineId) {
      sessionManager.closeSession(sessionId)
      return
    }

    if (!canDelete) return

    setIsDeleting(true)
    try {
      const isConfirmed = await confirmDelete(NO_REMOVAL)
      if (!isConfirmed) return
      const isDeleted = await sendDeleteConfirmingRemoval(isDataLossConfirmed =>
        deletePipelineMutation.mutateAsync({ pipelineId, isDataLossConfirmed }),
      )
      if (!isDeleted) return
      sessionManager.closeSession(sessionId)
    } catch (error) {
      if (isNotDraftError(error)) {
        toast.error(t('header.notDraftError'))
      } else if (isSagaInFlightError(error)) {
        toast.error(t('header.sagaInFlightError'))
      } else {
        console.error('Failed to delete pipeline:', error)
        toast.error(t('toolbar.deleteFailed'))
      }
    } finally {
      setIsDeleting(false)
    }
  }, [
    activeSession,
    isReadOnly,
    sessionManager,
    canDelete,
    confirmDelete,
    sendDeleteConfirmingRemoval,
    deletePipelineMutation,
    t,
  ])

  // ===== Loading State =====
  // Without the locks a provisioned node looks editable, so the editor waits for them too.
  const isLoadingEditor = pipelinesQuery.isLoading || isLoadingSinkLocks || !hasSettledDatasourcesOnOpen

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

    const failedNames: string[] = []
    for (const session of dirtySessions) {
      const result = validatePipelineWithNodeStatus(
        currentPipelineOf(session.pipeline),
        validationContextFor(session.id),
      )
      setValidationResultFor(session.id, result)
      if (!result.isValid) failedNames.push(session.name)
    }
    dirtySessions.forEach(session => sessionManager.updateSessionPipeline(session.id, withoutSelection))
    showValidationPanelFor(dirtySessions.map(session => session.id))

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
    const missingDataStructureUrnNames: string[] = []
    const notDraftNames: string[] = []
    const sagaInFlightNames: string[] = []
    const mappingInUseNames: string[] = []
    const mappingCleanupFailedNames: string[] = []
    let isAborted = false

    const classifySaveError = (name: string, error: unknown) => {
      if (isNotDraftError(error)) {
        notDraftNames.push(name)
      } else if (isSagaInFlightError(error)) {
        sagaInFlightNames.push(name)
      } else if (isDatapoolScopeViolationError(error)) {
        scopeViolationNames.push(name)
      } else if (isTableNameConflictError(error)) {
        tableNameConflictNames.push(name)
      } else if (isResourceInUseError(error)) {
        sinkInUsePipelineNames.push(name)
      } else if (isUnconfirmedDataLossError(error)) {
        dataLossPipelineNames.push(name)
      } else if (error instanceof MissingDataStructureUrnError) {
        missingDataStructureUrnNames.push(name)
      } else {
        saveFailedNames.push(name)
      }
    }

    // A Data sink that no Pipeline links stays active and keeps its APIs, so a save that is cancelled or fails before
    // its pipeline update deletes the ones it created and clears their refs from the nodes.
    const withoutCreatedDataSinks = async (pipeline: Pipeline, createdDataSinks: CreatedDataSink[]) => {
      let restored = pipeline
      for (const { nodeId, dataSinkId } of createdDataSinks) {
        try {
          await deleteDataSinkMutation.mutateAsync({ datasetId, dataSinkId })
          restored = updateNodeData(restored, nodeId, { entityId: undefined, configurationUrn: undefined })
        } catch (error) {
          // The kept ref lets the next save link this sink instead of creating another one.
          console.error('Failed to delete a data sink of the unsaved pipeline:', dataSinkId, error)
        }
      }
      return restored
    }

    // No Pipeline references a mapping that a cancelled save created, so the Dataset would keep it after the user
    // discards the session.
    const withoutCreatedMappings = async (pipeline: Pipeline, createdMappings: CreatedMapping[]) => {
      let restored = pipeline
      for (const { nodeId, logicalUrn } of createdMappings) {
        try {
          await deleteMappingMutation.mutateAsync(logicalUrn)
          restored = updateNodeData(restored, nodeId, { mappingRef: undefined, mappingLogicalUrn: undefined })
        } catch (error) {
          // The kept ref lets the next save version this mapping instead of creating another one.
          console.error('Failed to delete a mapping of the unsaved pipeline:', logicalUrn, error)
        }
      }
      return restored
    }

    const restoreUnsavedSession = async ({ session, pipeline, createdDataSinks, createdMappings }: PreparedSession) => {
      const restored = await withoutCreatedDataSinks(pipeline, createdDataSinks)
      sessionManager.updateSessionPipeline(session.id, await withoutCreatedMappings(restored, createdMappings))
    }

    /** Saves the pipeline of a prepared session; returns the removal the backend wants confirmed, if it refused one. */
    const savePreparedPipeline = async (
      { session, pipeline: prepared }: PreparedSession,
      isDataLossConfirmed: boolean,
    ): Promise<DataSinkRemoval | null> => {
      let currentPipeline = prepared
      try {
        // The CORE `model` is validated against the generated schema inside buildPipelinePayload.
        const pipelinePayload = buildPipelinePayload(currentPipeline)
        const pipelineId = currentPipeline.id

        if (pipelineId) {
          await updatePipelineMutation.mutateAsync({
            pipelineId,
            data: pipelinePayload,
            isDataLossConfirmed,
          })
        } else {
          const response = await createPipelineMutation.mutateAsync(pipelinePayload)
          currentPipeline = { ...currentPipeline, id: response.data.id }
        }

        // Delete removed mappings. Has to be executed after the pipeline save, otherwise the backend
        // refuses with 409.
        const mappingSnapshot = mappingSnapshotsRef.current[session.id] ?? {}
        for (const logicalUrn of getRemovedMappingUrns(currentPipeline, mappingSnapshot)) {
          try {
            await deleteMappingMutation.mutateAsync(logicalUrn)
          } catch (error) {
            // The save itself already succeeded, so a failed cleanup is reported on its own below.
            console.error('Failed to delete removed mapping of pipeline:', session.name, logicalUrn, error)
            const names = isResourceInUseError(error) ? mappingInUseNames : mappingCleanupFailedNames
            if (!names.includes(session.name)) names.push(session.name)
          }
        }

        sessionManager.updateSessionPipeline(session.id, { ...currentPipeline, isDirty: false })
        sessionManager.markSessionClean(session.id)
        dataSinkSnapshotsRef.current[session.id] = createDataSinkSnapshot(currentPipeline)
        mappingSnapshotsRef.current[session.id] = createMappingSnapshot(currentPipeline)
        toast.success(t('header.saveSuccess', { name: currentPipeline.name }))
      } catch (error) {
        const removal = isDataLossConfirmed ? null : unconfirmedRemoval(error)
        if (removal) return removal
        // Pipelines saved earlier in this run were planned with these new sinks staying, so deleting them now could
        // remove an API without a confirmation. Their kept refs let the next save link them.
        sessionManager.updateSessionPipeline(session.id, currentPipeline)
        classifySaveError(session.name, error)
      }
      return null
    }

    try {
      if (hasDestructiveChange) {
        const isConfirmed = await confirmDataLoss()
        if (!isConfirmed) return false
      }

      // Phase 1 saves the data sinks and mappings of every session before any pipeline update, so the backend
      // counts the new sinks when it plans a removal: a node replaced in one save keeps its API.
      const preparedSessions: PreparedSession[] = []
      // Serialize saves to avoid concurrent mutation state issues
      for (const session of dirtySessions) {
        // Declared outside the try below so the catch can still see whatever refs were stashed
        // onto it before the failing step.
        let currentPipeline = withoutSelection(currentPipelineOf(session.pipeline))
        const createdDataSinks: CreatedDataSink[] = []
        const createdMappings: CreatedMapping[] = []
        try {
          const snapshot = dataSinkSnapshotsRef.current[session.id] ?? {}

          // Save data sinks (create new / update changed). Stash the sink's CORE configurationUrn back onto
          // the node so it is emitted as the CORE model's `sinkRef`.
          const dataSinkPayloads = buildDataSinkPayloads(currentPipeline)
          for (const { nodeId, entityId, payload } of dataSinkPayloads) {
            if (!entityId) {
              const response = await createDataSinkMutation.mutateAsync({ datasetId, data: payload })
              createdDataSinks.push({ nodeId, dataSinkId: response.data.id })
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

          // Create/version mapping artifacts for configured mapping nodes (POST/PUT /v1/mappings). An
          // already-created mapping (has logicalUrn) whose body is unchanged since the last save is skipped —
          // the mapping API creates a new version per request, so sending it on every save (e.g. for an
          // unrelated node edit) would churn versions for nothing. Stash the returned versioned URN
          // (→ CORE model `mappingRef`) and logical URN (→ future PUT-versioning) back onto the node.
          // Validation happens inside buildMappingArtifacts, which throws on a schema-invalid mapping document.
          const mappingArtifacts = buildMappingArtifacts(currentPipeline)
          const mappingSnapshot = mappingSnapshotsRef.current[session.id] ?? {}
          for (const { nodeId, logicalUrn, body } of mappingArtifacts) {
            if (logicalUrn && !hasMappingChanged(nodeId, body, mappingSnapshot)) continue

            const response = logicalUrn
              ? await updateMappingMutation.mutateAsync({ logicalUrn, ...body })
              : await createMappingMutation.mutateAsync(body)
            if (!logicalUrn) createdMappings.push({ nodeId, logicalUrn: response.data.logicalUrn })
            currentPipeline = updateNodeData(currentPipeline, nodeId, {
              mappingRef: response.data.versionedUrn,
              mappingLogicalUrn: response.data.logicalUrn,
            })
          }

          preparedSessions.push({ session, pipeline: currentPipeline, createdDataSinks, createdMappings })
        } catch (error) {
          // A later step may fail after an earlier step already created a mapping and stashed its refs onto
          // currentPipeline. Persist that partial progress — still dirty — so a retry sees the stashed
          // logicalUrn and PUT-updates the existing mapping instead of re-POSTing and leaking a duplicate.
          sessionManager.updateSessionPipeline(
            session.id,
            await withoutCreatedDataSinks(currentPipeline, createdDataSinks),
          )
          classifySaveError(session.name, error)
        }
      }

      // Phase 2 saves the pipelines. The backend refuses an update that removes stored data, a Layer or an API
      // until it is confirmed, so those are asked once together and sent again.
      const unconfirmedSessions: { prepared: PreparedSession; removal: DataSinkRemoval }[] = []
      for (const prepared of preparedSessions) {
        const removal = await savePreparedPipeline(prepared, false)
        if (removal) unconfirmedSessions.push({ prepared, removal })
      }

      if (unconfirmedSessions.length > 0) {
        const isConfirmed = await confirmSaveRemoval(mergeRemovals(unconfirmedSessions.map(({ removal }) => removal)))
        isAborted = !isConfirmed
        for (const { prepared } of unconfirmedSessions) {
          if (isConfirmed) {
            await savePreparedPipeline(prepared, true)
          } else {
            await restoreUnsavedSession(prepared)
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
      if (missingDataStructureUrnNames.length > 0) {
        toast.error(t('header.missingDataStructureUrn', { names: missingDataStructureUrnNames.join(', ') }))
      }
      if (saveFailedNames.length > 0) {
        toast.error(t('header.saveFailed', { names: saveFailedNames.join(', ') }))
      }
      // The pipeline itself was saved in both cases, so neither fails the save.
      if (mappingInUseNames.length > 0) {
        toast.error(t('header.mappingStillInUse', { name: mappingInUseNames.join(', ') }))
      }
      if (mappingCleanupFailedNames.length > 0) {
        toast.error(t('header.mappingCleanupFailed', { name: mappingCleanupFailedNames.join(', ') }))
      }
      if (
        isAborted ||
        notDraftNames.length > 0 ||
        sagaInFlightNames.length > 0 ||
        scopeViolationNames.length > 0 ||
        tableNameConflictNames.length > 0 ||
        sinkInUsePipelineNames.length > 0 ||
        dataLossPipelineNames.length > 0 ||
        missingDataStructureUrnNames.length > 0 ||
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
    updateDataSinkMutation,
    deleteDataSinkMutation,
    createMappingMutation,
    updateMappingMutation,
    deleteMappingMutation,
    datasetId,
    datasetQuery.data,
    confirmDataLoss,
    confirmSaveRemoval,
    validationContextFor,
    currentPipelineOf,
    setValidationResultFor,
    showValidationPanelFor,
    t,
    isReadOnly,
  ])

  useRegisterUnsavedChanges(hasAnyDirtySession && !isReadOnly, saveAllPipelines)

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
      pipelineUsingTableName,
      shouldShowValidationPanel,
      hideValidationPanel,
      sessionIdsWithErrors,

      // Pipeline operations
      renamePipeline,
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
      pipelineUsingTableName,
      shouldShowValidationPanel,
      hideValidationPanel,
      sessionIdsWithErrors,
      renamePipeline,
      deletePipeline,
      isDeleting,
      isLoadingEditor,
      saveAllPipelines,
      isSavingAll,
      hasAnyDirtySession,
      getSinkLocks,
      getSinkLockReason,
    ],
  )

  return (
    <ActivePipelineProvider value={contextValue}>
      <NodeValidationContext.Provider value={getNodeValidationSeverity}>{children}</NodeValidationContext.Provider>
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
      <RemovalConfirmModal
        dialog={deleteRemovalDialog}
        title={t('toolbar.deleteConfirmTitle')}
        description={t('toolbar.deleteConfirmDescription')}
        confirmButtonTitle={t('toolbar.deletePipeline')}
      />
      <RemovalConfirmModal
        dialog={saveRemovalDialog}
        title={t('removalConfirm.title')}
        description={t('removalConfirm.description')}
        confirmButtonTitle={t('removalConfirm.confirm')}
      />
    </ActivePipelineProvider>
  )
}
