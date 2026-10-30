import { act, render } from '@testing-library/react'
import React from 'react'
import { toast } from 'sonner'

import { mockApiError } from '@/__mocks__/errors/apiError.mock'
import { useGetDataset } from '@/app/services/api/datasets/clientRequests'
import {
  useCreateDataSink,
  useDeleteDataSink,
  useGetDataSinks,
  useUpdateDataSink,
} from '@/app/services/api/datasets/datasinks/clientRequests'
import { useCreateMapping, useDeleteMapping, useUpdateMapping } from '@/app/services/api/mappings/clientRequests'
import {
  useCreatePipeline,
  useDeletePipeline,
  useGetPipelines,
  useUpdatePipeline,
} from '@/app/services/api/pipelines/clientRequests'
import type { Datasource } from '@/types/datasources'

import { getNodeDef } from '../../_config/nodeRegistry'
import { useActivePipeline } from '../../_hooks/use-active-pipeline'
import { useNodeValidationSeverity } from '../../_hooks/use-node-validation-severity'
import { usePipelineSession } from '../../_hooks/use-pipeline-session'
import {
  buildDataSinkPayloads,
  buildMappingArtifacts,
  buildPipelinePayload,
  createMappingSnapshot,
  getRemovedDataSinkIds,
  getRemovedMappingUrns,
  hasDataSinkChanged,
  hasMappingChanged,
  isDestructiveDataSinkChange,
  MissingDataStructureUrnError,
  updateNodeData,
  updateNodeEntityId,
} from '../../_services/payloadBuilderService'
import { createEmptyPipeline } from '../../_services/pipelineService'
import { getNodeValidationSeverity, validatePipelineWithNodeStatus } from '../../_services/validationService'
import type { ControlNodeData, DataSourceNodeData } from '../../_types/nodes'
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
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
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

const mockDatasources = vi.hoisted(() => ({ entities: [] as Datasource[], isFetching: false }))
vi.mock('../../_hooks/use-pipeline-datasources', () => ({
  usePipelineDatasources: () => mockDatasources,
}))

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
  useGetDataSinks: vi.fn(() => ({ data: undefined })),
  useCreateDataSink: vi.fn(),
  useDeleteDataSink: vi.fn(),
  useUpdateDataSink: vi.fn(),
}))

