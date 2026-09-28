import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import { ActivePipelineContext } from '../../../_hooks/use-active-pipeline'
import type { DataSinkLocks } from '../../../_hooks/use-datasink-locks'
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

const renderPanel = (
  pipelineUsingTableName: (nodeId: string, tableName: string) => string | null,
  overrides: Partial<GeoPersistenceNodeData> = {},
  onUpdate: (data: Partial<GeoPersistenceNodeData>) => void = vi.fn(),
  locks: DataSinkLocks = { provisioned: false, inUseByLayer: false },
) => {
  const contextValue = {
    selectedNode,
    pipelineUsingTableName,
    getSinkLocks: () => locks,
  } as unknown as ActivePipelineContextValue

  render(
    <QueryClientProvider client={new QueryClient()}>
      <ActivePipelineContext.Provider value={contextValue}>
        <GeoPersistencePanel data={{ ...nodeData, ...overrides }} onUpdate={onUpdate} />
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

  it('reports a name with disallowed characters', () => {
    renderPanel(() => null, { tableName: 'roads-2024' })

    expect(screen.getByText('geoPersistencePanel.tableNameInvalid')).toBeInTheDocument()
    expect(screen.getByLabelText('geoPersistencePanel.tableName')).toHaveAttribute('aria-invalid', 'true')
  })

  it('leaves the node unconfigured when the name contains disallowed characters', async () => {
    const onUpdate = vi.fn()
    renderPanel(() => null, { tableName: '', dataStructureVersionId: 'structure-1/version-1' }, onUpdate)

    await userEvent.type(screen.getByLabelText('geoPersistencePanel.tableName'), '-')

    expect(onUpdate).toHaveBeenCalledWith({ tableName: '-', configured: false })
  })

  it('reports a name starting with a digit', () => {
    renderPanel(() => null, { tableName: '2roads' })

    expect(screen.getByText('geoPersistencePanel.tableNameInvalid')).toBeInTheDocument()
  })

  it('accepts letters, digits and underscores', () => {
    renderPanel(() => null, { tableName: '_roads_2024' })

    expect(screen.queryByText('geoPersistencePanel.tableNameInvalid')).not.toBeInTheDocument()
    expect(screen.getByLabelText('geoPersistencePanel.tableName')).toHaveAttribute('aria-invalid', 'false')
  })

  it('limits the input to the 63 characters PostgreSQL keeps', () => {
    renderPanel(() => null)

    expect(screen.getByLabelText('geoPersistencePanel.tableName')).toHaveAttribute('maxlength', '63')
  })

  it('offers the data structure import for an unlocked sink', () => {
    renderPanel(() => null)

    expect(screen.getByRole('button', { name: 'geoPersistencePanel.importDataStructure' })).toBeEnabled()
  })

  it('hides the data structure import for a provisioned sink', () => {
    renderPanel(() => null, {}, vi.fn(), { provisioned: true, inUseByLayer: false })

    expect(screen.queryByRole('button', { name: 'geoPersistencePanel.importDataStructure' })).not.toBeInTheDocument()
    expect(screen.queryByText('geoPersistencePanel.dataStructureVersion')).not.toBeInTheDocument()
    expect(screen.getByLabelText('geoPersistencePanel.tableName')).toBeInTheDocument()
  })

  it('disables editing the data structure of a layer-referenced sink', () => {
    renderPanel(() => null, {}, vi.fn(), { provisioned: false, inUseByLayer: true })

    expect(screen.getByRole('button', { name: 'geoPersistencePanel.importDataStructure' })).toBeDisabled()
  })
})
