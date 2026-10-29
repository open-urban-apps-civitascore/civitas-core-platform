import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import { ActivePipelineContext } from '../../_hooks/use-active-pipeline'
import { ReadOnlyProvider } from '../../_hooks/use-pipeline-read-only'
import type { ActivePipelineContextValue } from '../../_types/context'
import type { GeoPersistenceNodeData } from '../../_types/nodes'
import type { PipelineNode } from '../../_types/pipeline'
import { PipelineCanvas } from './PipelineCanvas'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

const nodeData: GeoPersistenceNodeData = {
  label: 'Geo Persistence',
  configured: true,
  entityType: 'persistence',
  entityId: 'sink-1',
  tableName: 'roads',
}

const selectedSinkNode = {
  id: 'geo-1',
  type: 'geoPersistence',
  position: { x: 0, y: 0 },
  selected: true,
  data: nodeData,
} as PipelineNode

const renderCanvas = (isReadOnly = false, dispatch = vi.fn()) => {
  const contextValue = {
    pipeline: { id: 'pipeline-1', name: 'Test', nodes: [selectedSinkNode], edges: [] },
    dispatch,
    addNode: vi.fn(),
    addEdge: vi.fn(),
    validateConnection: () => true,
    hideValidationPanel: vi.fn(),
  } as unknown as ActivePipelineContextValue

  render(
    <ReadOnlyProvider isReadOnly={isReadOnly}>
      <ActivePipelineContext.Provider value={contextValue}>
        <PipelineCanvas />
      </ActivePipelineContext.Provider>
    </ReadOnlyProvider>,
  )

  return dispatch
}

const removedNodeIds = (dispatch: ReturnType<typeof vi.fn>): string[] =>
  dispatch.mock.calls
    .filter(([action]) => action.type === 'NODE_CHANGES')
    .flatMap(([action]) => action.payload)
    .filter((change: { type: string }) => change.type === 'remove')
    .map((change: { id: string }) => change.id)

describe('PipelineCanvas', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('removes a selected Data storage node on Delete', async () => {
    const dispatch = renderCanvas()

    await userEvent.keyboard('{Delete}')

    expect(removedNodeIds(dispatch)).toEqual(['geo-1'])
  })

  it('keeps the node in a read-only pipeline', async () => {
    const dispatch = renderCanvas(true)

    await userEvent.keyboard('{Delete}')

    expect(removedNodeIds(dispatch)).toEqual([])
  })
})
