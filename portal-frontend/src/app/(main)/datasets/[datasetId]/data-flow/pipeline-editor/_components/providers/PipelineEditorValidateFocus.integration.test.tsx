/**
 * Full-stack regression coverage for the "Validate leaves focus stuck" fix: renders the real
 * provider together with the real toolbar/canvas/inspector (not the isolated, mocked-context
 * unit tests those components have on their own) and drives it with actual DOM clicks, so it
 * exercises the whole Validate -> selection-clear -> re-click path end to end.
 */
import { act, fireEvent, render, screen } from '@testing-library/react'
import React from 'react'

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

import { useActivePipeline } from '../../_hooks/use-active-pipeline'
import { usePipelineSession } from '../../_hooks/use-pipeline-session'
import { createEmptyPipeline } from '../../_services/pipelineService'
import { validatePipelineWithNodeStatus } from '../../_services/validationService'
import { PIPELINE_NODE_TYPES } from '../../_types/pipeline'
import type { PipelineSession } from '../../_types/session'
import { PipelineCanvas } from '../canvas/PipelineCanvas'
import { PipelineInspector } from '../inspector/PipelineInspector'
import { PipelineToolbar } from '../tabs/PipelineToolbar'
import { PipelineEditorProviderComponent } from './PipelineEditorProvider'

vi.mock('next/navigation', () => ({
  useParams: () => ({ datasetId: 'dataset-1' }),
  useSearchParams: () => new URLSearchParams(),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-register-unsaved-changes', () => ({
  useRegisterUnsavedChanges: vi.fn(),
}))

vi.mock('../../_hooks/use-pipeline-read-only', () => ({
  useReadOnly: () => ({ isReadOnly: false }),
  ReadOnlyProvider: ({ children }: { children: React.ReactNode }) => children,
}))

const mockDatasources = vi.hoisted(() => ({ entities: [], isFetching: false }))
vi.mock('../../_hooks/use-pipeline-datasources', () => ({
  usePipelineDatasources: () => mockDatasources,
}))

