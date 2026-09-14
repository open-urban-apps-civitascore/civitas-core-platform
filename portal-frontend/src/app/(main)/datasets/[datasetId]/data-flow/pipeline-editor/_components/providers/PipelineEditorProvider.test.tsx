import { act, render } from '@testing-library/react'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import React from 'react'
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

import { useActivePipeline } from '../../_hooks/use-active-pipeline'
import { usePipelineSession } from '../../_hooks/use-pipeline-session'
import {
  buildDataSinkPayloads,
  buildMappingArtifacts,
  createMappingSnapshot,
  getRemovedDataSinkIds,
  hasDataSinkChanged,
  hasMappingChanged,
  isDestructiveDataSinkChange,
  updateNodeData,
  updateNodeEntityId,
} from '../../_services/payloadBuilderService'
import { createEmptyPipeline } from '../../_services/pipelineService'
import { getNodeValidationSeverity, validatePipelineWithNodeStatus } from '../../_services/validationService'
import type { ControlNodeData } from '../../_types/nodes'
import type { PipelineNode, PipelineNodeType, PipelineOutputDTO } from '../../_types/pipeline'
import { PIPELINE_NODE_TYPES } from '../../_types/pipeline'
import type { PipelineSession } from '../../_types/session'
import { PipelineEditorProviderComponent } from './PipelineEditorProvider'

vi.mock('next/navigation', () => ({
  useParams: () => ({ datasetId: 'dataset-1' }),
  useSearchParams: () => new URLSearchParams(),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/hooks/use-register-unsaved-changes', () => ({
  useRegisterUnsavedChanges: vi.fn(),
}))

const mockReadOnly = vi.hoisted(() => ({ isReadOnly: false }))

vi.mock('../../_hooks/use-pipeline-read-only', () => ({
  useReadOnly: () => mockReadOnly,
  ReadOnlyProvider: ({ children }: { children: React.ReactNode }) => children,
}))

const mockDatasetPermissions = vi.hoisted(() => ({ canDeletePipeline: true }))

vi.mock('@/hooks/use-dataset-permissions', () => ({
  useDatasetPermissions: () => mockDatasetPermissions,
  useDatasetPermissionsById: () => ({ ...mockDatasetPermissions, isLoading: false }),
}))

vi.mock('@/app/services/api/pipelines/clientRequests', () => ({
  useGetPipelines: vi.fn(),
  useCreatePipeline: vi.fn(),
  useUpdatePipeline: vi.fn(),
  useDeletePipeline: vi.fn(),
}))

vi.mock('@/app/services/api/datasets/clientRequests', () => ({
  useGetDataset: vi.fn(),
}))

vi.mock('@/app/services/api/datasets/datasinks/clientRequests', () => ({
  useCreateDataSink: vi.fn(),
  useDeleteDataSink: vi.fn(),
  useUpdateDataSink: vi.fn(),
}))

vi.mock('@/app/services/api/mappings/clientRequests', () => ({
  useCreateMapping: vi.fn(),
  useUpdateMapping: vi.fn(),
}))

// Capture the latest WarningModal props so tests can drive the data-loss dialog (confirm/discard).
const warningModalRef = vi.hoisted(() => ({
  current: null as { open?: boolean; onConfirm?: () => void; onDiscard?: () => void } | null,
}))

vi.mock('@/components/modals/warning-modal/WarningModal', () => ({
  WarningModal: (props: { open?: boolean; onConfirm?: () => void; onDiscard?: () => void }) => {
    warningModalRef.current = props
    return null
  },
}))

vi.mock('../../_services/validationService', () => ({
  validatePipelineWithNodeStatus: vi.fn(),
  getNodeValidationSeverity: vi.fn().mockReturnValue('none'),
}))

vi.mock('../../_services/payloadBuilderService', () => ({
  buildPipelinePayload: vi.fn().mockReturnValue({
    name: 'Test',
    description: '',
    styles: { nodes: [], edges: [], nodePositions: {}, viewport: { x: 0, y: 0, zoom: 1 } },
    dataSourceIds: [],
    apis: [],
    dataSinks: [],
    model: {},
  }),
  buildDataSinkPayloads: vi.fn().mockReturnValue([]),
  buildMappingArtifacts: vi.fn().mockReturnValue([]),
  createDataSinkSnapshot: vi.fn().mockReturnValue({}),
  createMappingSnapshot: vi.fn().mockReturnValue({}),
  getRemovedDataSinkIds: vi.fn().mockReturnValue([]),
  hasDataSinkChanged: vi.fn().mockReturnValue(false),
  hasMappingChanged: vi.fn().mockReturnValue(true),
  isDestructiveDataSinkChange: vi.fn().mockReturnValue(false),
  updateNodeData: vi.fn().mockImplementation((pipeline: unknown) => pipeline),
  updateNodeEntityId: vi.fn().mockImplementation((pipeline: unknown) => pipeline),
}))

const contextRef = { current: null as ReturnType<typeof useActivePipeline> | null }

const Consumer: React.FC = () => {
  contextRef.current = useActivePipeline()
  return null
}

const makeSession = (overrides?: Partial<PipelineSession>): PipelineSession => ({
  id: 'session-1',
  name: 'Test Pipeline',
  pipeline: createEmptyPipeline('Test Pipeline'),
  isDirty: false,
  created: new Date(),
  lastModified: new Date(),
  ...overrides,
})

const makeNode = (id: string, type: PipelineNodeType = PIPELINE_NODE_TYPES.Start): PipelineNode => ({
  id,
  type,
  position: { x: 0, y: 0 },
  data: {
    label: type,
    configured: true,
    nodeType: type as ControlNodeData['nodeType'],
    description: '',
  } as ControlNodeData,
})

const makeGeoPersistenceNode = (id: string, entityId?: string, tableName = `table_${id}`): PipelineNode => ({
  id,
  type: PIPELINE_NODE_TYPES.GeoPersistence,
  position: { x: 0, y: 0 },
  data: {
    label: 'Geo Persistence',
    configured: true,
    entityType: 'persistence',
    entityId,
    tableName,
  } as unknown as ControlNodeData,
})

const renderProvider = (initialSession?: PipelineSession) => {
  const Wrapper: React.FC = () => {
    const sessionManager = usePipelineSession(initialSession)
    return (
      <PipelineEditorProviderComponent sessionManager={sessionManager}>
        <Consumer />
      </PipelineEditorProviderComponent>
    )
  }
  render(<Wrapper />)
}

const renderProviderWithSessions = (sessions: PipelineSession[]) => {
  const Wrapper: React.FC = () => {
    const sessionManager = usePipelineSession()
    const { loadSessions } = sessionManager
    React.useEffect(() => {
      loadSessions(sessions, sessions[0]?.id ?? null)
    }, [loadSessions])
    return (
      <PipelineEditorProviderComponent sessionManager={sessionManager}>
        <Consumer />
      </PipelineEditorProviderComponent>
    )
  }
  render(<Wrapper />)
}

let mockCreatePipelineMutateAsync: ReturnType<typeof vi.fn>
let mockUpdatePipelineMutateAsync: ReturnType<typeof vi.fn>
let mockDeleteMutate: ReturnType<typeof vi.fn>

