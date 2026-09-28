import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import { ActivePipelineContext } from '../../../_hooks/use-active-pipeline'
import type { ActivePipelineContextValue } from '../../../_types/context'
import type { GeoPersistenceNodeData } from '../../../_types/nodes'
import type { PipelineNode } from '../../../_types/pipeline'
import { GeoPersistencePanel } from './GeoPersistencePanel'

vi.mock('next/navigation', () => ({
  useParams: () => ({ datasetId: 'dataset-1' }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/',
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

const permissions = vi.hoisted(() => ({ canReadDatastructures: false }))

vi.mock('@/hooks/use-permissions', () => ({
  usePermissions: () => ({
    hasPermission: () => permissions.canReadDatastructures,
    hasScopedPermission: () => false,
  }),
}))

vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: () => ({ getApiRequestParams: () => new URLSearchParams() }),
}))

vi.mock('@/app/services/api/datastructures/clientRequests', () => ({
  useGetDatastructures: () => ({
    data: {
      data: [
        {
          id: 'structure-1',
          name: 'Roads',
          description: '',
          dataStructureStatus: 'AVAILABLE',
          dataStructureVersions: [
            {
              id: 'version-1',
              version: '1.0',
              description: '',
              dataStructureVersionStatus: 'AVAILABLE',
              dataStructureVersionSource: null,
            },
          ],
          createdAt: '2024-01-01',
          modifiedAt: '2024-01-01',
        },
      ],
      totalElements: 1,
    },
    isFetching: false,
  }),
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
) => {
  const contextValue = { selectedNode, pipelineUsingTableName } as unknown as ActivePipelineContextValue

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

  describe('choosing a data structure version', () => {
    beforeEach(() => {
      permissions.canReadDatastructures = true
    })

    afterEach(() => {
      permissions.canReadDatastructures = false
    })

    const chooseVersion = async () => {
      const user = userEvent.setup()
      await user.click(screen.getByRole('button', { name: 'geoPersistencePanel.importDataStructure' }))
      await user.click(within(screen.getByTestId('expanderCell')).getByRole('button'))
      await user.click(screen.getByRole('checkbox', { name: 'Select datastructure Version 1.0' }))
      await user.click(screen.getByTestId('confirmButton'))
    }

    it('configures the node once a data structure version is chosen for a valid table name', async () => {
      const onUpdate = vi.fn()
      renderPanel(() => null, { configured: false }, onUpdate)

      await chooseVersion()

      expect(onUpdate).toHaveBeenCalledWith({ dataStructureVersionId: 'structure-1/version-1', configured: true })
    })

    it('leaves the node unconfigured when a version is chosen for an invalid table name', async () => {
      const onUpdate = vi.fn()
      renderPanel(() => null, { configured: false, tableName: 'roads-2024' }, onUpdate)

      await chooseVersion()

      expect(onUpdate).toHaveBeenCalledWith({ dataStructureVersionId: 'structure-1/version-1', configured: false })
    })
  })
})