vi.mock('@/hooks/use-dataset-permissions', () => ({
  useDatasetPermissions: () => ({ canDeletePipeline: true, canEditPipeline: true }),
  useDatasetPermissionsById: () => ({ canDeletePipeline: true, canEditPipeline: true, isLoading: false }),
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

vi.mock('@/hooks/use-data-sink-removal-state', () => ({
  useDataSinkRemovalState: () => ({ removalState: { dataSinks: [], layers: [], apis: [] }, isLoadingError: false }),
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

vi.mock('../../_services/validationService', async importOriginal => {
  const actual = await importOriginal<typeof import('../../_services/validationService')>()
  return { ...actual, validatePipelineWithNodeStatus: vi.fn() }
})

const makeNode = (id: string) => ({
  id,
  type: PIPELINE_NODE_TYPES.Start,
  position: { x: 0, y: 0 },
  data: { label: 'Start', configured: true, nodeType: PIPELINE_NODE_TYPES.Start, description: '' },
})

const makeSession = (): PipelineSession => ({
  id: 'session-1',
  name: 'Test Pipeline',
  pipeline: { ...createEmptyPipeline('Test Pipeline'), nodes: [makeNode('node-a')] },
  isDirty: false,
  created: new Date(),
  lastModified: new Date(),
})

const savedPipeline = (id: string, nodes: ReturnType<typeof makeNode>[]) => ({
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

const sessionIdOf = (pipelineId: string) =>
  String(sessionManagerRef.current?.sessions.find(session => session.pipeline.id === pipelineId)?.id)

// Wrapped in StrictMode: Next.js dev mode renders "use client" trees in StrictMode (double-
// invoking effects), which a plain RTL render does not do by default.
const sessionManagerRef = { current: null as ReturnType<typeof usePipelineSession> | null }
const contextRef = { current: null as ReturnType<typeof useActivePipeline> | null }

const ContextCapture: React.FC = () => {
  contextRef.current = useActivePipeline()
  return null
}

const Harness: React.FC<{ session?: PipelineSession }> = ({ session = makeSession() }) => {
  const sessionManager = usePipelineSession(session)
  sessionManagerRef.current = sessionManager
  return (
    <React.StrictMode>
      <PipelineEditorProviderComponent sessionManager={sessionManager}>
        <ContextCapture />
        <PipelineToolbar />
        <PipelineCanvas />
        <PipelineInspector />
      </PipelineEditorProviderComponent>
    </React.StrictMode>
  )
}

beforeEach(() => {
  vi.clearAllMocks()

  vi.mocked(useGetPipelines).mockReturnValue({ data: undefined, isLoading: false } as unknown as ReturnType<
    typeof useGetPipelines
  >)
  vi.mocked(useGetDataSinks).mockReturnValue({ data: undefined, isLoading: false } as unknown as ReturnType<
    typeof useGetDataSinks
  >)
  vi.mocked(useGetDataset).mockReturnValue({
    data: { data: { provisioned: false } },
    isLoading: false,
  } as unknown as ReturnType<typeof useGetDataset>)
  vi.mocked(useCreatePipeline).mockReturnValue({
    mutate: vi.fn(),
    mutateAsync: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useCreatePipeline>)
  vi.mocked(useUpdatePipeline).mockReturnValue({
    mutate: vi.fn(),
    mutateAsync: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useUpdatePipeline>)
  vi.mocked(useDeletePipeline).mockReturnValue({
    mutate: vi.fn(),
    mutateAsync: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useDeletePipeline>)
  vi.mocked(useCreateDataSink).mockReturnValue({
    mutate: vi.fn(),
    mutateAsync: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useCreateDataSink>)
  vi.mocked(useDeleteDataSink).mockReturnValue({
    mutate: vi.fn(),
    mutateAsync: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useDeleteDataSink>)
  vi.mocked(useUpdateDataSink).mockReturnValue({
    mutate: vi.fn(),
    mutateAsync: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useUpdateDataSink>)
  vi.mocked(useCreateMapping).mockReturnValue({
    mutate: vi.fn(),
    mutateAsync: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useCreateMapping>)
  vi.mocked(useUpdateMapping).mockReturnValue({
    mutate: vi.fn(),
    mutateAsync: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useUpdateMapping>)
  vi.mocked(useDeleteMapping).mockReturnValue({
    mutate: vi.fn(),
    mutateAsync: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useDeleteMapping>)
  vi.mocked(validatePipelineWithNodeStatus).mockReturnValue({
    isValid: true,
    errors: [],
    warnings: [],
    nodeStatuses: new Map(),
  })
})

describe('Pipeline editor: Validate focus', () => {
  it('shows Validation Results (not the empty state) when nothing was selected', () => {
    render(<Harness />)

    fireEvent.click(screen.getByText('toolbar.validate'))

    expect(screen.queryAllByText('validation.results').length).toBeGreaterThan(0)
    expect(screen.queryByText('inspector.noNodeSelected')).toBeNull()
  })

  it('shows Validation Results after Validate, then the node panel again on re-click', () => {
    const { container } = render(<Harness />)
    const node = container.querySelector('[data-id="node-a"]')
    expect(node).not.toBeNull()

    fireEvent.click(node!)
    expect(screen.queryAllByText('inspector.properties').length).toBeGreaterThan(0)

    fireEvent.click(screen.getByText('toolbar.validate'))
    expect(screen.queryAllByText('validation.results').length).toBeGreaterThan(0)
    expect(screen.queryByText('inspector.properties')).toBeNull()
    expect(screen.queryByText('inspector.noNodeSelected')).toBeNull()

    fireEvent.click(node!)
    expect(screen.queryAllByText('inspector.properties').length).toBeGreaterThan(0)
    expect(screen.queryByText('validation.results')).toBeNull()
    expect(screen.queryByText('inspector.noNodeSelected')).toBeNull()
  })
})

describe('Pipeline editor: validation on save', () => {
  it('shows Validation Results only in the changed pipeline, then the node panel on a click', async () => {
    vi.mocked(useGetPipelines).mockReturnValue({
      data: {
        data: [savedPipeline('pipeline-a', [makeNode('node-a')]), savedPipeline('pipeline-b', [makeNode('node-b')])],
      },
      isLoading: false,
    } as unknown as ReturnType<typeof useGetPipelines>)
    const { container } = render(<Harness />)

    fireEvent.click(container.querySelector('[data-id="node-a"]')!)
    act(() => {
      sessionManagerRef.current?.switchToSession(sessionIdOf('pipeline-b'))
    })
    fireEvent.click(container.querySelector('[data-id="node-b"]')!)
    act(() => {
      sessionManagerRef.current?.markSessionDirty(sessionIdOf('pipeline-b'))
      sessionManagerRef.current?.switchToSession(sessionIdOf('pipeline-a'))
    })
    expect(screen.queryByText('validation.results')).toBeNull()

    await act(async () => {
      await contextRef.current?.saveAllPipelines()
    })
    expect(screen.queryByText('validation.results')).toBeNull()

    act(() => {
      sessionManagerRef.current?.switchToSession(sessionIdOf('pipeline-b'))
    })
    expect(screen.queryAllByText('validation.results').length).toBeGreaterThan(0)

    fireEvent.click(container.querySelector('[data-id="node-b"]')!)
    expect(screen.queryAllByText('inspector.properties').length).toBeGreaterThan(0)
    expect(screen.queryByText('validation.results')).toBeNull()
  })
})

describe('Pipeline editor: validation on load', () => {
  const INVALID_RESULT = {
    isValid: false,
    errors: [
      { id: 'error-1', type: 'structure' as const, messageKey: 'validation.testError', severity: 'error' as const },
    ],
    warnings: [],
    nodeStatuses: new Map(),
  }
  const VALID_RESULT = { isValid: true, errors: [], warnings: [], nodeStatuses: new Map() }

  it('keeps Validation Results in another pipeline after the user selects a node and switches tabs', () => {
    vi.mocked(useGetPipelines).mockReturnValue({
      data: {
        data: [savedPipeline('pipeline-a', [makeNode('node-a')]), savedPipeline('pipeline-b', [makeNode('node-b')])],
      },
      isLoading: false,
    } as unknown as ReturnType<typeof useGetPipelines>)
    vi.mocked(validatePipelineWithNodeStatus).mockReturnValue(VALID_RESULT)
    const { container } = render(<Harness />)

    fireEvent.click(container.querySelector('[data-id="node-a"]')!)
    expect(screen.queryByText('validation.results')).toBeNull()

    act(() => {
      sessionManagerRef.current?.switchToSession(sessionIdOf('pipeline-b'))
    })

    expect(screen.queryAllByText('validation.results').length).toBeGreaterThan(0)
    expect(screen.getByText('validation.pipelineValid')).toBeInTheDocument()
  })

  it.each([
    ['an invalid', INVALID_RESULT, 'validation.testError'],
    ['a valid', VALID_RESULT, 'validation.pipelineValid'],
  ])('shows Validation Results for %s pipeline that was saved with a selected node', (_label, result, content) => {
    vi.mocked(useGetPipelines).mockReturnValue({
      data: {
        data: [
          {
            id: 'backend-pipeline-1',
            name: 'Saved Pipeline',
            description: '',
            styles: {
              nodes: [{ ...makeNode('node-a'), selected: true }],
              edges: [],
              nodePositions: {},
              viewport: { x: 0, y: 0, zoom: 1 },
            },
            dataSources: [],
            apis: [],
            dataSinks: [],
            model: {},
            createdAt: '2024-01-01T00:00:00Z',
            modifiedAt: '2024-01-01T00:00:00Z',
          },
        ],
      },
      isLoading: false,
    } as unknown as ReturnType<typeof useGetPipelines>)
    vi.mocked(validatePipelineWithNodeStatus).mockReturnValue(result)

    render(<Harness />)

    expect(screen.queryAllByText('validation.results').length).toBeGreaterThan(0)
    expect(screen.getByText(content)).toBeInTheDocument()
    expect(screen.queryByText('inspector.properties')).toBeNull()
  })
})