beforeEach(() => {
  vi.clearAllMocks()
  contextRef.current = null

  mockReadOnly.isReadOnly = false
  mockDatasetPermissions.canDeletePipeline = true

  vi.mocked(buildDataSinkPayloads).mockReturnValue([])
  vi.mocked(buildMappingArtifacts).mockReturnValue([])
  vi.mocked(createMappingSnapshot).mockReturnValue({})
  vi.mocked(getRemovedDataSinkIds).mockReturnValue([])
  vi.mocked(hasDataSinkChanged).mockReturnValue(false)
  vi.mocked(hasMappingChanged).mockReturnValue(true)
  vi.mocked(updateNodeData).mockImplementation((pipeline: unknown) => pipeline as never)
  vi.mocked(updateNodeEntityId).mockImplementation((pipeline: unknown) => pipeline as never)

  mockCreatePipelineMutateAsync = vi.fn().mockResolvedValue({ data: { id: 'created-id' } })
  mockUpdatePipelineMutateAsync = vi.fn().mockResolvedValue({})
  mockDeleteMutate = vi.fn()

  vi.mocked(useGetPipelines).mockReturnValue({
    data: undefined,
    isLoading: false,
  } as unknown as ReturnType<typeof useGetPipelines>)

  vi.mocked(useGetDataset).mockReturnValue({
    data: { data: { provisioned: false } },
    isLoading: false,
  } as unknown as ReturnType<typeof useGetDataset>)

  vi.mocked(useCreatePipeline).mockReturnValue({
    mutate: vi.fn(),
    mutateAsync: mockCreatePipelineMutateAsync,
    isPending: false,
  } as unknown as ReturnType<typeof useCreatePipeline>)

  vi.mocked(useUpdatePipeline).mockReturnValue({
    mutate: vi.fn(),
    mutateAsync: mockUpdatePipelineMutateAsync,
    isPending: false,
  } as unknown as ReturnType<typeof useUpdatePipeline>)

  vi.mocked(useDeletePipeline).mockReturnValue({
    mutate: mockDeleteMutate,
    mutateAsync: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useDeletePipeline>)

  vi.mocked(useCreateDataSink).mockReturnValue({
    mutate: vi.fn(),
    mutateAsync: vi.fn().mockResolvedValue({ data: { id: 'dataSink-id' } }),
    isPending: false,
  } as unknown as ReturnType<typeof useCreateDataSink>)

  vi.mocked(useDeleteDataSink).mockReturnValue({
    mutate: vi.fn(),
    mutateAsync: vi.fn().mockResolvedValue({}),
    isPending: false,
  } as unknown as ReturnType<typeof useDeleteDataSink>)

  vi.mocked(useUpdateDataSink).mockReturnValue({
    mutate: vi.fn(),
    mutateAsync: vi.fn().mockResolvedValue({}),
    isPending: false,
  } as unknown as ReturnType<typeof useUpdateDataSink>)

  vi.mocked(useCreateMapping).mockReturnValue({
    mutate: vi.fn(),
    mutateAsync: vi.fn().mockResolvedValue({ data: { logicalUrn: 'urn:logical', versionedUrn: 'urn:versioned' } }),
    isPending: false,
  } as unknown as ReturnType<typeof useCreateMapping>)

  vi.mocked(useUpdateMapping).mockReturnValue({
    mutate: vi.fn(),
    mutateAsync: vi.fn().mockResolvedValue({ data: { logicalUrn: 'urn:logical', versionedUrn: 'urn:versioned-2' } }),
    isPending: false,
  } as unknown as ReturnType<typeof useUpdateMapping>)

  vi.mocked(validatePipelineWithNodeStatus).mockReturnValue({
    isValid: true,
    errors: [],
    warnings: [],
    nodeStatuses: new Map(),
  })
})

describe('PipelineEditorProviderComponent', () => {
  describe('backend loading', () => {
    it('loads sessions from backend DTOs on first render', () => {
      const dto: PipelineOutputDTO = {
        id: 'backend-pipeline-1',
        name: 'Backend Pipeline',
        description: 'From backend',
        styles: { nodes: [], edges: [], nodePositions: {}, viewport: { x: 0, y: 0, zoom: 1 } },
        dataSources: [],
        apis: [],
        dataSinks: [],
        model: {},
        createdAt: '2024-01-01T00:00:00Z',
        modifiedAt: '2024-01-01T00:00:00Z',
      }

      vi.mocked(useGetPipelines).mockReturnValue({
        data: { data: [dto] },
        isLoading: false,
      } as unknown as ReturnType<typeof useGetPipelines>)

      renderProvider()

      expect(contextRef.current?.pipeline?.id).toBe('backend-pipeline-1')
      expect(contextRef.current?.pipeline?.name).toBe('Backend Pipeline')
    })

    it('keeps the default empty session when the backend returns no pipelines', () => {
      vi.mocked(useGetPipelines).mockReturnValue({
        data: { data: [] },
        isLoading: false,
      } as unknown as ReturnType<typeof useGetPipelines>)

      renderProvider()

      expect(contextRef.current?.pipeline?.id).toBeUndefined()
    })
  })

  describe('isLoadingPipelines', () => {
    it('reflects the loading state of the pipelines query', () => {
      vi.mocked(useGetPipelines).mockReturnValue({
        data: undefined,
        isLoading: true,
      } as unknown as ReturnType<typeof useGetPipelines>)

      renderProvider()

      expect(contextRef.current?.isLoadingPipelines).toBe(true)
    })
  })

  describe('hasAnyDirtySession', () => {
    it('is false when no session is dirty', () => {
      renderProvider()
      expect(contextRef.current?.hasAnyDirtySession).toBe(false)
    })

    it('is true when the active session is dirty', () => {
      renderProvider(makeSession({ isDirty: true }))
      expect(contextRef.current?.hasAnyDirtySession).toBe(true)
    })
  })

  describe('dispatch', () => {
    it('marks session dirty for non-MARK_CLEAN actions', () => {
      renderProvider()

      expect(contextRef.current?.isDirty).toBe(false)

      act(() => {
        contextRef.current?.dispatch({ type: 'MARK_DIRTY' })
      })

      expect(contextRef.current?.isDirty).toBe(true)
    })

    it('marks session clean for MARK_CLEAN action', () => {
      renderProvider(makeSession({ isDirty: true }))

      expect(contextRef.current?.isDirty).toBe(true)

      act(() => {
        contextRef.current?.dispatch({ type: 'MARK_CLEAN' })
      })

      expect(contextRef.current?.isDirty).toBe(false)
    })
  })

  describe('addNode', () => {
    it('adds a node to the pipeline', () => {
      renderProvider()

      expect(contextRef.current?.pipeline?.nodes).toHaveLength(0)

      act(() => {
        contextRef.current?.addNode({
          nodeType: PIPELINE_NODE_TYPES.Start,
          position: { x: 100, y: 200 },
        })
      })

      expect(contextRef.current?.pipeline?.nodes).toHaveLength(1)
      expect(contextRef.current?.pipeline?.nodes[0].type).toBe(PIPELINE_NODE_TYPES.Start)
    })

    it('marks the session dirty after adding a node', () => {
      renderProvider()

      act(() => {
        contextRef.current?.addNode({
          nodeType: PIPELINE_NODE_TYPES.Start,
          position: { x: 0, y: 0 },
        })
      })

      expect(contextRef.current?.isDirty).toBe(true)
    })
  })

  describe('updateNode', () => {
    it('updates the specified node data', () => {
      const pipeline = { ...createEmptyPipeline('Test'), nodes: [makeNode('node-1')] }
      renderProvider(makeSession({ pipeline }))

      act(() => {
        contextRef.current?.updateNode('node-1', { label: 'Updated' })
      })

      expect(contextRef.current?.pipeline?.nodes[0].data.label).toBe('Updated')
    })
  })

  describe('deleteNodes', () => {
    it('removes the specified node from the pipeline', () => {
      const pipeline = {
        ...createEmptyPipeline('Test'),
        nodes: [makeNode('node-1'), makeNode('node-2', PIPELINE_NODE_TYPES.End)],
      }

      renderProvider(makeSession({ pipeline }))

      expect(contextRef.current?.pipeline?.nodes).toHaveLength(2)

      act(() => {
        contextRef.current?.deleteNodes(['node-1'])
      })

      expect(contextRef.current?.pipeline?.nodes).toHaveLength(1)
      expect(contextRef.current?.pipeline?.nodes[0].id).toBe('node-2')
    })

    it('also removes edges connected to deleted nodes', () => {
      const pipeline = {
        ...createEmptyPipeline('Test'),
        nodes: [makeNode('node-1'), makeNode('node-2', PIPELINE_NODE_TYPES.End)],
        edges: [{ id: 'edge-1', source: 'node-1', target: 'node-2', type: 'smoothstep', data: { label: '' } }],
      }

      renderProvider(makeSession({ pipeline }))

      act(() => {
        contextRef.current?.deleteNodes(['node-1'])
      })

      expect(contextRef.current?.pipeline?.edges).toHaveLength(0)
    })

    it('removes connected edges when React Flow emits edge + node removals in the same event', () => {
      const pipeline = {
        ...createEmptyPipeline('Test'),
        nodes: [makeNode('node-1'), makeNode('node-2', PIPELINE_NODE_TYPES.End)],
        edges: [{ id: 'edge-1', source: 'node-1', target: 'node-2', type: 'smoothstep', data: { label: '' } }],
      }

      renderProvider(makeSession({ pipeline }))

      // React Flow's native delete synchronously dispatches the connected-edge removal
      // first, then the node removal. Both must compose so no orphaned edge remains.
      act(() => {
        contextRef.current?.dispatch({ type: 'EDGE_CHANGES', payload: [{ type: 'remove', id: 'edge-1' }] })
        contextRef.current?.dispatch({ type: 'NODE_CHANGES', payload: [{ type: 'remove', id: 'node-1' }] })
      })

      expect(contextRef.current?.pipeline?.nodes).toHaveLength(1)
      expect(contextRef.current?.pipeline?.nodes[0].id).toBe('node-2')
      expect(contextRef.current?.pipeline?.edges).toHaveLength(0)
    })
  })

  describe('addEdge', () => {
    it('adds an edge for a valid connection', () => {
      const pipeline = {
        ...createEmptyPipeline('Test'),
        nodes: [makeNode('node-a'), makeNode('node-b', PIPELINE_NODE_TYPES.End)],
      }

      renderProvider(makeSession({ pipeline }))

      act(() => {
        contextRef.current?.addEdge({ source: 'node-a', target: 'node-b', sourceHandle: null, targetHandle: null })
      })

      expect(contextRef.current?.pipeline?.edges).toHaveLength(1)
      expect(contextRef.current?.pipeline?.edges[0].source).toBe('node-a')
      expect(contextRef.current?.pipeline?.edges[0].target).toBe('node-b')
    })

    it('does not add an edge for a self-connection', () => {
      const pipeline = {
        ...createEmptyPipeline('Test'),
        nodes: [makeNode('node-a')],
      }

      renderProvider(makeSession({ pipeline }))

      act(() => {
        contextRef.current?.addEdge({ source: 'node-a', target: 'node-a', sourceHandle: null, targetHandle: null })
      })

      expect(contextRef.current?.pipeline?.edges).toHaveLength(0)
    })

    it('does not add a duplicate edge between the same nodes', () => {
      const pipeline = {
        ...createEmptyPipeline('Test'),
        nodes: [makeNode('node-a'), makeNode('node-b', PIPELINE_NODE_TYPES.End)],
        edges: [{ id: 'edge-1', source: 'node-a', target: 'node-b', type: 'smoothstep', data: { label: '' } }],
      }

      renderProvider(makeSession({ pipeline }))

      act(() => {
        contextRef.current?.addEdge({ source: 'node-a', target: 'node-b', sourceHandle: null, targetHandle: null })
      })

      expect(contextRef.current?.pipeline?.edges).toHaveLength(1)
    })
  })

  describe('deleteEdges', () => {
    it('removes the specified edge from the pipeline', () => {
      const pipeline = {
        ...createEmptyPipeline('Test'),
        nodes: [makeNode('node-a'), makeNode('node-b', PIPELINE_NODE_TYPES.End)],
        edges: [
          { id: 'edge-1', source: 'node-a', target: 'node-b', type: 'smoothstep', data: { label: '' } },
          { id: 'edge-2', source: 'node-a', target: 'node-b', type: 'smoothstep', data: { label: '' } },
        ],
      }
      renderProvider(makeSession({ pipeline }))

      act(() => {
        contextRef.current?.deleteEdges(['edge-1'])
      })

      expect(contextRef.current?.pipeline?.edges).toHaveLength(1)
      expect(contextRef.current?.pipeline?.edges[0].id).toBe('edge-2')
    })
  })

  describe('validateConnection', () => {
    it('returns true for a valid connection between different nodes', () => {
      const pipeline = {
        ...createEmptyPipeline('Test'),
        nodes: [makeNode('node-a'), makeNode('node-b', PIPELINE_NODE_TYPES.End)],
      }
      renderProvider(makeSession({ pipeline }))

      expect(
        contextRef.current?.validateConnection({
          source: 'node-a',
          target: 'node-b',
          sourceHandle: null,
          targetHandle: null,
        }),
      ).toBe(true)
    })

    it('returns false for a self-connection', () => {
      const pipeline = { ...createEmptyPipeline('Test'), nodes: [makeNode('node-a')] }
      renderProvider(makeSession({ pipeline }))

      expect(
        contextRef.current?.validateConnection({
          source: 'node-a',
          target: 'node-a',
          sourceHandle: null,
          targetHandle: null,
        }),
      ).toBe(false)
    })
  })

  describe('selectNode', () => {
    it('marks the specified node as selected and deselects others', () => {
      const pipeline = {
        ...createEmptyPipeline('Test'),
        nodes: [makeNode('node-a'), makeNode('node-b', PIPELINE_NODE_TYPES.End)],
      }
      renderProvider(makeSession({ pipeline }))

      act(() => {
        contextRef.current?.selectNode('node-a')
      })

      const nodes = contextRef.current?.pipeline?.nodes
      expect(nodes?.find(n => n.id === 'node-a')?.selected).toBe(true)
      expect(nodes?.find(n => n.id === 'node-b')?.selected).toBe(false)
    })

    it('deselects all edges in single-select mode', () => {
      const pipeline = {
        ...createEmptyPipeline('Test'),
        nodes: [makeNode('node-a'), makeNode('node-b', PIPELINE_NODE_TYPES.End)],
        edges: [
          { id: 'edge-1', source: 'node-a', target: 'node-b', type: 'smoothstep', data: { label: '' }, selected: true },
        ],
      }
      renderProvider(makeSession({ pipeline }))

      act(() => {
        contextRef.current?.selectNode('node-a')
      })

      expect(contextRef.current?.pipeline?.edges[0].selected).toBe(false)
    })
  })

  describe('selectEdge', () => {
    it('marks the specified edge as selected and deselects others', () => {
      const pipeline = {
        ...createEmptyPipeline('Test'),
        nodes: [makeNode('node-a'), makeNode('node-b', PIPELINE_NODE_TYPES.End)],
        edges: [
          { id: 'edge-1', source: 'node-a', target: 'node-b', type: 'smoothstep', data: { label: '' } },
          { id: 'edge-2', source: 'node-a', target: 'node-b', type: 'smoothstep', data: { label: '' } },
        ],
      }
      renderProvider(makeSession({ pipeline }))

      act(() => {
        contextRef.current?.selectEdge('edge-1')
      })

      const edges = contextRef.current?.pipeline?.edges
      expect(edges?.find(e => e.id === 'edge-1')?.selected).toBe(true)
      expect(edges?.find(e => e.id === 'edge-2')?.selected).toBe(false)
    })

    it('deselects all nodes in single-select mode', () => {
      const pipeline = {
        ...createEmptyPipeline('Test'),
        nodes: [{ ...makeNode('node-a'), selected: true }, makeNode('node-b', PIPELINE_NODE_TYPES.End)],
        edges: [{ id: 'edge-1', source: 'node-a', target: 'node-b', type: 'smoothstep', data: { label: '' } }],
      }
      renderProvider(makeSession({ pipeline }))

      act(() => {
        contextRef.current?.selectEdge('edge-1')
      })

      expect(contextRef.current?.pipeline?.nodes.every(n => !n.selected)).toBe(true)
    })
  })

  describe('clearSelection', () => {
    it('deselects all nodes and edges', () => {
      const pipeline = {
        ...createEmptyPipeline('Test'),
        nodes: [{ ...makeNode('node-a'), selected: true }],
        edges: [
          { id: 'edge-1', source: 'node-a', target: 'node-b', type: 'smoothstep', data: { label: '' }, selected: true },
        ],
      }
      renderProvider(makeSession({ pipeline }))

      act(() => {
        contextRef.current?.clearSelection()
      })

      expect(contextRef.current?.pipeline?.nodes.every(n => !n.selected)).toBe(true)
      expect(contextRef.current?.pipeline?.edges.every(e => !e.selected)).toBe(true)
    })
  })

  describe('deleteSelected', () => {
    it('removes all selected nodes and their connected edges', () => {
      const pipeline = {
        ...createEmptyPipeline('Test'),
        nodes: [{ ...makeNode('node-a'), selected: true }, makeNode('node-b', PIPELINE_NODE_TYPES.End)],
        edges: [{ id: 'edge-1', source: 'node-a', target: 'node-b', type: 'smoothstep', data: { label: '' } }],
      }
      renderProvider(makeSession({ pipeline }))

      act(() => {
        contextRef.current?.deleteSelected()
      })

      expect(contextRef.current?.pipeline?.nodes).toHaveLength(1)
      expect(contextRef.current?.pipeline?.nodes[0].id).toBe('node-b')
      expect(contextRef.current?.pipeline?.edges).toHaveLength(0)
    })
  })

  describe('validation lifecycle', () => {
    it('clears isValidationRequired after runValidation', () => {
      renderProvider()

      expect(contextRef.current?.isValidationRequired).toBe(true)

      act(() => {
        contextRef.current?.runValidation()
      })

      expect(contextRef.current?.isValidationRequired).toBe(false)
    })

    it('resets isValidationRequired to true when pipeline changes after validation', () => {
      renderProvider()

      act(() => {
        contextRef.current?.runValidation()
      })
      expect(contextRef.current?.isValidationRequired).toBe(false)

      act(() => {
        contextRef.current?.addNode({ nodeType: PIPELINE_NODE_TYPES.Start, position: { x: 0, y: 0 } })
      })

      expect(contextRef.current?.isValidationRequired).toBe(true)
    })

    it('clears validationResult when pipeline changes after validation', () => {
      renderProvider()

      act(() => {
        contextRef.current?.runValidation()
      })
      expect(contextRef.current?.validationResult).not.toBeNull()

      act(() => {
        contextRef.current?.addNode({ nodeType: PIPELINE_NODE_TYPES.Start, position: { x: 0, y: 0 } })
      })

      expect(contextRef.current?.validationResult).toBeNull()
    })
  })

  describe('canSave', () => {
    it('is false when session is not dirty', () => {
      renderProvider()
      expect(contextRef.current?.canSave).toBe(false)
    })

    it('is false when dirty but validation has not been run', () => {
      renderProvider(makeSession({ isDirty: true }))
      expect(contextRef.current?.canSave).toBe(false)
    })

    it('is true when dirty, validation run, and no errors', () => {
      renderProvider(makeSession({ isDirty: true }))

      act(() => {
        contextRef.current?.runValidation()
      })

      expect(contextRef.current?.canSave).toBe(true)
    })

    it('is false when dirty, validation run, but has errors', () => {
      vi.mocked(validatePipelineWithNodeStatus).mockReturnValue({
        isValid: false,
        errors: [{ id: 'err-1', type: 'structure', messageKey: 'test.error', severity: 'error' }],
        warnings: [],
        nodeStatuses: new Map(),
      })

      renderProvider(makeSession({ isDirty: true }))

      act(() => {
        contextRef.current?.runValidation()
      })

      expect(contextRef.current?.canSave).toBe(false)
    })
  })

  describe('getNodeValidationSeverity', () => {
    it('returns the severity for the given node from the validation service', () => {
      vi.mocked(getNodeValidationSeverity).mockReturnValue('error')
      renderProvider()

      act(() => {
        contextRef.current?.runValidation()
      })

      expect(contextRef.current?.getNodeValidationSeverity('node-1')).toBe('error')
    })
  })

  describe('pipelineUsingTableName', () => {
    const sessionWithGeoNode = (sessionId: string, nodeId: string, tableName: string) =>
      makeSession({
        id: sessionId,
        name: sessionId,
        pipeline: {
          ...createEmptyPipeline(sessionId),
          id: `pipeline-${sessionId}`,
          nodes: [makeGeoPersistenceNode(nodeId, undefined, tableName)],
        },
      })

    it('is null for the name the node itself uses', () => {
      renderProvider(sessionWithGeoNode('session-1', 'persist-1', 'roads'))

      expect(contextRef.current?.pipelineUsingTableName('persist-1', 'roads')).toBeNull()
    })

    it('names the other pipeline of the dataset using the name, ignoring case', () => {
      renderProviderWithSessions([
        sessionWithGeoNode('session-1', 'persist-1', 'roads'),
        sessionWithGeoNode('session-2', 'persist-2', 'Roads'),
      ])

      expect(contextRef.current?.pipelineUsingTableName('persist-1', 'ROADS')).toBe('session-2')
    })

    it('is null for an empty name', () => {
      renderProvider(sessionWithGeoNode('session-1', 'persist-1', 'roads'))

      expect(contextRef.current?.pipelineUsingTableName('persist-2', '  ')).toBeNull()
    })

    it('passes the names used outside the validated pipeline to the validation service', () => {
      renderProviderWithSessions([
        sessionWithGeoNode('session-1', 'persist-1', 'roads'),
        sessionWithGeoNode('session-2', 'persist-2', 'Rivers'),
      ])

      act(() => {
        contextRef.current?.runValidation()
      })

      expect(vi.mocked(validatePipelineWithNodeStatus)).toHaveBeenLastCalledWith(expect.anything(), {
        tableNameOwners: { rivers: 'session-2' },
      })
    })
  })

  describe('hideValidationPanel', () => {
    it('sets shouldShowValidationPanel to false after runValidation', () => {
      renderProvider()

      act(() => {
        contextRef.current?.runValidation()
      })
      expect(contextRef.current?.shouldShowValidationPanel).toBe(true)

      act(() => {
        contextRef.current?.hideValidationPanel()
      })
      expect(contextRef.current?.shouldShowValidationPanel).toBe(false)
    })
  })

  describe('saveAllPipelines', () => {
    it('returns true without API calls when no sessions are dirty', async () => {
      renderProvider()

      let result: boolean | undefined
      await act(async () => {
        result = await contextRef.current?.saveAllPipelines()
      })

      expect(result).toBe(true)
      expect(mockCreatePipelineMutateAsync).not.toHaveBeenCalled()
      expect(mockUpdatePipelineMutateAsync).not.toHaveBeenCalled()
    })

    it('calls createPipeline when pipeline has no id', async () => {
      const session = makeSession({
        isDirty: true,
        pipeline: { ...createEmptyPipeline('Test'), id: undefined },
      })
      renderProvider(session)

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(mockCreatePipelineMutateAsync).toHaveBeenCalledOnce()
      expect(mockUpdatePipelineMutateAsync).not.toHaveBeenCalled()
    })

    it('writes the backend-assigned id back into the session after create', async () => {
      const session = makeSession({
        isDirty: true,
        pipeline: { ...createEmptyPipeline('Test'), id: undefined },
      })
      renderProvider(session)

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(contextRef.current?.pipeline?.id).toBe('created-id')
    })

    it('calls updatePipeline when pipeline has an id', async () => {
      const session = makeSession({
        isDirty: true,
        pipeline: { ...createEmptyPipeline('Test'), id: 'existing-pipeline-id' },
      })
      renderProvider(session)

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(mockUpdatePipelineMutateAsync).toHaveBeenCalledOnce()
      expect(mockCreatePipelineMutateAsync).not.toHaveBeenCalled()
    })

    it('calls createDataSink when a persistence node has no entityId', async () => {
      const mockCreateDataSinkMutateAsync = vi.fn().mockResolvedValue({ data: { id: 'new-dataSink-id' } })
      vi.mocked(useCreateDataSink).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: mockCreateDataSinkMutateAsync,
        isPending: false,
      } as unknown as ReturnType<typeof useCreateDataSink>)

      vi.mocked(buildDataSinkPayloads).mockReturnValue([
        { nodeId: 'persist-node-1', entityId: null, payload: { name: 'sink-1', type: 'postgres' } as never },
      ])

      const session = makeSession({
        isDirty: true,
        pipeline: {
          ...createEmptyPipeline('Test'),
          id: 'pipeline-1',
          nodes: [makeGeoPersistenceNode('persist-node-1')],
        },
      })
      renderProvider(session)

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(mockCreateDataSinkMutateAsync).toHaveBeenCalledOnce()
      expect(mockCreateDataSinkMutateAsync).toHaveBeenCalledWith({
        datasetId: 'dataset-1',
        data: { name: 'sink-1', type: 'postgres' },
      })
    })

    it('updates the node entityId after dataSink creation', async () => {
      const mockCreateDataSinkMutateAsync = vi.fn().mockResolvedValue({ data: { id: 'new-dataSink-id' } })
      vi.mocked(useCreateDataSink).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: mockCreateDataSinkMutateAsync,
        isPending: false,
      } as unknown as ReturnType<typeof useCreateDataSink>)

      vi.mocked(buildDataSinkPayloads).mockReturnValue([
        { nodeId: 'persist-node-1', entityId: null, payload: { name: 'sink-1', type: 'postgres' } as never },
      ])

      const updatedPipeline = {
        ...createEmptyPipeline('Test'),
        id: 'pipeline-1',
        nodes: [makeGeoPersistenceNode('persist-node-1', 'new-dataSink-id')],
      }
      vi.mocked(updateNodeEntityId).mockReturnValue(updatedPipeline)

      const session = makeSession({
        isDirty: true,
        pipeline: {
          ...createEmptyPipeline('Test'),
          id: 'pipeline-1',
          nodes: [makeGeoPersistenceNode('persist-node-1')],
        },
      })
      renderProvider(session)

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(updateNodeEntityId).toHaveBeenCalledWith(expect.anything(), 'persist-node-1', 'new-dataSink-id')
    })

    it('calls updateDataSink when a persistence node has changed', async () => {
      const mockUpdateDataSinkMutateAsync = vi.fn().mockResolvedValue({})
      vi.mocked(useUpdateDataSink).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: mockUpdateDataSinkMutateAsync,
        isPending: false,
      } as unknown as ReturnType<typeof useUpdateDataSink>)

      vi.mocked(buildDataSinkPayloads).mockReturnValue([
        {
          nodeId: 'persist-node-1',
          entityId: 'existing-dataSink-id',
          payload: { name: 'sink-1', type: 'postgres' } as never,
        },
      ])
      vi.mocked(hasDataSinkChanged).mockReturnValue(true)

      const session = makeSession({
        isDirty: true,
        pipeline: {
          ...createEmptyPipeline('Test'),
          id: 'pipeline-1',
          nodes: [makeGeoPersistenceNode('persist-node-1', 'existing-dataSink-id')],
        },
      })
      renderProvider(session)

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(mockUpdateDataSinkMutateAsync).toHaveBeenCalledOnce()
      expect(mockUpdateDataSinkMutateAsync).toHaveBeenCalledWith({
        datasetId: 'dataset-1',
        dataSinkId: 'existing-dataSink-id',
        data: { name: 'sink-1', type: 'postgres' },
      })
    })

    it('creates a mapping artifact via POST when the node has no prior logicalUrn', async () => {
      const mockCreateMappingMutateAsync = vi
        .fn()
        .mockResolvedValue({ data: { logicalUrn: 'urn:logical-1', versionedUrn: 'urn:versioned-1' } })
      vi.mocked(useCreateMapping).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: mockCreateMappingMutateAsync,
        isPending: false,
      } as unknown as ReturnType<typeof useCreateMapping>)

      const mappingBody = { source: 'urn:src', target: 'urn:tgt', fields: {}, title: 'Src-to-Tgt', positions: {} }
      vi.mocked(buildMappingArtifacts).mockReturnValue([
        { nodeId: 'map-1', logicalUrn: undefined, body: mappingBody as never },
      ])

      const session = makeSession({
        isDirty: true,
        pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1', nodes: [] },
      })
      renderProvider(session)

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(mockCreateMappingMutateAsync).toHaveBeenCalledOnce()
      expect(mockCreateMappingMutateAsync).toHaveBeenCalledWith(mappingBody)
    })

    it('updates an existing mapping via PUT when the node has a prior logicalUrn', async () => {
      const mockUpdateMappingMutateAsync = vi
        .fn()
        .mockResolvedValue({ data: { logicalUrn: 'urn:logical-1', versionedUrn: 'urn:versioned-2' } })
      vi.mocked(useUpdateMapping).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: mockUpdateMappingMutateAsync,
        isPending: false,
      } as unknown as ReturnType<typeof useUpdateMapping>)

      const mappingBody = { source: 'urn:src', target: 'urn:tgt', fields: {}, title: 'Src-to-Tgt', positions: {} }
      vi.mocked(buildMappingArtifacts).mockReturnValue([
        { nodeId: 'map-1', logicalUrn: 'urn:logical-1', body: mappingBody as never },
      ])

      const session = makeSession({
        isDirty: true,
        pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1', nodes: [] },
      })
      renderProvider(session)

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(mockUpdateMappingMutateAsync).toHaveBeenCalledOnce()
      expect(mockUpdateMappingMutateAsync).toHaveBeenCalledWith({ logicalUrn: 'urn:logical-1', ...mappingBody })
    })

    it('does not call updateMapping when an already-created mapping has not changed', async () => {
      const mockUpdateMappingMutateAsync = vi.fn().mockResolvedValue({})
      vi.mocked(useUpdateMapping).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: mockUpdateMappingMutateAsync,
        isPending: false,
      } as unknown as ReturnType<typeof useUpdateMapping>)

      const mappingBody = { source: 'urn:src', target: 'urn:tgt', fields: {}, title: 'Src-to-Tgt', positions: {} }
      vi.mocked(buildMappingArtifacts).mockReturnValue([
        { nodeId: 'map-1', logicalUrn: 'urn:logical-1', body: mappingBody as never },
      ])
      vi.mocked(hasMappingChanged).mockReturnValue(false)

      const session = makeSession({
        isDirty: true,
        pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1', nodes: [] },
      })
      renderProvider(session)

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(mockUpdateMappingMutateAsync).not.toHaveBeenCalled()
    })

    it('creates mapping artifacts before saving the pipeline, so the mappingRef makes it into the CORE model', async () => {
      const callOrder: string[] = []

      const mockCreateMappingMutateAsync = vi.fn().mockImplementation(async () => {
        callOrder.push('createMapping')
        return { data: { logicalUrn: 'urn:logical-1', versionedUrn: 'urn:versioned-1' } }
      })
      vi.mocked(useCreateMapping).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: mockCreateMappingMutateAsync,
        isPending: false,
      } as unknown as ReturnType<typeof useCreateMapping>)

      mockUpdatePipelineMutateAsync.mockImplementation(async () => {
        callOrder.push('updatePipeline')
        return {}
      })

      vi.mocked(buildMappingArtifacts).mockReturnValue([
        {
          nodeId: 'map-1',
          logicalUrn: undefined,
          body: { source: 'urn:src', target: 'urn:tgt', fields: {}, title: 'Src-to-Tgt', positions: {} } as never,
        },
      ])

      const session = makeSession({
        isDirty: true,
        pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1', nodes: [] },
      })
      renderProvider(session)

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(callOrder).toEqual(['createMapping', 'updatePipeline'])
      expect(updateNodeData).toHaveBeenCalledWith(expect.anything(), 'map-1', {
        mappingRef: 'urn:versioned-1',
        mappingLogicalUrn: 'urn:logical-1',
      })
    })

    it('retains refs obtained before a later save step fails, so a retry does not re-POST', async () => {
      const mockCreateDataSinkMutateAsync = vi
        .fn()
        .mockResolvedValue({ data: { id: 'new-dataSink-id', configurationUrn: 'urn:core:sink-config' } })
      vi.mocked(useCreateDataSink).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: mockCreateDataSinkMutateAsync,
        isPending: false,
      } as unknown as ReturnType<typeof useCreateDataSink>)

      vi.mocked(buildDataSinkPayloads).mockReturnValue([
        { nodeId: 'persist-node-1', entityId: null, payload: { name: 'sink-1', type: 'postgres' } as never },
      ])

      // The sink create succeeds and stashes its entityId onto the node (mirrors updateNodeEntityId's
      // real behavior, driven here via the mock since it is stubbed out for the whole file).
      const pipelineWithStashedSink = {
        ...createEmptyPipeline('Test'),
        id: 'pipeline-1',
        nodes: [makeGeoPersistenceNode('persist-node-1', 'new-dataSink-id')],
      }
      vi.mocked(updateNodeEntityId).mockReturnValue(pipelineWithStashedSink)
      vi.mocked(updateNodeData).mockReturnValue(pipelineWithStashedSink)

      // The pipeline save itself (after the sink was already created) fails.
      vi.mocked(useUpdatePipeline).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: vi.fn().mockRejectedValue(new Error('Pipeline save failed')),
        isPending: false,
      } as unknown as ReturnType<typeof useUpdatePipeline>)

      const session = makeSession({
        isDirty: true,
        pipeline: {
          ...createEmptyPipeline('Test'),
          id: 'pipeline-1',
          nodes: [makeGeoPersistenceNode('persist-node-1')],
        },
      })
      renderProvider(session)

      let result: boolean | undefined
      await act(async () => {
        result = await contextRef.current?.saveAllPipelines()
      })

      expect(result).toBe(false)
      // The sink WAS created — the partial pipeline (with the new entityId stashed) must be
      // persisted back into the session so a retry sees it and PUTs instead of re-POSTing.
      expect(contextRef.current?.pipeline?.nodes.find(n => n.id === 'persist-node-1')).toMatchObject({
        data: expect.objectContaining({ entityId: 'new-dataSink-id' }),
      })
    })

    it('does not call updateDataSink when dataSink has not changed', async () => {
      const mockUpdateDataSinkMutateAsync = vi.fn().mockResolvedValue({})
      vi.mocked(useUpdateDataSink).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: mockUpdateDataSinkMutateAsync,
        isPending: false,
      } as unknown as ReturnType<typeof useUpdateDataSink>)

      vi.mocked(buildDataSinkPayloads).mockReturnValue([
        {
          nodeId: 'persist-node-1',
          entityId: 'existing-dataSink-id',
          payload: { name: 'sink-1', type: 'postgres' } as never,
        },
      ])
      vi.mocked(hasDataSinkChanged).mockReturnValue(false)

      const session = makeSession({
        isDirty: true,
        pipeline: {
          ...createEmptyPipeline('Test'),
          id: 'pipeline-1',
          nodes: [makeGeoPersistenceNode('persist-node-1', 'existing-dataSink-id')],
        },
      })
      renderProvider(session)

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(mockUpdateDataSinkMutateAsync).not.toHaveBeenCalled()
    })

    it('calls deleteDataSink for removed persistence nodes before saving', async () => {
      const mockDeleteDataSinkMutateAsync = vi.fn().mockResolvedValue({})
      vi.mocked(useDeleteDataSink).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: mockDeleteDataSinkMutateAsync,
        isPending: false,
      } as unknown as ReturnType<typeof useDeleteDataSink>)

      vi.mocked(getRemovedDataSinkIds).mockReturnValue(['removed-dataSink-1', 'removed-dataSink-2'])

      const session = makeSession({
        isDirty: true,
        pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1' },
      })
      renderProvider(session)

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(mockDeleteDataSinkMutateAsync).toHaveBeenCalledTimes(2)
      expect(mockDeleteDataSinkMutateAsync).toHaveBeenCalledWith({
        datasetId: 'dataset-1',
        dataSinkId: 'removed-dataSink-1',
      })
      expect(mockDeleteDataSinkMutateAsync).toHaveBeenCalledWith({
        datasetId: 'dataset-1',
        dataSinkId: 'removed-dataSink-2',
      })
    })

    it('executes dataSink deletes before dataSink creates and pipeline save', async () => {
      const callOrder: string[] = []

      const mockDeleteDataSinkAsync = vi.fn().mockImplementation(async () => {
        callOrder.push('deleteDataSink')
        return {}
      })
      vi.mocked(useDeleteDataSink).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: mockDeleteDataSinkAsync,
        isPending: false,
      } as unknown as ReturnType<typeof useDeleteDataSink>)

      const mockCreateDataSinkAsync = vi.fn().mockImplementation(async () => {
        callOrder.push('createDataSink')
        return { data: { id: 'new-sink-id' } }
      })
      vi.mocked(useCreateDataSink).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: mockCreateDataSinkAsync,
        isPending: false,
      } as unknown as ReturnType<typeof useCreateDataSink>)

      mockUpdatePipelineMutateAsync.mockImplementation(async () => {
        callOrder.push('updatePipeline')
        return {}
      })

      vi.mocked(getRemovedDataSinkIds).mockReturnValue(['old-sink-1'])
      vi.mocked(buildDataSinkPayloads).mockReturnValue([
        { nodeId: 'persist-new', entityId: null, payload: { name: 'new-sink' } as never },
      ])

      const session = makeSession({
        isDirty: true,
        pipeline: {
          ...createEmptyPipeline('Test'),
          id: 'pipeline-1',
          nodes: [makeGeoPersistenceNode('persist-new')],
        },
      })
      renderProvider(session)

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(callOrder).toEqual(['deleteDataSink', 'createDataSink', 'updatePipeline'])
    })

    it('shows a success toast with the pipeline name after save', async () => {
      const session = makeSession({
        isDirty: true,
        pipeline: { ...createEmptyPipeline('My Pipeline'), id: 'pipeline-1' },
      })
      renderProvider(session)

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(toast.success).toHaveBeenCalledWith(expect.stringContaining('saveSuccess'))
    })

    it('does not save the pipeline when dataSink creation fails', async () => {
      const mockCreateDataSinkAsync = vi.fn().mockRejectedValue(new Error('DataSink creation failed'))
      vi.mocked(useCreateDataSink).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: mockCreateDataSinkAsync,
        isPending: false,
      } as unknown as ReturnType<typeof useCreateDataSink>)

      vi.mocked(buildDataSinkPayloads).mockReturnValue([
        { nodeId: 'persist-node', entityId: null, payload: { name: 'sink' } as never },
      ])

      const session = makeSession({
        isDirty: true,
        pipeline: {
          ...createEmptyPipeline('Test'),
          id: 'pipeline-1',
          nodes: [makeGeoPersistenceNode('persist-node')],
        },
      })
      renderProvider(session)

      let result: boolean | undefined
      await act(async () => {
        result = await contextRef.current?.saveAllPipelines()
      })

      expect(mockCreateDataSinkAsync).toHaveBeenCalledOnce()
      expect(result).toBe(false)
      expect(mockUpdatePipelineMutateAsync).not.toHaveBeenCalled()
      expect(toast.error).toHaveBeenCalled()
    })

    it('does not save the pipeline when dataSink deletion fails', async () => {
      vi.mocked(useDeleteDataSink).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: vi.fn().mockRejectedValue(new Error('DataSink deletion failed')),
        isPending: false,
      } as unknown as ReturnType<typeof useDeleteDataSink>)

      vi.mocked(getRemovedDataSinkIds).mockReturnValue(['old-sink-1'])

      const session = makeSession({
        isDirty: true,
        pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1' },
      })
      renderProvider(session)

      let result: boolean | undefined
      await act(async () => {
        result = await contextRef.current?.saveAllPipelines()
      })

      expect(result).toBe(false)
      expect(mockUpdatePipelineMutateAsync).not.toHaveBeenCalled()
      expect(toast.error).toHaveBeenCalled()
    })

    it('does not save the pipeline when dataSink update fails', async () => {
      vi.mocked(useUpdateDataSink).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: vi.fn().mockRejectedValue(new Error('DataSink update failed')),
        isPending: false,
      } as unknown as ReturnType<typeof useUpdateDataSink>)

      vi.mocked(buildDataSinkPayloads).mockReturnValue([
        { nodeId: 'persist-node', entityId: 'existing-sink', payload: { name: 'sink' } as never },
      ])
      vi.mocked(hasDataSinkChanged).mockReturnValue(true)

      const session = makeSession({
        isDirty: true,
        pipeline: {
          ...createEmptyPipeline('Test'),
          id: 'pipeline-1',
          nodes: [makeGeoPersistenceNode('persist-node', 'existing-sink')],
        },
      })
      renderProvider(session)

      let result: boolean | undefined
      await act(async () => {
        result = await contextRef.current?.saveAllPipelines()
      })

      expect(result).toBe(false)
      expect(mockUpdatePipelineMutateAsync).not.toHaveBeenCalled()
      expect(toast.error).toHaveBeenCalled()
    })

    it('handles multiple dataSink creates and updates in one save', async () => {
      const mockCreateDataSinkAsync = vi.fn().mockResolvedValue({ data: { id: 'new-sink-id' } })
      const mockUpdateDataSinkAsync = vi.fn().mockResolvedValue({})

      vi.mocked(useCreateDataSink).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: mockCreateDataSinkAsync,
        isPending: false,
      } as unknown as ReturnType<typeof useCreateDataSink>)

      vi.mocked(useUpdateDataSink).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: mockUpdateDataSinkAsync,
        isPending: false,
      } as unknown as ReturnType<typeof useUpdateDataSink>)

      vi.mocked(buildDataSinkPayloads).mockReturnValue([
        { nodeId: 'persist-new', entityId: null, payload: { name: 'new-sink' } as never },
        { nodeId: 'persist-existing', entityId: 'existing-sink-id', payload: { name: 'updated-sink' } as never },
      ])
      vi.mocked(hasDataSinkChanged).mockReturnValue(true)

      const session = makeSession({
        isDirty: true,
        pipeline: {
          ...createEmptyPipeline('Test'),
          id: 'pipeline-1',
          nodes: [
            makeGeoPersistenceNode('persist-new'),
            makeGeoPersistenceNode('persist-existing', 'existing-sink-id'),
          ],
        },
      })
      renderProvider(session)

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(mockCreateDataSinkAsync).toHaveBeenCalledOnce()
      expect(mockCreateDataSinkAsync).toHaveBeenCalledWith({
        datasetId: 'dataset-1',
        data: { name: 'new-sink' },
      })
      expect(mockUpdateDataSinkAsync).toHaveBeenCalledOnce()
      expect(mockUpdateDataSinkAsync).toHaveBeenCalledWith({
        datasetId: 'dataset-1',
        dataSinkId: 'existing-sink-id',
        data: { name: 'updated-sink' },
      })
    })

    it('saves pipeline with updated entityIds from dataSink creation responses', async () => {
      vi.mocked(useCreateDataSink).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: vi.fn().mockResolvedValue({ data: { id: 'backend-sink-id' } }),
        isPending: false,
      } as unknown as ReturnType<typeof useCreateDataSink>)

      vi.mocked(buildDataSinkPayloads).mockReturnValue([
        { nodeId: 'persist-node', entityId: null, payload: { name: 'sink' } as never },
      ])

      const pipelineWithUpdatedIds = {
        ...createEmptyPipeline('Test'),
        id: 'pipeline-1',
        nodes: [makeGeoPersistenceNode('persist-node', 'backend-sink-id')],
      }
      vi.mocked(updateNodeEntityId).mockReturnValue(pipelineWithUpdatedIds)

      const session = makeSession({
        isDirty: true,
        pipeline: {
          ...createEmptyPipeline('Test'),
          id: 'pipeline-1',
          nodes: [makeGeoPersistenceNode('persist-node')],
        },
      })
      renderProvider(session)

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(updateNodeEntityId).toHaveBeenCalledWith(expect.anything(), 'persist-node', 'backend-sink-id')
      expect(mockUpdatePipelineMutateAsync).toHaveBeenCalledOnce()
    })

    it('continues saving other pipelines when one fails due to dataSink error', async () => {
      const mockCreateDataSinkAsync = vi
        .fn()
        .mockRejectedValueOnce(new Error('Sink creation failed'))
        .mockResolvedValueOnce({ data: { id: 'new-sink-id' } })

      vi.mocked(useCreateDataSink).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: mockCreateDataSinkAsync,
        isPending: false,
      } as unknown as ReturnType<typeof useCreateDataSink>)

      vi.mocked(buildDataSinkPayloads).mockReturnValue([
        { nodeId: 'persist-node', entityId: null, payload: { name: 'sink' } as never },
      ])

      const session1 = makeSession({
        id: 'session-a',
        name: 'Pipeline A',
        isDirty: true,
        pipeline: {
          ...createEmptyPipeline('Pipeline A'),
          id: 'pipeline-a',
          nodes: [makeGeoPersistenceNode('persist-node')],
        },
      })
      const session2 = makeSession({
        id: 'session-b',
        name: 'Pipeline B',
        isDirty: true,
        pipeline: {
          ...createEmptyPipeline('Pipeline B'),
          id: 'pipeline-b',
          nodes: [makeGeoPersistenceNode('persist-node')],
        },
      })

      renderProviderWithSessions([session1, session2])

      let result: boolean | undefined
      await act(async () => {
        result = await contextRef.current?.saveAllPipelines()
      })

      expect(result).toBe(false)
      expect(mockUpdatePipelineMutateAsync).toHaveBeenCalledOnce()
      expect(toast.error).toHaveBeenCalled()
    })

    it('returns false without API calls when sessions share the same pipeline name', async () => {
      const session1 = makeSession({
        id: 'session-a',
        isDirty: true,
        pipeline: { ...createEmptyPipeline('Shared Name') },
      })
      const session2 = makeSession({
        id: 'session-b',
        isDirty: true,
        pipeline: { ...createEmptyPipeline('Shared Name') },
      })

      renderProviderWithSessions([session1, session2])

      let result: boolean | undefined
      await act(async () => {
        result = await contextRef.current?.saveAllPipelines()
      })

      expect(result).toBe(false)
      expect(mockCreatePipelineMutateAsync).not.toHaveBeenCalled()
      expect(mockUpdatePipelineMutateAsync).not.toHaveBeenCalled()
    })

    it('returns false without API calls when a dirty pipeline fails validation', async () => {
      vi.mocked(validatePipelineWithNodeStatus).mockReturnValue({
        isValid: false,
        errors: [{ id: 'err-1', type: 'structure', messageKey: 'test.error', severity: 'error' }],
        warnings: [],
        nodeStatuses: new Map(),
      })

      renderProvider(makeSession({ isDirty: true, pipeline: { ...createEmptyPipeline('Test'), id: undefined } }))

      let result: boolean | undefined
      await act(async () => {
        result = await contextRef.current?.saveAllPipelines()
      })

      expect(result).toBe(false)
      expect(mockCreatePipelineMutateAsync).not.toHaveBeenCalled()
    })

    it('returns false when the API call throws a generic error', async () => {
      mockCreatePipelineMutateAsync.mockRejectedValue(new Error('Network error'))

      renderProvider(makeSession({ isDirty: true, pipeline: { ...createEmptyPipeline('Test'), id: undefined } }))

      let result: boolean | undefined
      await act(async () => {
        result = await contextRef.current?.saveAllPipelines()
      })

      expect(result).toBe(false)
    })

    it('shows a scope violation toast with the pipeline name on a 422 error', async () => {
      const axiosError = new AxiosError(
        'Unprocessable Entity',
        undefined,
        { headers: new AxiosHeaders(), method: 'POST', url: '/pipelines' } as InternalAxiosRequestConfig,
        undefined,
        {
          status: 422,
          statusText: 'Unprocessable Entity',
          headers: new AxiosHeaders(),
          config: { headers: new AxiosHeaders(), method: 'POST', url: '/pipelines' } as InternalAxiosRequestConfig,
          data: {
            detail: 'DataSource "My DS" is not permitted for this datapool',
            type: 'urn:civitas:error:DATASOURCE_SCOPE_VIOLATION',
          },
        },
      )
      mockCreatePipelineMutateAsync.mockRejectedValue(axiosError)

      renderProvider(makeSession({ isDirty: true, pipeline: { ...createEmptyPipeline('Test'), id: undefined } }))

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(vi.mocked(toast.error)).toHaveBeenCalledWith('header.datasourceScopeViolation')
      expect(vi.mocked(toast.error)).not.toHaveBeenCalledWith(expect.stringContaining('header.saveFailed'))
    })

    it('reports a duplicate table name instead of a generic save failure on a 409 error', async () => {
      const axiosError = new AxiosError(
        'Conflict',
        undefined,
        { headers: new AxiosHeaders(), method: 'POST', url: '/datasinks' } as InternalAxiosRequestConfig,
        undefined,
        {
          status: 409,
          statusText: 'Conflict',
          headers: new AxiosHeaders(),
          config: { headers: new AxiosHeaders(), method: 'POST', url: '/datasinks' } as InternalAxiosRequestConfig,
          data: {
            detail: "DataSink with configuration.tableName 'roads' and dataSetId 'dataset-1' already exists",
            type: 'urn:civitas:error:CONFLICT',
          },
        },
      )
      mockCreatePipelineMutateAsync.mockRejectedValue(axiosError)

      renderProvider(makeSession({ isDirty: true, pipeline: { ...createEmptyPipeline('Test'), id: undefined } }))

      let result: boolean | undefined
      await act(async () => {
        result = await contextRef.current?.saveAllPipelines()
      })

      expect(result).toBe(false)
      expect(vi.mocked(toast.error)).toHaveBeenCalledWith('header.tableNameConflict')
      expect(vi.mocked(toast.error)).not.toHaveBeenCalledWith('header.saveFailed')
    })

    it('shows a not-draft toast instead of a generic save failure on a DATASET_NOT_EDITABLE error', async () => {
      const axiosError = new AxiosError(
        'Bad Request',
        undefined,
        { headers: new AxiosHeaders(), method: 'PUT', url: '/pipelines/pipeline-1' } as InternalAxiosRequestConfig,
        undefined,
        {
          status: 400,
          statusText: 'Bad Request',
          headers: new AxiosHeaders(),
          config: { headers: new AxiosHeaders(), method: 'PUT', url: '/pipelines/pipeline-1' } as InternalAxiosRequestConfig,
          data: {
            detail: 'DataSet must be in DRAFT to modify sub-entities',
            type: 'urn:civitas:error:DATASET_NOT_EDITABLE',
          },
        },
      )
      mockUpdatePipelineMutateAsync.mockRejectedValue(axiosError)

      renderProvider(makeSession({ isDirty: true, pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1' } }))

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(vi.mocked(toast.error)).toHaveBeenCalledWith('header.notDraftError')
      expect(vi.mocked(toast.error)).not.toHaveBeenCalledWith(expect.stringContaining('header.saveFailed'))
    })

    it('returns false on a DATASET_NOT_EDITABLE error', async () => {
      const axiosError = new AxiosError(
        'Bad Request',
        undefined,
        { headers: new AxiosHeaders(), method: 'PUT', url: '/pipelines/pipeline-1' } as InternalAxiosRequestConfig,
        undefined,
        {
          status: 400,
          statusText: 'Bad Request',
          headers: new AxiosHeaders(),
          config: { headers: new AxiosHeaders(), method: 'PUT', url: '/pipelines/pipeline-1' } as InternalAxiosRequestConfig,
          data: {
            detail: 'DataSet must be in DRAFT to modify sub-entities',
            type: 'urn:civitas:error:DATASET_NOT_EDITABLE',
          },
        },
      )
      mockUpdatePipelineMutateAsync.mockRejectedValue(axiosError)

      renderProvider(makeSession({ isDirty: true, pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1' } }))

      let result: boolean | undefined
      await act(async () => {
        result = await contextRef.current?.saveAllPipelines()
      })

      expect(result).toBe(false)
    })

    it('shows a saga-in-flight toast instead of a generic save failure on a RESOURCE_IN_USE error', async () => {
      const axiosError = new AxiosError(
        'Conflict',
        undefined,
        { headers: new AxiosHeaders(), method: 'PUT', url: '/pipelines/pipeline-1' } as InternalAxiosRequestConfig,
        undefined,
        {
          status: 409,
          statusText: 'Conflict',
          headers: new AxiosHeaders(),
          config: { headers: new AxiosHeaders(), method: 'PUT', url: '/pipelines/pipeline-1' } as InternalAxiosRequestConfig,
          data: {
            detail: 'Cannot write while a saga is in-flight: UNRELEASE',
            type: 'urn:civitas:error:RESOURCE_IN_USE',
          },
        },
      )
      mockUpdatePipelineMutateAsync.mockRejectedValue(axiosError)

      renderProvider(makeSession({ isDirty: true, pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1' } }))

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(vi.mocked(toast.error)).toHaveBeenCalledWith('header.sagaInFlightError')
      expect(vi.mocked(toast.error)).not.toHaveBeenCalledWith(expect.stringContaining('header.saveFailed'))
    })

    it('returns false on a RESOURCE_IN_USE error', async () => {
      const axiosError = new AxiosError(
        'Conflict',
        undefined,
        { headers: new AxiosHeaders(), method: 'PUT', url: '/pipelines/pipeline-1' } as InternalAxiosRequestConfig,
        undefined,
        {
          status: 409,
          statusText: 'Conflict',
          headers: new AxiosHeaders(),
          config: { headers: new AxiosHeaders(), method: 'PUT', url: '/pipelines/pipeline-1' } as InternalAxiosRequestConfig,
          data: {
            detail: 'Cannot write while a saga is in-flight: UNRELEASE',
            type: 'urn:civitas:error:RESOURCE_IN_USE',
          },
        },
      )
      mockUpdatePipelineMutateAsync.mockRejectedValue(axiosError)

      renderProvider(makeSession({ isDirty: true, pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1' } }))

      let result: boolean | undefined
      await act(async () => {
        result = await contextRef.current?.saveAllPipelines()
      })

      expect(result).toBe(false)
    })

    it('returns false on a 422 error', async () => {
      const axiosError = new AxiosError(
        'Unprocessable Entity',
        undefined,
        { headers: new AxiosHeaders(), method: 'POST', url: '/pipelines' } as InternalAxiosRequestConfig,
        undefined,
        {
          status: 422,
          statusText: 'Unprocessable Entity',
          headers: new AxiosHeaders(),
          config: { headers: new AxiosHeaders(), method: 'POST', url: '/pipelines' } as InternalAxiosRequestConfig,
          data: {
            detail: 'DataSource "My DS" is not permitted for this datapool',
            type: 'urn:civitas:error:DATASOURCE_SCOPE_VIOLATION',
          },
        },
      )
      mockCreatePipelineMutateAsync.mockRejectedValue(axiosError)

      renderProvider(makeSession({ isDirty: true, pipeline: { ...createEmptyPipeline('Test'), id: undefined } }))

      let result: boolean | undefined
      await act(async () => {
        result = await contextRef.current?.saveAllPipelines()
      })

      expect(result).toBe(false)
    })

    describe('data-loss confirmation', () => {
      const destructiveUpdateSession = () =>
        makeSession({
          isDirty: true,
          pipeline: {
            ...createEmptyPipeline('Test'),
            id: 'pipeline-1',
            nodes: [makeGeoPersistenceNode('persist-existing', 'existing-sink-id')],
          },
        })

      const armDestructiveUpdate = (mockUpdateDataSinkAsync: ReturnType<typeof vi.fn>) => {
        vi.mocked(useUpdateDataSink).mockReturnValue({
          mutate: vi.fn(),
          mutateAsync: mockUpdateDataSinkAsync,
          isPending: false,
        } as unknown as ReturnType<typeof useUpdateDataSink>)
        vi.mocked(useGetDataset).mockReturnValue({
          data: { data: { provisioned: true } },
          isLoading: false,
        } as unknown as ReturnType<typeof useGetDataset>)
        vi.mocked(buildDataSinkPayloads).mockReturnValue([
          { nodeId: 'persist-existing', entityId: 'existing-sink-id', payload: { name: 'sink' } as never },
        ])
        vi.mocked(hasDataSinkChanged).mockReturnValue(true)
        vi.mocked(isDestructiveDataSinkChange).mockReturnValue(true)
      }

      it('confirms the dialog and sends confirmDataLoss on a destructive change', async () => {
        const mockUpdateDataSinkAsync = vi.fn().mockResolvedValue({})
        armDestructiveUpdate(mockUpdateDataSinkAsync)

        renderProvider(destructiveUpdateSession())

        let savePromise: Promise<boolean | undefined> | undefined
        await act(async () => {
          savePromise = contextRef.current?.saveAllPipelines()
        })
        // Dialog is now open, awaiting the user's decision.
        expect(warningModalRef.current?.open).toBe(true)

        await act(async () => {
          warningModalRef.current?.onConfirm?.()
          await savePromise
        })

        expect(mockUpdateDataSinkAsync).toHaveBeenCalledWith({
          datasetId: 'dataset-1',
          dataSinkId: 'existing-sink-id',
          data: { name: 'sink', confirmDataLoss: true },
        })
      })

      it('cancels the dialog and saves nothing on a destructive change', async () => {
        const mockUpdateDataSinkAsync = vi.fn().mockResolvedValue({})
        armDestructiveUpdate(mockUpdateDataSinkAsync)

        renderProvider(destructiveUpdateSession())

        let savePromise: Promise<boolean | undefined> | undefined
        await act(async () => {
          savePromise = contextRef.current?.saveAllPipelines()
        })
        expect(warningModalRef.current?.open).toBe(true)

        let result: boolean | undefined
        await act(async () => {
          warningModalRef.current?.onDiscard?.()
          result = await savePromise
        })

        expect(result).toBe(false)
        expect(mockUpdateDataSinkAsync).not.toHaveBeenCalled()
      })

      it('rejects a second save-all while the data-loss dialog is open, then completes the first', async () => {
        const mockUpdateDataSinkAsync = vi.fn().mockResolvedValue({})
        armDestructiveUpdate(mockUpdateDataSinkAsync)

        renderProvider(destructiveUpdateSession())

        let firstSave: Promise<boolean | undefined> | undefined
        await act(async () => {
          firstSave = contextRef.current?.saveAllPipelines()
        })
        expect(warningModalRef.current?.open).toBe(true)

        // A second trigger while the dialog awaits confirmation must be rejected by the guard,
        // not reopen the dialog or overwrite the first save's pending resolve.
        let secondResult: boolean | undefined
        await act(async () => {
          secondResult = await contextRef.current?.saveAllPipelines()
        })
        expect(secondResult).toBe(false)
        expect(mockUpdateDataSinkAsync).not.toHaveBeenCalled()

        // The first save's promise is still live and resolves normally once confirmed.
        let firstResult: boolean | undefined
        await act(async () => {
          warningModalRef.current?.onConfirm?.()
          firstResult = await firstSave
        })
        expect(firstResult).toBe(true)
        expect(mockUpdateDataSinkAsync).toHaveBeenCalledTimes(1)
        expect(mockUpdateDataSinkAsync).toHaveBeenCalledWith({
          datasetId: 'dataset-1',
          dataSinkId: 'existing-sink-id',
          data: { name: 'sink', confirmDataLoss: true },
        })
      })

      it('does not open the dialog when the dataset is not provisioned', async () => {
        const mockUpdateDataSinkAsync = vi.fn().mockResolvedValue({})
        armDestructiveUpdate(mockUpdateDataSinkAsync)
        // Override: never provisioned → no table at risk → no dialog, no confirmDataLoss flag.
        vi.mocked(useGetDataset).mockReturnValue({
          data: { data: { provisioned: false } },
          isLoading: false,
        } as unknown as ReturnType<typeof useGetDataset>)

        renderProvider(destructiveUpdateSession())

        await act(async () => {
          await contextRef.current?.saveAllPipelines()
        })

        expect(warningModalRef.current?.open).toBeFalsy()
        expect(mockUpdateDataSinkAsync).toHaveBeenCalledWith({
          datasetId: 'dataset-1',
          dataSinkId: 'existing-sink-id',
          data: { name: 'sink' },
        })
      })
    })
  })

  describe('deletePipeline', () => {
    it('calls the delete mutation when pipeline has an id', () => {
      const session = makeSession({
        pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-to-delete' },
      })
      renderProvider(session)

      act(() => {
        contextRef.current?.deletePipeline()
      })

      expect(mockDeleteMutate).toHaveBeenCalledWith(
        'pipeline-to-delete',
        expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) }),
      )
    })

    it('removes the active session after successful deletion', () => {
      const session = makeSession({
        pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-to-delete' },
      })
      renderProvider(session)

      act(() => {
        contextRef.current?.deletePipeline()
      })

      const [, { onSuccess }] = mockDeleteMutate.mock.calls[0]
      act(() => {
        onSuccess()
      })

      expect(contextRef.current?.activeSessionId).not.toBe('session-1')
    })

    it('closes the session without a backend call when pipeline has no id', () => {
      const session = makeSession({
        pipeline: { ...createEmptyPipeline('Test'), id: undefined },
      })
      renderProvider(session)

      act(() => {
        contextRef.current?.deletePipeline()
      })

      expect(mockDeleteMutate).not.toHaveBeenCalled()
    })

    it('removes the active session when pipeline has no id', () => {
      const session = makeSession({
        pipeline: { ...createEmptyPipeline('Test'), id: undefined },
      })
      renderProvider(session)

      act(() => {
        contextRef.current?.deletePipeline()
      })

      expect(contextRef.current?.activeSessionId).not.toBe('session-1')
    })

    it('does not call the delete mutation when canDeletePipeline is false', () => {
      mockDatasetPermissions.canDeletePipeline = false

      const session = makeSession({
        pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-to-delete' },
      })
      renderProvider(session)

      act(() => {
        contextRef.current?.deletePipeline()
      })

      expect(mockDeleteMutate).not.toHaveBeenCalled()
    })

    it('does not remove the session when canDeletePipeline is false', () => {
      mockDatasetPermissions.canDeletePipeline = false

      const session = makeSession({
        pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-to-delete' },
      })
      renderProvider(session)

      act(() => {
        contextRef.current?.deletePipeline()
      })

      expect(contextRef.current?.activeSessionId).toBe('session-1')
    })

    it('shows a not-draft toast when the backend rejects with DATASET_NOT_EDITABLE', () => {
      const session = makeSession({
        pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-to-delete' },
      })
      renderProvider(session)

      act(() => {
        contextRef.current?.deletePipeline()
      })

      const [, { onError }] = mockDeleteMutate.mock.calls[0]
      const axiosError = new AxiosError(
        'Bad Request',
        undefined,
        { headers: new AxiosHeaders(), method: 'DELETE', url: '/pipelines/pipeline-to-delete' } as InternalAxiosRequestConfig,
        undefined,
        {
          status: 400,
          statusText: 'Bad Request',
          headers: new AxiosHeaders(),
          config: { headers: new AxiosHeaders(), method: 'DELETE', url: '/pipelines/pipeline-to-delete' } as InternalAxiosRequestConfig,
          data: {
            detail: 'DataSet must be in DRAFT',
            type: 'urn:civitas:error:DATASET_NOT_EDITABLE',
          },
        },
      )
      act(() => {
        onError(axiosError)
      })

      expect(vi.mocked(toast.error)).toHaveBeenCalledWith('header.notDraftError')
    })

    it('shows a saga-in-flight toast when the backend rejects with RESOURCE_IN_USE', () => {
      const session = makeSession({
        pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-to-delete' },
      })
      renderProvider(session)

      act(() => {
        contextRef.current?.deletePipeline()
      })

      const [, { onError }] = mockDeleteMutate.mock.calls[0]
      const axiosError = new AxiosError(
        'Conflict',
        undefined,
        { headers: new AxiosHeaders(), method: 'DELETE', url: '/pipelines/pipeline-to-delete' } as InternalAxiosRequestConfig,
        undefined,
        {
          status: 409,
          statusText: 'Conflict',
          headers: new AxiosHeaders(),
          config: { headers: new AxiosHeaders(), method: 'DELETE', url: '/pipelines/pipeline-to-delete' } as InternalAxiosRequestConfig,
          data: {
            detail: 'Cannot write while a saga is in-flight: UNRELEASE',
            type: 'urn:civitas:error:RESOURCE_IN_USE',
          },
        },
      )
      act(() => {
        onError(axiosError)
      })

      expect(vi.mocked(toast.error)).toHaveBeenCalledWith('header.sagaInFlightError')
    })

    it('shows a generic delete-failed toast on an unrecognised error', () => {
      const session = makeSession({
        pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-to-delete' },
      })
      renderProvider(session)

      act(() => {
        contextRef.current?.deletePipeline()
      })

      const [, { onError }] = mockDeleteMutate.mock.calls[0]
      act(() => {
        onError(new Error('Network failure'))
      })

      expect(vi.mocked(toast.error)).toHaveBeenCalledWith('toolbar.deleteFailed')
    })
  })

  describe('status guards', () => {
    it('saveAllPipelines returns false without API calls when isReadOnly is true', async () => {
      mockReadOnly.isReadOnly = true

      const session = makeSession({
        isDirty: true,
        pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1' },
      })
      renderProvider(session)

      let result: boolean | undefined
      await act(async () => {
        result = await contextRef.current?.saveAllPipelines()
      })

      expect(result).toBe(false)
      expect(mockUpdatePipelineMutateAsync).not.toHaveBeenCalled()
      expect(mockCreatePipelineMutateAsync).not.toHaveBeenCalled()
    })

    it('saveAllPipelines proceeds when isReadOnly is false', async () => {
      mockReadOnly.isReadOnly = false

      const session = makeSession({
        isDirty: true,
        pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1', nodes: [makeNode('n-1')] },
      })
      renderProvider(session)

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(mockUpdatePipelineMutateAsync).toHaveBeenCalled()
    })
  })
})
