import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { toast } from 'sonner'

import { ActivePipelineContext } from '../../_hooks/use-active-pipeline'
import type { SinkLockReason } from '../../_hooks/use-datasink-locks'
import { ReadOnlyProvider } from '../../_hooks/use-pipeline-read-only'
import type { ActivePipelineContextValue } from '../../_types/context'
import type { GeoPersistenceNodeData } from '../../_types/nodes'
import type { PipelineNode } from '../../_types/pipeline'
import { PipelineCanvas } from './PipelineCanvas'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('sonner', () => ({
  toast: { error: vi.fn() },
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

const renderCanvas = (lockReason: SinkLockReason | null, dispatch = vi.fn()) => {
  const contextValue = {
    pipeline: { id: 'pipeline-1', name: 'Test', nodes: [selectedSinkNode], edges: [] },
    dispatch,
    addNode: vi.fn(),
    addEdge: vi.fn(),
    validateConnection: () => true,
    hideValidationPanel: vi.fn(),
    getSinkLockReason: () => lockReason,
    getSelectionLockReason: () => lockReason,
  } as unknown as ActivePipelineContextValue

  render(
    <ReadOnlyProvider isReadOnly={false}>
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

  describe('locked data sink deletion', () => {
    it('keeps a layer-referenced sink node', async () => {
      const dispatch = renderCanvas('inUseByLayer')

      await userEvent.keyboard('{Delete}')

      expect(removedNodeIds(dispatch)).toEqual([])
    })

    it('shows a toast that says why a layer-referenced sink node can not be deleted', async () => {
      renderCanvas('inUseByLayer')

      await userEvent.keyboard('{Delete}')

      expect(toast.error).toHaveBeenCalledWith('sink.notRemovable.inUseByLayer')
    })

    it('keeps a provisioned sink node', async () => {
      const dispatch = renderCanvas('provisioned')

      await userEvent.keyboard('{Delete}')

      expect(removedNodeIds(dispatch)).toEqual([])
    })

    it('shows a toast that says why a provisioned sink node can not be deleted', async () => {
      renderCanvas('provisioned')

      await userEvent.keyboard('{Delete}')

      expect(toast.error).toHaveBeenCalledWith('sink.notRemovable.provisioned')
    })
  })

  it('removes an unlocked node without a toast', async () => {
    const dispatch = renderCanvas(null)

    await userEvent.keyboard('{Delete}')

    expect(toast.error).not.toHaveBeenCalled()
    expect(removedNodeIds(dispatch)).toEqual(['geo-1'])
  })
})
