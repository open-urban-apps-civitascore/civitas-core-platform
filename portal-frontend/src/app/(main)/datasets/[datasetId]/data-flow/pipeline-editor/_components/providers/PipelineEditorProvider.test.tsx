import { act, render } from '@testing-library/react'
import React from 'react'

import {
  useCreatePipeline,
  useDeletePipeline,
  useGetPipelines,
  useUpdatePipeline,
} from '@/app/services/api/pipelines/clientRequests'

import { useActivePipeline } from '../../_hooks/use-active-pipeline'
import { usePipelineSession } from '../../_hooks/use-pipeline-session'
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

vi.mock('@/app/services/api/pipelines/clientRequests', () => ({
  useGetPipelines: vi.fn(),
  useCreatePipeline: vi.fn(),
  useUpdatePipeline: vi.fn(),
  useDeletePipeline: vi.fn(),
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
    datasinks: [],
    model: {},
  }),
  syncDatasinkIds: vi.fn().mockImplementation((pipeline: unknown) => ({ pipeline, hasChanges: false })),
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

let mockCreateMutateAsync: ReturnType<typeof vi.fn>
let mockUpdateMutateAsync: ReturnType<typeof vi.fn>
let mockDeleteMutate: ReturnType<typeof vi.fn>

beforeEach(() => {
  vi.clearAllMocks()
  contextRef.current = null

  mockCreateMutateAsync = vi.fn().mockResolvedValue({ data: { id: 'created-id' } })
  mockUpdateMutateAsync = vi.fn().mockResolvedValue({})
  mockDeleteMutate = vi.fn()

  vi.mocked(useGetPipelines).mockReturnValue({
    data: undefined,
    isLoading: false,
  } as unknown as ReturnType<typeof useGetPipelines>)

  vi.mocked(useCreatePipeline).mockReturnValue({
    mutate: vi.fn(),
    mutateAsync: mockCreateMutateAsync,
    isPending: false,
  } as unknown as ReturnType<typeof useCreatePipeline>)

  vi.mocked(useUpdatePipeline).mockReturnValue({
    mutate: vi.fn(),
    mutateAsync: mockUpdateMutateAsync,
    isPending: false,
  } as unknown as ReturnType<typeof useUpdatePipeline>)

  vi.mocked(useDeletePipeline).mockReturnValue({
    mutate: mockDeleteMutate,
    mutateAsync: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useDeletePipeline>)

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
      expect(mockCreateMutateAsync).not.toHaveBeenCalled()
      expect(mockUpdateMutateAsync).not.toHaveBeenCalled()
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

      expect(mockCreateMutateAsync).toHaveBeenCalledOnce()
      expect(mockUpdateMutateAsync).not.toHaveBeenCalled()
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

      expect(mockUpdateMutateAsync).toHaveBeenCalledOnce()
      expect(mockCreateMutateAsync).not.toHaveBeenCalled()
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
      expect(mockCreateMutateAsync).not.toHaveBeenCalled()
      expect(mockUpdateMutateAsync).not.toHaveBeenCalled()
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
      expect(mockCreateMutateAsync).not.toHaveBeenCalled()
    })

    it('returns false when the API call throws', async () => {
      mockCreateMutateAsync.mockRejectedValue(new Error('Network error'))

      renderProvider(makeSession({ isDirty: true, pipeline: { ...createEmptyPipeline('Test'), id: undefined } }))

      let result: boolean | undefined
      await act(async () => {
        result = await contextRef.current?.saveAllPipelines()
      })

      expect(result).toBe(false)
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
  })
})
