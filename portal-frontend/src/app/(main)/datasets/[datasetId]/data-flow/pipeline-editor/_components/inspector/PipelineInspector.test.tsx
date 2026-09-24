import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import { ActivePipelineContext } from '../../_hooks/use-active-pipeline'
import type { SinkLockReason } from '../../_hooks/use-datasink-locks'
import { ReadOnlyProvider } from '../../_hooks/use-pipeline-read-only'
import type { ActivePipelineContextValue } from '../../_types/context'
import type { GeoPersistenceNodeData } from '../../_types/nodes'
import type { PipelineNode } from '../../_types/pipeline'
import { PipelineInspector } from './PipelineInspector'

vi.mock('next/navigation', () => ({
  useParams: () => ({ datasetId: 'dataset-1' }),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-permissions', () => ({
  usePermissions: () => ({ hasPermission: () => false, hasScopedPermission: () => false }),
}))

vi.mock('@/app/services/api/datasets/clientRequests', () => ({
  useGetDataset: () => ({ data: undefined }),
}))

vi.mock('@/hooks/use-dataset-permissions', () => ({
  useDatasetPermissionsById: () => ({ canReadDatastructures: false }),
}))

const nodeData: GeoPersistenceNodeData = {
  label: 'Geo Persistence',
  configured: true,
  entityType: 'persistence',
  entityId: 'sink-1',
  tableName: 'roads',
}

const selectedNode = { id: 'geo-1', type: 'geoPersistence', position: { x: 0, y: 0 }, data: nodeData } as PipelineNode

const renderInspector = (lockReason: SinkLockReason | null, updateNode = vi.fn(), isReadOnly = false) => {
  const contextValue = {
    selectedNode,
    selectedEdge: null,
    updateNode,
    shouldShowValidationPanel: false,
    validationResult: null,
    getSinkLockReason: () => lockReason,
    pipelineUsingTableName: () => null,
    getSinkLocks: () => ({
      provisioned: lockReason === 'provisioned',
      inUseByLayer: lockReason === 'inUseByLayer',
    }),
  } as unknown as ActivePipelineContextValue

  render(
    <QueryClientProvider client={new QueryClient()}>
      <ReadOnlyProvider isReadOnly={isReadOnly}>
        <ActivePipelineContext.Provider value={contextValue}>
          <PipelineInspector />
        </ActivePipelineContext.Provider>
      </ReadOnlyProvider>
    </QueryClientProvider>,
  )
}

describe('PipelineInspector', () => {
  it('shows no lock hint for an editable sink', () => {
    renderInspector(null)

    expect(screen.queryByText('sink.locked.provisioned.title')).not.toBeInTheDocument()
  })

  it('explains why a provisioned sink is locked', () => {
    renderInspector('provisioned')

    expect(screen.getByText('sink.locked.provisioned.title')).toBeInTheDocument()
    expect(screen.getByText('sink.locked.provisioned.description')).toBeInTheDocument()
  })

  it('does not update a provisioned sink when its panel is edited', async () => {
    const updateNode = vi.fn()
    renderInspector('provisioned', updateNode)

    await userEvent.type(screen.getByLabelText('geoPersistencePanel.tableName'), 'x')

    expect(updateNode).not.toHaveBeenCalled()
  })

  it('labels a layer-referenced sink without a description', () => {
    renderInspector('inUseByLayer')

    expect(screen.getByText('sink.locked.inUseByLayer.title')).toBeInTheDocument()
    expect(screen.queryByText('sink.locked.provisioned.description')).not.toBeInTheDocument()
  })

  it('hides the hint of a provisioned sink in a read-only pipeline', () => {
    renderInspector('provisioned', vi.fn(), true)

    expect(screen.queryByText('sink.locked.provisioned.title')).not.toBeInTheDocument()
  })

  it('keeps the hint of a layer-referenced sink in a read-only pipeline', () => {
    renderInspector('inUseByLayer', vi.fn(), true)

    expect(screen.getByText('sink.locked.inUseByLayer.title')).toBeInTheDocument()
  })

  it('keeps a layer-referenced sink editable', async () => {
    const updateNode = vi.fn()
    renderInspector('inUseByLayer', updateNode)

    await userEvent.type(screen.getByLabelText('geoPersistencePanel.tableName'), 'x')

    expect(updateNode).toHaveBeenCalled()
  })
})
