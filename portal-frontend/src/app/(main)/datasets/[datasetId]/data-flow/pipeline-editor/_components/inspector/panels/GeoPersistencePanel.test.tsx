import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import { buildDataStructureUrn } from '@/utils/urn'

import { ActivePipelineContext } from '../../../_hooks/use-active-pipeline'
import type { DataSinkLocks } from '../../../_hooks/use-datasink-locks'
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

const mockHasPermission = vi.fn().mockReturnValue(false)
vi.mock('@/hooks/use-permissions', () => ({
  usePermissions: () => ({
    hasPermission: (...args: unknown[]) => mockHasPermission(...args),
    hasScopedPermission: () => false,
  }),
}))

vi.mock('@/app/services/api/datasets/clientRequests', () => ({
  useGetDataset: () => ({ data: undefined }),
}))

const mockUseGetDatastructures = vi.fn().mockReturnValue({ data: undefined, isFetching: false })
vi.mock('@/app/services/api/datastructures/clientRequests', () => ({
  useGetDatastructures: (...args: unknown[]) => mockUseGetDatastructures(...args),
}))

vi.mock('@/app/services/api/datastructures/versions/clientRequests', () => ({
  useGetDatastructureVersion: () => ({ data: undefined }),
}))

vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: () => ({ getApiRequestParams: () => new URLSearchParams() }),
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

describe('GeoPersistencePanel data structure selection', () => {
  const DATASTRUCTURE_ID = '6f1c2b8e-3a4d-4e5f-9a0b-1c2d3e4f5a6b'
  const FIRST_VERSION_ID = '0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d'
  const SECOND_VERSION_ID = '1b2c3d4e-5f6a-4b7c-9d8e-0f1a2b3c4d5e'

  const datastructure = {
    id: DATASTRUCTURE_ID,
    name: 'Test Structure',
    description: 'Test description',
    dataStructureStatus: 'AVAILABLE' as const,
    dataStructureVersions: [
      {
        id: FIRST_VERSION_ID,
        version: '1.0.0',
        description: 'First version',
        dataStructureVersionStatus: 'AVAILABLE' as const,
        dataStructureVersionSource: null,
      },
      {
        id: SECOND_VERSION_ID,
        version: '2.0.0',
        description: 'Second version',
        dataStructureVersionStatus: 'AVAILABLE' as const,
        dataStructureVersionSource: null,
      },
    ],
    createdAt: '2024-01-01',
    modifiedAt: '2024-01-01',
  }

  beforeEach(() => {
    mockHasPermission.mockReturnValue(true)
    mockUseGetDatastructures.mockReturnValue({ data: { data: [datastructure], totalElements: 1 }, isFetching: false })
  })

  afterEach(() => {
    mockHasPermission.mockReturnValue(false)
  })

  const selectVersion = async (openButtonLabel: string, versionLabel: string) => {
    await userEvent.click(screen.getByRole('button', { name: openButtonLabel }))
    await userEvent.click(within(screen.getByTestId('expanderCell')).getByRole('button'))
    await userEvent.click(screen.getByRole('checkbox', { name: `Select datastructure ${versionLabel}` }))
    await userEvent.click(screen.getByTestId('confirmButton'))
  }

  it('stores the data structure URN of the selected version on the node', async () => {
    const onUpdate = vi.fn()
    renderPanel(() => null, {}, onUpdate)

    await selectVersion('geoPersistencePanel.importDataStructure', 'Version 1.0.0')

    expect(onUpdate).toHaveBeenCalledWith({
      dataStructureVersionId: `${DATASTRUCTURE_ID}/${FIRST_VERSION_ID}`,
      dataStructureUrn: buildDataStructureUrn('Test Structure', DATASTRUCTURE_ID, '1.0.0'),
      configured: true,
    })
  })

  it('replaces the data structure URN when another version is selected', async () => {
    const onUpdate = vi.fn()
    renderPanel(
      () => null,
      {
        dataStructureVersionId: `${DATASTRUCTURE_ID}/${FIRST_VERSION_ID}`,
        dataStructureUrn: buildDataStructureUrn('Test Structure', DATASTRUCTURE_ID, '1.0.0'),
      },
      onUpdate,
    )

    await selectVersion('geoPersistencePanel.changeDataStructure', 'Version 2.0.0')

    expect(onUpdate).toHaveBeenCalledWith({
      dataStructureVersionId: `${DATASTRUCTURE_ID}/${SECOND_VERSION_ID}`,
      dataStructureUrn: buildDataStructureUrn('Test Structure', DATASTRUCTURE_ID, '2.0.0'),
      configured: true,
    })
  })

  it('configures the node once a data structure version is chosen for a valid table name', async () => {
    const onUpdate = vi.fn()
    renderPanel(() => null, { configured: false }, onUpdate)

    await selectVersion('geoPersistencePanel.importDataStructure', 'Version 1.0.0')

    expect(onUpdate).toHaveBeenCalledWith({
      dataStructureVersionId: `${DATASTRUCTURE_ID}/${FIRST_VERSION_ID}`,
      dataStructureUrn: buildDataStructureUrn('Test Structure', DATASTRUCTURE_ID, '1.0.0'),
      configured: true,
    })
  })

  it('leaves the node unconfigured when a version is chosen for an invalid table name', async () => {
    const onUpdate = vi.fn()
    renderPanel(() => null, { configured: false, tableName: 'roads-2024' }, onUpdate)

    await selectVersion('geoPersistencePanel.importDataStructure', 'Version 1.0.0')

    expect(onUpdate).toHaveBeenCalledWith({
      dataStructureVersionId: `${DATASTRUCTURE_ID}/${FIRST_VERSION_ID}`,
      dataStructureUrn: buildDataStructureUrn('Test Structure', DATASTRUCTURE_ID, '1.0.0'),
      configured: false,
    })
  })
})
