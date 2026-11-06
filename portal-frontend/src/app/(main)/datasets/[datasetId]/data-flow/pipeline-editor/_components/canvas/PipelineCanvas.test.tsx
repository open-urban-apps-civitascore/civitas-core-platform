import { render, screen } from '@testing-library/react'
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

const selectedSinkNode = (data: GeoPersistenceNodeData = nodeData) =>
  ({
    id: 'geo-1',
    type: 'geoPersistence',
    position: { x: 0, y: 0 },
    selected: true,
    data,
  }) as PipelineNode

const renderCanvas = (isReadOnly = false, node = selectedSinkNode(), dispatch = vi.fn()) => {
  const contextValue = {
    pipeline: { id: 'pipeline-1', name: 'Test', nodes: [node], edges: [] },
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

  it('removes a saved Data storage node after the user confirms', async () => {
    const dispatch = renderCanvas()

    await userEvent.keyboard('{Delete}')
    expect(await screen.findByText('dataSinkNodeRemoval.description')).toBeInTheDocument()
    expect(removedNodeIds(dispatch)).toEqual([])

    await userEvent.click(screen.getByTestId('confirmButton'))

    expect(removedNodeIds(dispatch)).toEqual(['geo-1'])
  })

  it('keeps a saved Data storage node when the user cancels', async () => {
    const dispatch = renderCanvas()

    await userEvent.keyboard('{Delete}')
    await userEvent.click(await screen.findByTestId('discardButton'))

    expect(removedNodeIds(dispatch)).toEqual([])
  })

  it('removes an unsaved Data storage node without asking', async () => {
    const dispatch = renderCanvas(false, selectedSinkNode({ ...nodeData, entityId: undefined }))

    await userEvent.keyboard('{Delete}')

    expect(screen.queryByText('dataSinkNodeRemoval.description')).not.toBeInTheDocument()
    expect(removedNodeIds(dispatch)).toEqual(['geo-1'])
  })

  it('keeps the node in a read-only pipeline', async () => {
    const dispatch = renderCanvas(true)

    await userEvent.keyboard('{Delete}')

    expect(removedNodeIds(dispatch)).toEqual([])
  })
})