vi.mock('@/app/services/api/mappings/clientRequests', () => ({
  useCreateMapping: vi.fn(),
  useUpdateMapping: vi.fn(),
  useDeleteMapping: vi.fn(),
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

vi.mock('../../_services/payloadBuilderService', async importOriginal => ({
  MissingDataStructureUrnError: (await importOriginal<typeof import('../../_services/payloadBuilderService')>())
    .MissingDataStructureUrnError,
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
  getRemovedMappingUrns: vi.fn().mockReturnValue([]),
  hasDataSinkChanged: vi.fn().mockReturnValue(false),
  hasMappingChanged: vi.fn().mockReturnValue(true),
  isDestructiveDataSinkChange: vi.fn().mockReturnValue(false),
  updateNodeData: vi.fn().mockImplementation((pipeline: unknown) => pipeline),
  updateNodeEntityId: vi.fn().mockImplementation((pipeline: unknown) => pipeline),
}))

const contextRef = { current: null as ReturnType<typeof useActivePipeline> | null }

const nodeSeverityRef = { current: null as ReturnType<typeof useNodeValidationSeverity> | null }

const Consumer: React.FC = () => {
  contextRef.current = useActivePipeline()
  nodeSeverityRef.current = useNodeValidationSeverity('node-1')
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

const makeResourceInUseError = () =>
  mockApiError(
    409,
    'Cannot delete urn:core:platform:civitas:mapping:common:00000000-0000-4000-8000-000000000043 — still referenced by: urn:core:platform:civitas:pipeline:common:00000000-0000-4000-8000-000000000044',
    'urn:civitas:error:RESOURCE_IN_USE',
  )

const renderProvider = (initialSession?: PipelineSession) => {
  const Wrapper: React.FC = () => {
    const sessionManager = usePipelineSession(initialSession)
    return (
      <PipelineEditorProviderComponent sessionManager={sessionManager}>
        <Consumer />
      </PipelineEditorProviderComponent>
    )
  }
  const view = render(<Wrapper />)
  return { rerender: () => view.rerender(<Wrapper />) }
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
  mockDatasources.entities = []
  mockDatasources.isFetching = false

  vi.mocked(buildDataSinkPayloads).mockReturnValue([])
  vi.mocked(buildMappingArtifacts).mockReturnValue([])
  vi.mocked(createMappingSnapshot).mockReturnValue({})
  vi.mocked(getRemovedDataSinkIds).mockReturnValue([])
  vi.mocked(getRemovedMappingUrns).mockReturnValue([])
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

  vi.mocked(useGetDataSinks).mockReturnValue({
    data: undefined,
    isLoading: false,
  } as unknown as ReturnType<typeof useGetDataSinks>)

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

  vi.mocked(useDeleteMapping).mockReturnValue({
    mutate: vi.fn(),
    mutateAsync: vi.fn().mockResolvedValue({}),
    isPending: false,
  } as unknown as ReturnType<typeof useDeleteMapping>)

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

  describe('isLoadingEditor', () => {
    it('reflects the loading state of the pipelines query', () => {
      vi.mocked(useGetPipelines).mockReturnValue({
        data: undefined,
        isLoading: true,
      } as unknown as ReturnType<typeof useGetPipelines>)

      renderProvider()

      expect(contextRef.current?.isLoadingEditor).toBe(true)
    })

    it('waits for the data sink locks', () => {
      vi.mocked(useGetDataSinks).mockReturnValue({
        data: undefined,
        isLoading: true,
      } as unknown as ReturnType<typeof useGetDataSinks>)

      renderProvider()

      expect(contextRef.current?.isLoadingEditor).toBe(true)
    })

    it('is false once both queries are done', () => {
      renderProvider()

      expect(contextRef.current?.isLoadingEditor).toBe(false)
    })

    it('waits for the first fetch of the Data source list', () => {
      mockDatasources.isFetching = true
      const { rerender } = renderProvider()
      expect(contextRef.current?.isLoadingEditor).toBe(true)

      mockDatasources.isFetching = false
      rerender()

      expect(contextRef.current?.isLoadingEditor).toBe(false)
    })

    it('stays false when the Data source list is fetched again later', () => {
      const { rerender } = renderProvider()

      mockDatasources.isFetching = true
      rerender()

      expect(contextRef.current?.isLoadingEditor).toBe(false)
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

    it('keeps session clean for node select and dimensions changes', () => {
      const pipeline = { ...createEmptyPipeline('Test'), nodes: [makeNode('node-a')] }
      renderProvider(makeSession({ pipeline }))

      act(() => {
        contextRef.current?.dispatch({
          type: 'NODE_CHANGES',
          payload: [
            { type: 'dimensions', id: 'node-a', dimensions: { width: 100, height: 50 } },
            { type: 'select', id: 'node-a', selected: true },
          ],
        })
      })

      expect(contextRef.current?.pipeline?.nodes[0].selected).toBe(true)
      expect(contextRef.current?.isDirty).toBe(false)
    })

    it('keeps session clean for edge select changes', () => {
      const pipeline = {
        ...createEmptyPipeline('Test'),
        nodes: [makeNode('node-a'), makeNode('node-b', PIPELINE_NODE_TYPES.End)],
        edges: [{ id: 'edge-1', source: 'node-a', target: 'node-b', type: 'smoothstep', data: { label: '' } }],
      }
      renderProvider(makeSession({ pipeline }))

      act(() => {
        contextRef.current?.dispatch({
          type: 'EDGE_CHANGES',
          payload: [{ type: 'select', id: 'edge-1', selected: true }],
        })
      })

      expect(contextRef.current?.isDirty).toBe(false)
    })

    it('marks session dirty for node position changes', () => {
      const pipeline = { ...createEmptyPipeline('Test'), nodes: [makeNode('node-a')] }
      renderProvider(makeSession({ pipeline }))

      act(() => {
        contextRef.current?.dispatch({
          type: 'NODE_CHANGES',
          payload: [{ type: 'position', id: 'node-a', position: { x: 10, y: 20 } }],
        })
      })

      expect(contextRef.current?.isDirty).toBe(true)
    })

    it('marks session dirty for node changes that mix select and position', () => {
      const pipeline = { ...createEmptyPipeline('Test'), nodes: [makeNode('node-a')] }
      renderProvider(makeSession({ pipeline }))

      act(() => {
        contextRef.current?.dispatch({
          type: 'NODE_CHANGES',
          payload: [
            { type: 'select', id: 'node-a', selected: true },
            { type: 'position', id: 'node-a', position: { x: 10, y: 20 } },
          ],
        })
      })

      expect(contextRef.current?.isDirty).toBe(true)
    })

    it('marks session dirty for edge remove changes', () => {
      const pipeline = {
        ...createEmptyPipeline('Test'),
        nodes: [makeNode('node-a'), makeNode('node-b', PIPELINE_NODE_TYPES.End)],
        edges: [{ id: 'edge-1', source: 'node-a', target: 'node-b', type: 'smoothstep', data: { label: '' } }],
      }
      renderProvider(makeSession({ pipeline }))

      act(() => {
        contextRef.current?.dispatch({
          type: 'EDGE_CHANGES',
          payload: [{ type: 'remove', id: 'edge-1' }],
        })
      })

      expect(contextRef.current?.pipeline?.edges).toHaveLength(0)
      expect(contextRef.current?.isDirty).toBe(true)
    })

    it('marks session dirty for a node select change together with a position change', () => {
      const pipeline = { ...createEmptyPipeline('Test'), nodes: [makeNode('node-a')] }
      renderProvider(makeSession({ pipeline }))

      act(() => {
        contextRef.current?.dispatch({
          type: 'NODE_CHANGES',
          payload: [
            { type: 'select', id: 'node-a', selected: true },
            { type: 'position', id: 'node-a', position: { x: 10, y: 20 } },
          ],
        })
      })

      expect(contextRef.current?.isDirty).toBe(true)
    })

    it('marks session dirty for edge remove changes', () => {
      const pipeline = {
        ...createEmptyPipeline('Test'),
        nodes: [makeNode('node-a'), makeNode('node-b', PIPELINE_NODE_TYPES.End)],
        edges: [{ id: 'edge-1', source: 'node-a', target: 'node-b', type: 'smoothstep', data: { label: '' } }],
      }
      renderProvider(makeSession({ pipeline }))

      act(() => {
        contextRef.current?.dispatch({ type: 'EDGE_CHANGES', payload: [{ type: 'remove', id: 'edge-1' }] })
      })

      expect(contextRef.current?.isDirty).toBe(true)
    })

    it('keeps session dirty for select changes after an edit', () => {
      const pipeline = { ...createEmptyPipeline('Test'), nodes: [makeNode('node-a')] }
      renderProvider(makeSession({ pipeline, isDirty: true }))

      act(() => {
        contextRef.current?.dispatch({
          type: 'NODE_CHANGES',
          payload: [{ type: 'select', id: 'node-a', selected: true }],
        })
      })

      expect(contextRef.current?.isDirty).toBe(true)
    })
  })

  describe('renamePipeline', () => {
    it('keeps session clean when the name does not change', () => {
      renderProvider(makeSession())

      act(() => {
        contextRef.current?.renamePipeline('Test Pipeline')
      })

      expect(contextRef.current?.isDirty).toBe(false)
    })

    it('marks session dirty when the name changes', () => {
      renderProvider(makeSession())

      act(() => {
        contextRef.current?.renamePipeline('Renamed Pipeline')
      })

      expect(contextRef.current?.isDirty).toBe(true)
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

    it.each(Object.values(PIPELINE_NODE_TYPES))(
      'adds a %s node at the drop position with the registry default data',
      nodeType => {
        renderProvider()

        act(() => {
          contextRef.current?.addNode({ nodeType, position: { x: 100, y: 200 } })
        })

        const added = contextRef.current?.pipeline?.nodes[0]
        expect(added?.type).toBe(nodeType)
        expect(added?.position).toEqual({ x: 100, y: 200 })
        expect(added?.data).toEqual(getNodeDef(nodeType)?.createDefaultData())
      },
    )
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

    it('keeps session clean', () => {
      const pipeline = { ...createEmptyPipeline('Test'), nodes: [makeNode('node-a')] }
      renderProvider(makeSession({ pipeline }))

      act(() => {
        contextRef.current?.selectNode('node-a')
      })

      expect(contextRef.current?.isDirty).toBe(false)
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

    it('deselects all nodes and edges, so a still-selected element can be clicked again', () => {
      // React Flow treats a click on an already-selected element as a no-op (no selection-change
      // event), so a node/edge Validate leaves selected can't be re-clicked to open its config
      // until something else is clicked first. Validate must clear the stale selection itself.
      const pipeline = {
        ...createEmptyPipeline('Test'),
        nodes: [{ ...makeNode('node-a'), selected: true }, makeNode('node-b', PIPELINE_NODE_TYPES.End)],
        edges: [
          { id: 'edge-1', source: 'node-a', target: 'node-b', type: 'smoothstep', data: { label: '' }, selected: true },
        ],
      }
      renderProvider(makeSession({ pipeline }))

      act(() => {
        contextRef.current?.runValidation()
      })

      expect(contextRef.current?.pipeline?.nodes.every(n => !n.selected)).toBe(true)
      expect(contextRef.current?.pipeline?.edges.every(e => !e.selected)).toBe(true)
      // Clearing the stale selection must not come at the cost of the validation panel itself:
      // the inspector shows it precisely when shouldShowValidationPanel && validationResult, so
      // both must still be set in the same pass that clears selectedNode/selectedEdge.
      expect(contextRef.current?.shouldShowValidationPanel).toBe(true)
      expect(contextRef.current?.validationResult).not.toBeNull()
      expect(contextRef.current?.selectedNode).toBeNull()
      expect(contextRef.current?.selectedEdge).toBeNull()
    })
  })

  describe('node validation context', () => {
    it('provides the severity of the active validation result to the nodes', () => {
      const result = { isValid: false, errors: [], warnings: [], nodeStatuses: new Map() }
      vi.mocked(validatePipelineWithNodeStatus).mockReturnValue(result)
      vi.mocked(getNodeValidationSeverity).mockReturnValue('error')
      renderProvider()

      act(() => {
        contextRef.current?.runValidation()
      })

      expect(getNodeValidationSeverity).toHaveBeenLastCalledWith('node-1', result)
      expect(nodeSeverityRef.current).toBe('error')
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

      expect(vi.mocked(validatePipelineWithNodeStatus)).toHaveBeenLastCalledWith(
        expect.anything(),
        expect.objectContaining({ tableNameOwners: { rivers: 'session-2' } }),
      )
    })

    it('passes the API call names of the HTTP data sources to the validation service', () => {
      const { rerender } = renderProvider(sessionWithGeoNode('session-1', 'persist-1', 'roads'))
      mockDatasources.entities = [
        {
          id: 'http-1',
          connector: { connectorType: 'HTTP', apiCalls: [{ name: 'Test Call 1', path: '/items' }] },
        } as unknown as Datasource,
        { id: 'sql-1', connector: { connectorType: 'SQL' } } as unknown as Datasource,
      ]
      rerender()

      act(() => {
        contextRef.current?.runValidation()
      })

      expect(vi.mocked(validatePipelineWithNodeStatus)).toHaveBeenLastCalledWith(
        expect.anything(),
        expect.objectContaining({ apiCallNamesByDatasourceId: new Map([['http-1', ['Test Call 1']]]) }),
      )
    })

    it('passes no API call names for an HTTP data source the frontend cannot read', () => {
      const { rerender } = renderProvider(sessionWithGeoNode('session-1', 'persist-1', 'roads'))
      mockDatasources.entities = [
        {
          id: 'http-1',
          connector: {
            connectorType: 'HTTP',
            apiCalls: [{ name: 'Test Call 1', path: '/items', pagination: { type: 'LIMIT_OFFSET' } }],
          },
        } as unknown as Datasource,
      ]
      rerender()

      act(() => {
        contextRef.current?.runValidation()
      })

      expect(vi.mocked(validatePipelineWithNodeStatus)).toHaveBeenLastCalledWith(
        expect.anything(),
        expect.objectContaining({ apiCallNamesByDatasourceId: new Map() }),
      )
    })

    it('passes node names in the UI language to the validation service', () => {
      renderProviderWithSessions([sessionWithGeoNode('session-1', 'persist-1', 'roads')])

      act(() => {
        contextRef.current?.runValidation()
      })

      const [pipeline, context] = vi.mocked(validatePipelineWithNodeStatus).mock.lastCall ?? []
      const geoNode = pipeline?.nodes.find(node => node.id === 'persist-1')
      expect(geoNode && context?.nodeName?.(geoNode)).toBe('nodeTypes.geoPersistence')
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

    it('deletes the mapping artifacts of removed mapping nodes', async () => {
      const mockDeleteMappingMutateAsync = vi.fn().mockResolvedValue({})
      vi.mocked(useDeleteMapping).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: mockDeleteMappingMutateAsync,
        isPending: false,
      } as unknown as ReturnType<typeof useDeleteMapping>)

      vi.mocked(getRemovedMappingUrns).mockReturnValue(['urn:logical-removed-1', 'urn:logical-removed-2'])

      const session = makeSession({
        isDirty: true,
        pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1' },
      })
      renderProvider(session)

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(mockDeleteMappingMutateAsync).toHaveBeenCalledTimes(2)
      expect(mockDeleteMappingMutateAsync).toHaveBeenCalledWith('urn:logical-removed-1')
      expect(mockDeleteMappingMutateAsync).toHaveBeenCalledWith('urn:logical-removed-2')
    })

    it('deletes removed mappings after the pipeline save', async () => {
      const callOrder: string[] = []

      const mockDeleteMappingMutateAsync = vi.fn().mockImplementation(async () => {
        callOrder.push('deleteMapping')
        return {}
      })
      vi.mocked(useDeleteMapping).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: mockDeleteMappingMutateAsync,
        isPending: false,
      } as unknown as ReturnType<typeof useDeleteMapping>)

      mockUpdatePipelineMutateAsync.mockImplementation(async () => {
        callOrder.push('updatePipeline')
        return {}
      })

      vi.mocked(getRemovedMappingUrns).mockReturnValue(['urn:logical-removed-1'])

      const session = makeSession({
        isDirty: true,
        pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1' },
      })
      renderProvider(session)

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(callOrder).toEqual(['updatePipeline', 'deleteMapping'])
    })

    it('does not delete a removed mapping when the pipeline save fails', async () => {
      const mockDeleteMappingMutateAsync = vi.fn().mockResolvedValue({})
      vi.mocked(useDeleteMapping).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: mockDeleteMappingMutateAsync,
        isPending: false,
      } as unknown as ReturnType<typeof useDeleteMapping>)

      vi.mocked(getRemovedMappingUrns).mockReturnValue(['urn:logical-removed-1'])
      mockUpdatePipelineMutateAsync.mockRejectedValue(new Error('save failed'))

      const session = makeSession({
        isDirty: true,
        pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1' },
      })
      renderProvider(session)

      let result: boolean | undefined
      await act(async () => {
        result = await contextRef.current?.saveAllPipelines()
      })

      expect(mockDeleteMappingMutateAsync).not.toHaveBeenCalled()
      expect(result).toBe(false)
    })

    it('reports that a removed mapping is still in use elsewhere, but keeps the save successful', async () => {
      const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
      vi.mocked(useDeleteMapping).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: vi.fn().mockRejectedValue(makeResourceInUseError()),
        isPending: false,
      } as unknown as ReturnType<typeof useDeleteMapping>)

      vi.mocked(getRemovedMappingUrns).mockReturnValue(['urn:logical-removed-1'])

      const session = makeSession({
        isDirty: true,
        pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1' },
      })
      renderProvider(session)

      let result: boolean | undefined
      await act(async () => {
        result = await contextRef.current?.saveAllPipelines()
      })

      expect(result).toBe(true)
      expect(toast.success).toHaveBeenCalled()
      expect(toast.error).toHaveBeenCalledWith('header.mappingStillInUse')
      expect(toast.error).not.toHaveBeenCalledWith('header.saveFailed')
      consoleError.mockRestore()
    })

    it('distinguishes an unexpected delete failure from a mapping that is still in use, but keeps the save successful', async () => {
      const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
      vi.mocked(useDeleteMapping).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: vi.fn().mockRejectedValue(new Error('network down')),
        isPending: false,
      } as unknown as ReturnType<typeof useDeleteMapping>)

      vi.mocked(getRemovedMappingUrns).mockReturnValue(['urn:logical-removed-1'])

      const session = makeSession({
        isDirty: true,
        pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1' },
      })
      renderProvider(session)

      let result: boolean | undefined
      await act(async () => {
        result = await contextRef.current?.saveAllPipelines()
      })

      expect(result).toBe(true)
      expect(toast.success).toHaveBeenCalled()
      expect(toast.error).toHaveBeenCalledWith('header.mappingCleanupFailed')
      expect(toast.error).not.toHaveBeenCalledWith('header.mappingStillInUse')
      expect(toast.error).not.toHaveBeenCalledWith('header.saveFailed')
      consoleError.mockRestore()
    })

    it('retains refs obtained before a later save step fails, so a retry does not re-POST', async () => {
      const mockCreateDataSinkMutateAsync = vi.fn().mockResolvedValue({
        data: {
          id: 'new-dataSink-id',
          configurationUrn: 'urn:core:platform:civitas:datasink:common:00000000-0000-4000-8000-000000000046:1.0.0',
        },
      })
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

    it('deletes removed dataSinks only after the pipeline was saved', async () => {
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

      expect(callOrder).toEqual(['createDataSink', 'updatePipeline', 'deleteDataSink'])
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

    it('keeps the saved pipeline when dataSink deletion fails', async () => {
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

      expect(result).toBe(true)
      expect(mockUpdatePipelineMutateAsync).toHaveBeenCalled()
      expect(toast.warning).toHaveBeenCalledWith(expect.stringContaining('sinkDeleteFailed'))
    })

    it('reports a still-referenced dataSink separately', async () => {
      const inUseError = mockApiError(
        409,
        'DataSink is referenced by one or more Layers',
        'urn:civitas:error:RESOURCE_IN_USE',
      )
      vi.mocked(useDeleteDataSink).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: vi.fn().mockRejectedValue(inUseError),
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

      expect(result).toBe(true)
      expect(toast.warning).toHaveBeenCalledWith(expect.stringContaining('sinkStillInUse'))
    })

    it('does not delete removed dataSinks when the pipeline save fails', async () => {
      const mockDeleteDataSinkAsync = vi.fn()
      vi.mocked(useDeleteDataSink).mockReturnValue({
        mutate: vi.fn(),
        mutateAsync: mockDeleteDataSinkAsync,
        isPending: false,
      } as unknown as ReturnType<typeof useDeleteDataSink>)

      vi.mocked(getRemovedDataSinkIds).mockReturnValue(['old-sink-1'])
      mockUpdatePipelineMutateAsync.mockRejectedValue(new Error('Pipeline save failed'))

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
      expect(mockDeleteDataSinkAsync).not.toHaveBeenCalled()
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
      const axiosError = mockApiError(
        422,
        'DataSource "My DS" is not permitted for this datapool',
        'urn:civitas:error:DATASOURCE_SCOPE_VIOLATION',
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
      const axiosError = mockApiError(
        409,
        "Another POSTGIS DataSink of this dataset already uses tableName 'roads'; they would share one physical table",
        'urn:civitas:error:UNIQUE_CONSTRAINT_VIOLATION',
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
      const axiosError = mockApiError(
        400,
        'DataSet must be in DRAFT to modify sub-entities',
        'urn:civitas:error:DATASET_NOT_EDITABLE',
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
      const axiosError = mockApiError(
        400,
        'DataSet must be in DRAFT to modify sub-entities',
        'urn:civitas:error:DATASET_NOT_EDITABLE',
      )
      mockUpdatePipelineMutateAsync.mockRejectedValue(axiosError)

      renderProvider(makeSession({ isDirty: true, pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1' } }))

      let result: boolean | undefined
      await act(async () => {
        result = await contextRef.current?.saveAllPipelines()
      })

      expect(result).toBe(false)
    })

    it('names the pipeline when a destructive sink change was not confirmed', async () => {
      const axiosError = mockApiError(
        409,
        "This change rebuilds the sink's table and discards all stored data; set confirmDataLoss=true to proceed",
        'urn:civitas:error:RESOURCE_IN_USE',
      )
      mockUpdatePipelineMutateAsync.mockRejectedValue(axiosError)

      renderProvider(makeSession({ isDirty: true, pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1' } }))

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(vi.mocked(toast.error)).toHaveBeenCalledWith('header.unconfirmedDataLossError')
      expect(vi.mocked(toast.error)).not.toHaveBeenCalledWith(expect.stringContaining('header.saveFailed'))
    })

    it('asks to re-add the geo persistence node when it has no data structure URN', async () => {
      vi.mocked(buildDataSinkPayloads).mockImplementation(() => {
        throw new MissingDataStructureUrnError('persist-1')
      })

      renderProvider(makeSession({ isDirty: true, pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1' } }))

      let result: boolean | undefined
      await act(async () => {
        result = await contextRef.current?.saveAllPipelines()
      })

      expect(result).toBe(false)
      expect(vi.mocked(toast.error)).toHaveBeenCalledWith('header.missingDataStructureUrn')
      expect(vi.mocked(toast.error)).not.toHaveBeenCalledWith(expect.stringContaining('header.saveFailed'))
    })

    it('names the pipeline when a data sink is still referenced by a layer', async () => {
      const axiosError = mockApiError(
        409,
        'DataSink is referenced by one or more Layers',
        'urn:civitas:error:RESOURCE_IN_USE',
      )
      mockUpdatePipelineMutateAsync.mockRejectedValue(axiosError)

      renderProvider(makeSession({ isDirty: true, pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1' } }))

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(vi.mocked(toast.error)).toHaveBeenCalledWith('header.dataSinkInUseError')
      expect(vi.mocked(toast.error)).not.toHaveBeenCalledWith(expect.stringContaining('header.saveFailed'))
    })

    it('shows a saga-in-flight toast instead of a generic save failure when a saga is running', async () => {
      const axiosError = mockApiError(
        409,
        'Cannot write while a saga is in-flight: UNRELEASE',
        'urn:civitas:error:SAGA_IN_FLIGHT',
      )
      mockUpdatePipelineMutateAsync.mockRejectedValue(axiosError)

      renderProvider(makeSession({ isDirty: true, pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1' } }))

      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })

      expect(vi.mocked(toast.error)).toHaveBeenCalledWith('header.sagaInFlightError')
      expect(vi.mocked(toast.error)).not.toHaveBeenCalledWith(expect.stringContaining('header.saveFailed'))
    })

    it('returns false when a saga is running', async () => {
      const axiosError = mockApiError(
        409,
        'Cannot write while a saga is in-flight: UNRELEASE',
        'urn:civitas:error:SAGA_IN_FLIGHT',
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
      const axiosError = mockApiError(
        422,
        'DataSource "My DS" is not permitted for this datapool',
        'urn:civitas:error:DATASOURCE_SCOPE_VIOLATION',
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

  describe('pipelineDeleteBlockReason', () => {
    beforeEach(() => {
      vi.mocked(useGetDataSinks).mockReturnValue({
        data: { data: [{ id: 'sink-1', pipelineId: 'pipeline-1', dataSinkType: 'FROST' }] },
        isLoading: false,
      } as unknown as ReturnType<typeof useGetDataSinks>)
      vi.mocked(useGetDataset).mockReturnValue({
        data: { data: { provisioned: false, namedApis: [{ name: 'STA', slug: 'sta', standard: 'STA' }] } },
        isLoading: false,
      } as unknown as ReturnType<typeof useGetDataset>)
    })

    it('returns the block reason of the active saved pipeline', () => {
      renderProvider(makeSession({ pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-1' } }))

      expect(contextRef.current?.pipelineDeleteBlockReason).toBe('lastFrostWithSta')
    })

    it('returns null for a never-saved pipeline without sink nodes', () => {
      renderProvider(makeSession({ pipeline: { ...createEmptyPipeline('Test'), id: undefined } }))

      expect(contextRef.current?.pipelineDeleteBlockReason).toBeNull()
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

    it('closes the session for a never-saved pipeline even without delete permission', () => {
      mockDatasetPermissions.canDeletePipeline = false

      const session = makeSession({
        pipeline: { ...createEmptyPipeline('Test'), id: undefined },
      })
      renderProvider(session)

      act(() => {
        contextRef.current?.deletePipeline()
      })

      expect(mockDeleteMutate).not.toHaveBeenCalled()
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
      const axiosError = mockApiError(400, 'DataSet must be in DRAFT', 'urn:civitas:error:DATASET_NOT_EDITABLE')
      act(() => {
        onError(axiosError)
      })

      expect(vi.mocked(toast.error)).toHaveBeenCalledWith('header.notDraftError')
    })

    it('shows a saga-in-flight toast when the backend rejects because a saga is running', () => {
      const session = makeSession({
        pipeline: { ...createEmptyPipeline('Test'), id: 'pipeline-to-delete' },
      })
      renderProvider(session)

      act(() => {
        contextRef.current?.deletePipeline()
      })

      const [, { onError }] = mockDeleteMutate.mock.calls[0]
      const axiosError = mockApiError(
        409,
        'Cannot write while a saga is in-flight: UNRELEASE',
        'urn:civitas:error:SAGA_IN_FLIGHT',
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

  describe('validation of all pipelines', () => {
    const INVALID_RESULT = {
      isValid: false,
      errors: [{ id: 'error-1', type: 'structure' as const, messageKey: 'test', severity: 'error' as const }],
      warnings: [],
      nodeStatuses: new Map(),
    }

    const dataSourceNode = (connector: string): PipelineNode => ({
      id: 'src-1',
      type: PIPELINE_NODE_TYPES.DataSource,
      position: { x: 0, y: 0 },
      data: {
        label: 'Source',
        configured: true,
        entityType: 'datasource',
        entityId: 'ds-1',
        entityMetadata: { connector },
      } satisfies DataSourceNodeData,
    })

    const pipelineDto = (id: string, nodes: PipelineNode[] = []): PipelineOutputDTO => ({
      id,
      name: id,
      description: '',
      styles: { nodes, edges: [], nodePositions: {}, viewport: { x: 0, y: 0, zoom: 1 } },
      dataSources: [],
      apis: [],
      dataSinks: [],
      model: {},
      createdAt: '2024-01-01T00:00:00Z',
      modifiedAt: '2024-01-01T00:00:00Z',
    })

    const sessionManagerRef = { current: null as ReturnType<typeof usePipelineSession> | null }

    const loadPipelines = (dtos: PipelineOutputDTO[]) => {
      vi.mocked(useGetPipelines).mockReturnValue({
        data: { data: dtos },
        isLoading: false,
      } as unknown as ReturnType<typeof useGetPipelines>)
      const Wrapper: React.FC = () => {
        const sessionManager = usePipelineSession()
        sessionManagerRef.current = sessionManager
        return (
          <PipelineEditorProviderComponent sessionManager={sessionManager}>
            <Consumer />
          </PipelineEditorProviderComponent>
        )
      }
      const view = render(<Wrapper />)
      return { rerender: () => view.rerender(<Wrapper />) }
    }

    const switchToPipeline = (pipelineId: string) => {
      const session = sessionManagerRef.current?.sessions.find(candidate => candidate.pipeline.id === pipelineId)
      act(() => {
        sessionManagerRef.current?.switchToSession(String(session?.id))
      })
    }

    const failValidationFor = (pipelineIds: string[]) => {
      vi.mocked(validatePipelineWithNodeStatus).mockImplementation(pipeline =>
        pipelineIds.includes(String(pipeline.id))
          ? INVALID_RESULT
          : { isValid: true, errors: [], warnings: [], nodeStatuses: new Map() },
      )
    }

    const validatedPipelineIds = () =>
      vi.mocked(validatePipelineWithNodeStatus).mock.calls.map(([pipeline]) => pipeline.id)

    const renderSessions = (sessions: PipelineSession[]) => {
      const Wrapper: React.FC = () => {
        const sessionManager = usePipelineSession()
        sessionManagerRef.current = sessionManager
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

    const savedSession = (id: string, isDirty = false, nodes: PipelineNode[] = []) =>
      makeSession({ id, name: id, isDirty, pipeline: { ...createEmptyPipeline(id), id, nodes } })

    const saveAll = async () => {
      await act(async () => {
        await contextRef.current?.saveAllPipelines()
      })
    }

    describe('on load', () => {
      it('validates every loaded pipeline once', () => {
        loadPipelines([pipelineDto('pipeline-a'), pipelineDto('pipeline-b')])

        expect(validatedPipelineIds()).toEqual(['pipeline-a', 'pipeline-b'])
      })

      it('does not validate in read-only mode', () => {
        mockReadOnly.isReadOnly = true

        loadPipelines([pipelineDto('pipeline-a')])

        expect(validatePipelineWithNodeStatus).not.toHaveBeenCalled()
      })

      it('validates the loaded pipelines when a refetch returns an additional pipeline', () => {
        mockDatasources.isFetching = true
        vi.mocked(useGetPipelines).mockReturnValue({
          data: { data: [pipelineDto('pipeline-a')] },
          isLoading: false,
        } as unknown as ReturnType<typeof useGetPipelines>)
        const { rerender } = renderProvider()

        vi.mocked(useGetPipelines).mockReturnValue({
          data: { data: [pipelineDto('pipeline-a'), pipelineDto('pipeline-b')] },
          isLoading: false,
        } as unknown as ReturnType<typeof useGetPipelines>)
        mockDatasources.isFetching = false
        rerender()

        expect(validatedPipelineIds()).toEqual(['pipeline-a'])
      })

      it('waits until the Data source list is fetched', () => {
        mockDatasources.isFetching = true
        const { rerender } = loadPipelines([pipelineDto('pipeline-a')])
        expect(validatePipelineWithNodeStatus).not.toHaveBeenCalled()

        mockDatasources.isFetching = false
        rerender()

        expect(validatedPipelineIds()).toEqual(['pipeline-a'])
      })

      it('validates with the fetched Data source list, not with the cached list', () => {
        mockDatasources.entities = [{ id: 'ds-1', connector: { connectorType: 'SQL' } } as unknown as Datasource]
        mockDatasources.isFetching = true
        const { rerender } = loadPipelines([pipelineDto('pipeline-a', [dataSourceNode('SQL')])])

        mockDatasources.entities = [{ id: 'ds-1', connector: { connectorType: 'HTTP' } } as unknown as Datasource]
        mockDatasources.isFetching = false
        rerender()

        expect(validatePipelineWithNodeStatus).toHaveBeenCalledOnce()
        const [validatedPipeline] = vi.mocked(validatePipelineWithNodeStatus).mock.calls[0]
        expect(validatedPipeline.nodes[0].data).toMatchObject({ entityMetadata: { connector: 'HTTP' } })
      })

      it('does not validate the default session when there are no pipelines', () => {
        loadPipelines([])

        expect(validatePipelineWithNodeStatus).not.toHaveBeenCalled()
      })

      it('opens the validation panel when the active pipeline has errors', () => {
        failValidationFor(['pipeline-a'])

        loadPipelines([pipelineDto('pipeline-a'), pipelineDto('pipeline-b')])

        expect(contextRef.current?.pipeline?.id).toBe('pipeline-a')
        expect(contextRef.current?.shouldShowValidationPanel).toBe(true)
        expect(contextRef.current?.sessionIdsWithErrors).toEqual(new Set([contextRef.current?.activeSessionId]))
      })

      it('opens the validation panel when the active pipeline is valid', () => {
        loadPipelines([pipelineDto('pipeline-a')])

        expect(contextRef.current?.shouldShowValidationPanel).toBe(true)
        expect(contextRef.current?.validationResult?.errors).toEqual([])
        expect(contextRef.current?.sessionIdsWithErrors.size).toBe(0)
      })

      it.each([
        ['an invalid', ['pipeline-a']],
        ['a valid', []],
      ])('removes the stored selection of %s pipeline and opens the panel', (_label, failingIds) => {
        failValidationFor(failingIds)

        loadPipelines([pipelineDto('pipeline-a', [{ ...dataSourceNode('SQL'), selected: true }])])

        expect(contextRef.current?.shouldShowValidationPanel).toBe(true)
        expect(contextRef.current?.pipeline?.nodes[0]).not.toHaveProperty('selected')
        expect(contextRef.current?.isDirty).toBe(false)
      })

      it('keeps the active tab when only another pipeline has errors', () => {
        failValidationFor(['pipeline-b'])

        loadPipelines([pipelineDto('pipeline-a'), pipelineDto('pipeline-b')])

        expect(contextRef.current?.pipeline?.id).toBe('pipeline-a')
        expect(contextRef.current?.shouldShowValidationPanel).toBe(true)
        expect(contextRef.current?.sessionIdsWithErrors.size).toBe(1)
        expect(contextRef.current?.sessionIdsWithErrors.has(String(contextRef.current?.activeSessionId))).toBe(false)
      })

      it('validates with the current connector type of the data source', () => {
        mockDatasources.entities = [{ id: 'ds-1', connector: { connectorType: 'HTTP' } } as unknown as Datasource]

        loadPipelines([pipelineDto('pipeline-a', [dataSourceNode('SQL')])])

        const [validatedPipeline] = vi.mocked(validatePipelineWithNodeStatus).mock.calls[0]
        expect(validatedPipeline.nodes[0].data).toMatchObject({ entityMetadata: { connector: 'HTTP' } })
      })

      it('keeps the panel open in the other pipeline after a tab switch', () => {
        loadPipelines([pipelineDto('pipeline-a'), pipelineDto('pipeline-b')])

        switchToPipeline('pipeline-b')

        expect(contextRef.current?.pipeline?.id).toBe('pipeline-b')
        expect(contextRef.current?.shouldShowValidationPanel).toBe(true)
      })

      it.each([
        ['selects a node', () => contextRef.current?.selectNode('src-1')],
        ['selects an element on the canvas', () => contextRef.current?.hideValidationPanel()],
      ])('closes the panel only in the pipeline where the user %s', (_label, closePanel) => {
        loadPipelines([pipelineDto('pipeline-a', [dataSourceNode('SQL')]), pipelineDto('pipeline-b')])

        act(() => {
          closePanel()
        })
        expect(contextRef.current?.shouldShowValidationPanel).toBe(false)

        switchToPipeline('pipeline-b')
        expect(contextRef.current?.shouldShowValidationPanel).toBe(true)

        switchToPipeline('pipeline-a')
        expect(contextRef.current?.shouldShowValidationPanel).toBe(false)
      })
    })

    describe('on save', () => {
      it.each([
        ['after a save', []],
        ['when the save stops because of errors', ['changed']],
      ])('opens the panel only in the changed pipeline %s', async (_label, failingIds) => {
        failValidationFor(failingIds)
        renderSessions([savedSession('changed', true), savedSession('unchanged', false)])

        await saveAll()
        expect(contextRef.current?.shouldShowValidationPanel).toBe(true)

        act(() => {
          sessionManagerRef.current?.switchToSession('unchanged')
        })
        expect(contextRef.current?.shouldShowValidationPanel).toBe(false)
      })

      it.each([
        ['after a save', []],
        ['when the save stops because of errors', ['changed']],
      ])('clears the selection only in the changed pipeline %s', async (_label, failingIds) => {
        failValidationFor(failingIds)
        const selectedNode = { ...makeNode('node-1'), selected: true }
        renderSessions([
          savedSession('changed', true, [selectedNode]),
          savedSession('unchanged', false, [selectedNode]),
        ])

        await saveAll()

        const sessions = sessionManagerRef.current?.sessions ?? []
        const isSelectedIn = (sessionId: string) =>
          sessions.find(session => session.id === sessionId)?.pipeline.nodes.some(node => node.selected)
        expect(isSelectedIn('changed')).toBe(false)
        expect(isSelectedIn('unchanged')).toBe(true)
      })

      it('saves nothing when a changed pipeline has errors', async () => {
        failValidationFor(['changed'])
        renderSessions([savedSession('changed', true), savedSession('unchanged', false)])

        await saveAll()

        expect(mockUpdatePipelineMutateAsync).not.toHaveBeenCalled()
        expect(toast.error).toHaveBeenCalledWith('header.validationFailed')
      })

      it('does not validate an unchanged saved pipeline', async () => {
        renderSessions([savedSession('changed', true), savedSession('unchanged', false)])

        await saveAll()

        expect(validatedPipelineIds()).toEqual(['changed'])
      })

      it('does not validate an unchanged pipeline that was never saved', async () => {
        const neverSaved = makeSession({ id: 'new', isDirty: false, pipeline: createEmptyPipeline('new') })
        renderSessions([savedSession('changed', true), neverSaved])

        await saveAll()

        expect(validatedPipelineIds()).toEqual(['changed'])
      })

      it('builds the payload with the current connector type of the data source', async () => {
        mockDatasources.entities = [{ id: 'ds-1', connector: { connectorType: 'HTTP' } } as unknown as Datasource]
        renderSessions([savedSession('changed', true, [dataSourceNode('SQL')])])

        await saveAll()

        const [savedPipeline] = vi.mocked(buildPipelinePayload).mock.calls[0]
        expect(savedPipeline.nodes[0].data).toMatchObject({ entityMetadata: { connector: 'HTTP' } })
      })
    })

    it('shows the validation result of the new active pipeline after a tab switch', () => {
      failValidationFor(['pipeline-1'])
      renderSessions([savedSession('pipeline-1'), savedSession('pipeline-2')])

      act(() => {
        contextRef.current?.runValidation()
      })
      expect(contextRef.current?.validationResult).toBe(INVALID_RESULT)

      act(() => {
        sessionManagerRef.current?.switchToSession('pipeline-2')
      })

      expect(contextRef.current?.validationResult).toBeNull()
    })

    it('opens the panel only for the active pipeline after "Validate"', () => {
      renderSessions([savedSession('pipeline-1'), savedSession('pipeline-2')])

      act(() => {
        contextRef.current?.runValidation()
      })
      expect(contextRef.current?.shouldShowValidationPanel).toBe(true)

      act(() => {
        sessionManagerRef.current?.switchToSession('pipeline-2')
      })
      expect(contextRef.current?.shouldShowValidationPanel).toBe(false)
    })

    it('keeps the validation result of a changed pipeline when the user switches to its tab', async () => {
      failValidationFor(['pipeline-2'])
      renderSessions([savedSession('pipeline-1', true), savedSession('pipeline-2', true)])
      await saveAll()

      act(() => {
        sessionManagerRef.current?.switchToSession('pipeline-2')
      })

      expect(contextRef.current?.validationResult).toBe(INVALID_RESULT)
      expect(contextRef.current?.sessionIdsWithErrors).toEqual(new Set(['pipeline-2']))
      expect(contextRef.current?.shouldShowValidationPanel).toBe(true)
    })

    it('clears the validation result when the user edits the pipeline after a tab switch', async () => {
      failValidationFor(['pipeline-2'])
      renderSessions([savedSession('pipeline-1', true), savedSession('pipeline-2', true)])
      await saveAll()
      act(() => {
        sessionManagerRef.current?.switchToSession('pipeline-2')
      })

      act(() => {
        contextRef.current?.addNode({ nodeType: PIPELINE_NODE_TYPES.Start, position: { x: 0, y: 0 } })
      })

      expect(contextRef.current?.validationResult).toBeNull()
      expect(contextRef.current?.sessionIdsWithErrors.has('pipeline-2')).toBe(false)
      expect(contextRef.current?.shouldShowValidationPanel).toBe(false)
    })
  })
})
