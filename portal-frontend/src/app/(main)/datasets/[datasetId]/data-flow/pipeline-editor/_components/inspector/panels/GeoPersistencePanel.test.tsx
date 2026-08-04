import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'

import { ActivePipelineContext } from '../../../_hooks/use-active-pipeline'
import type { ActivePipelineContextValue } from '../../../_types/context'
import type { GeoPersistenceNodeData } from '../../../_types/nodes'
import type { PipelineNode } from '../../../_types/pipeline'
import { GeoPersistencePanel } from './GeoPersistencePanel'

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

const nodeData: GeoPersistenceNodeData = {
  label: 'Geo Persistence',
  configured: true,
  entityType: 'persistence',
  tableName: 'roads',
}

const selectedNode = { id: 'geo-1', type: 'geoPersistence', position: { x: 0, y: 0 }, data: nodeData } as PipelineNode

const renderPanel = (pipelineUsingTableName: (nodeId: string, tableName: string) => string | null) => {
  const contextValue = { selectedNode, pipelineUsingTableName } as unknown as ActivePipelineContextValue

  render(
    <QueryClientProvider client={new QueryClient()}>
      <ActivePipelineContext.Provider value={contextValue}>
        <GeoPersistencePanel data={nodeData} onUpdate={vi.fn()} />
      </ActivePipelineContext.Provider>
    </QueryClientProvider>,
  )
}

describe('GeoPersistencePanel', () => {
  it('reports the pipeline that already uses the table name', () => {
    const pipelineUsingTableName = vi.fn().mockReturnValue('Water')

    renderPanel(pipelineUsingTableName)

    expect(pipelineUsingTableName).toHaveBeenCalledWith('geo-1', 'roads')
    expect(screen.getByText('validation.messages.duplicateTableName')).toBeInTheDocument()
    expect(screen.getByLabelText('geoPersistencePanel.tableName')).toHaveAttribute('aria-invalid', 'true')
  })

  it('shows no conflict for an unused table name', () => {
    renderPanel(() => null)

    expect(screen.queryByText('validation.messages.duplicateTableName')).not.toBeInTheDocument()
    expect(screen.getByLabelText('geoPersistencePanel.tableName')).toHaveAttribute('aria-invalid', 'false')
  })
})
