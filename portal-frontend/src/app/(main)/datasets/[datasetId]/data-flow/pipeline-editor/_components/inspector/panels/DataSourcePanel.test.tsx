import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import type { Datasource } from '@/types/datasources'

import type { DataSourceNodeData } from '../../../_types/nodes'
import { DataSourcePanel } from './DataSourcePanel'

vi.mock('next/navigation', () => ({
  useParams: () => ({ datasetId: 'dataset-1' }),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-permissions', () => ({
  usePermissions: () => ({ hasPermission: () => true, hasScopedPermission: () => false }),
}))

vi.mock('@/app/services/api/datasets/clientRequests', () => ({
  useGetDataset: () => ({ data: undefined, isLoading: false, isError: false }),
}))

vi.mock('@/app/services/api/datastructures/versions/clientRequests', () => ({
  useGetDatastructureVersion: () => ({
    data: { data: { dataStructure: { name: 'Air Quality' }, version: '1.2.0', dataStructureVersionStatus: 'DRAFT' } },
  }),
}))

const mockUseGetDatasources = vi.fn()
vi.mock('@/app/services/api/datasources/clientRequests', () => ({
  useGetDatasources: (...args: unknown[]) => mockUseGetDatasources(...args),
}))

// The select is a Radix listbox, and jsdom implements neither pointer capture nor scrollIntoView.
beforeAll(() => {
  Element.prototype.hasPointerCapture = vi.fn(() => false)
  Element.prototype.setPointerCapture = vi.fn()
  Element.prototype.releasePointerCapture = vi.fn()
  Element.prototype.scrollIntoView = vi.fn()
})

const DATASTRUCTURE_ID = 'structure-1'
const VERSION_ID = 'version-1'

const datasource = (
  id: string,
  name: string,
  connector: Record<string, unknown>,
  dataStructureVersion: Record<string, unknown> | null = null,
  description: string | null = null,
): Datasource =>
  ({
    id,
    name,
    description,
    dataSourceStatus: 'AVAILABLE',
    connector,
    configurationUrn: `urn:test:${id}`,
    dataStructureVersion,
  }) as unknown as Datasource

const detailsValue = (label: string) =>
  within(screen.getByText(label).parentElement as HTMLElement).getByTestId('statusBadge')

const apiCall = (name: string, method: string | null = 'GET') => ({ name, method, path: '/items' })

const HTTP_ONE_CALL = datasource('http-1', 'Test HTTP Source 1', {
  connectorType: 'HTTP',
  apiCalls: [apiCall('Test Call 1')],
})
const HTTP_TWO_CALLS = datasource('http-2', 'Test HTTP Source 2', {
  connectorType: 'HTTP',
  apiCalls: [apiCall('Test Call 1'), apiCall('Test Call 2')],
})
const SQL_SOURCE = datasource('sql-1', 'Test SQL Source', { connectorType: 'SQL' }, null, 'Weather station')
const MQTT_SOURCE = datasource(
  'mqtt-1',
  'Test MQTT Source',
  { connectorType: 'MQTT' },
  { id: VERSION_ID, version: '1.2.0', dataStructureVersionStatus: 'DRAFT', dataStructureId: DATASTRUCTURE_ID },
)

const nodeData: DataSourceNodeData = {
  label: 'Data source',
  configured: false,
  entityType: 'datasource',
}

const renderPanel = (overrides: Partial<DataSourceNodeData> = {}, onUpdate = vi.fn(), isLocked = false) => {
  render(<DataSourcePanel data={{ ...nodeData, ...overrides }} onUpdate={onUpdate} isLocked={isLocked} />)
  return onUpdate
}

const selectedNode = (entity: Datasource): Partial<DataSourceNodeData> => ({
  entityId: entity.id,
  entityName: entity.name,
  configured: true,
  entityMetadata: { connector: String(entity.connector?.connectorType) },
})

const toggleInDialog = async (datasourceName: string) => {
  await userEvent.click(screen.getByLabelText('dataSourcePanel.label'))
  const dialog = await screen.findByRole('dialog')
  await userEvent.click(within(dialog).getByRole('checkbox', { name: datasourceName }))
  await userEvent.click(within(dialog).getByRole('button', { name: 'confirm' }))
}

const chooseApiCall = async (optionName: string | RegExp) => {
  await userEvent.click(screen.getByRole('combobox'))
  await userEvent.click(screen.getByRole('option', { name: optionName }))
}

beforeEach(() => {
  mockUseGetDatasources.mockReturnValue({
    data: { data: [HTTP_ONE_CALL, HTTP_TWO_CALLS, SQL_SOURCE, MQTT_SOURCE] },
    isLoading: false,
    isError: false,
    error: null,
  })
})

describe('DataSourcePanel', () => {
  it('sets the only API call when an HTTP data source with one API call is selected', async () => {
    const onUpdate = renderPanel()

    await toggleInDialog('Test HTTP Source 1')

    expect(onUpdate).toHaveBeenCalledWith(expect.objectContaining({ entityId: 'http-1', apiCallName: 'Test Call 1' }))
  })

  it('sets no API call when an HTTP data source with two API calls is selected', async () => {
    const onUpdate = renderPanel()

    await toggleInDialog('Test HTTP Source 2')

    expect(onUpdate).toHaveBeenCalledWith(expect.objectContaining({ entityId: 'http-2', apiCallName: undefined }))
  })

  it('sets the API call chosen in the API call select', async () => {
    const onUpdate = renderPanel(selectedNode(HTTP_TWO_CALLS))

    await chooseApiCall(/Test Call 2/)

    expect(onUpdate).toHaveBeenCalledWith({ apiCallName: 'Test Call 2' })
  })

  it('shows the method before each API call name', async () => {
    renderPanel(selectedNode(HTTP_TWO_CALLS))

    await userEvent.click(screen.getByRole('combobox'))

    expect(screen.getByRole('option', { name: 'GET Test Call 1' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'GET Test Call 2' })).toBeInTheDocument()
  })

  it('shows no method when the API call has none', async () => {
    const httpNoMethod = datasource('http-4', 'Test HTTP Source 4', {
      connectorType: 'HTTP',
      apiCalls: [apiCall('Test Call 1', null)],
    })
    mockUseGetDatasources.mockReturnValue({ data: { data: [httpNoMethod] }, isLoading: false, isError: false })
    renderPanel(selectedNode(httpNoMethod))

    await userEvent.click(screen.getByRole('combobox'))

    expect(screen.getByRole('option', { name: 'Test Call 1' })).toBeInTheDocument()
  })

  it('shows no API call select for a SQL data source', () => {
    renderPanel(selectedNode(SQL_SOURCE))

    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
    expect(screen.queryByText('dataSourcePanel.apiCall')).not.toBeInTheDocument()
  })

  it('clears the API call when the data source is cleared', async () => {
    const onUpdate = renderPanel({ ...selectedNode(HTTP_ONE_CALL), apiCallName: 'Test Call 1' })

    await toggleInDialog('Test HTTP Source 1')

    expect(onUpdate).toHaveBeenCalledWith(expect.objectContaining({ entityId: undefined, apiCallName: undefined }))
  })

  it('shows the stored API call name when the data source no longer has it', () => {
    renderPanel({ ...selectedNode(HTTP_ONE_CALL), apiCallName: 'Test Old Call' })

    expect(screen.getByRole('combobox')).toHaveTextContent('Test Old Call')
    expect(screen.getByRole('combobox')).not.toHaveTextContent('GET')
  })

  it('marks the API call select as invalid when the data source no longer has the stored name', () => {
    renderPanel({ ...selectedNode(HTTP_ONE_CALL), apiCallName: 'Test Old Call' })

    expect(screen.getByRole('combobox')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByText('dataSourcePanel.apiCallNotFound')).toBeInTheDocument()
  })

  it('shows the stored API call name in grey when the data source no longer has it', async () => {
    renderPanel({ ...selectedNode(HTTP_ONE_CALL), apiCallName: 'Test Old Call' })

    await userEvent.click(screen.getByRole('combobox'))

    const option = screen.getByRole('option', { name: 'Test Old Call' })
    expect(within(option).getByText('Test Old Call')).toHaveClass('text-muted-foreground')
    const currentOption = screen.getByRole('option', { name: 'GET Test Call 1' })
    expect(within(currentOption).getByText(/Test Call 1/)).not.toHaveClass('text-muted-foreground')
  })

  it('does not mark an API call that the data source has', () => {
    renderPanel({ ...selectedNode(HTTP_ONE_CALL), apiCallName: 'Test Call 1' })

    expect(screen.getByRole('combobox')).toHaveAttribute('aria-invalid', 'false')
    expect(screen.queryByText('dataSourcePanel.apiCallNotFound')).not.toBeInTheDocument()
  })

  it('does not mark a missing API call', () => {
    renderPanel(selectedNode(HTTP_TWO_CALLS))

    expect(screen.getByRole('combobox')).toHaveAttribute('aria-invalid', 'false')
    expect(screen.queryByText('dataSourcePanel.apiCallNotFound')).not.toBeInTheDocument()
  })

  it('shows a placeholder when the HTTP data source has no API calls', () => {
    const httpNoCalls = datasource('http-3', 'Test HTTP Source 3', { connectorType: 'HTTP', apiCalls: [] })
    mockUseGetDatasources.mockReturnValue({ data: { data: [httpNoCalls] }, isLoading: false, isError: false })

    renderPanel(selectedNode(httpNoCalls))

    expect(screen.getByRole('combobox')).toHaveTextContent('dataSourcePanel.noApiCalls')
  })

  it('shows the Data structure, its version and status in the details', () => {
    renderPanel(selectedNode(MQTT_SOURCE))

    expect(screen.getByText('Air Quality')).toBeInTheDocument()
    expect(screen.getByText('1.2.0')).toBeInTheDocument()
    expect(detailsValue('dataSourcePanel.status')).toHaveTextContent('status.AVAILABLE')
    expect(detailsValue('dataSourcePanel.dataStructureVersionStatus')).toHaveTextContent('status.DRAFT')
  })

  it('opens the Data structure version used by the Data source', async () => {
    const open = vi.spyOn(window, 'open').mockImplementation(() => null)
    renderPanel(selectedNode(MQTT_SOURCE))

    await userEvent.click(screen.getByRole('button', { name: 'dataSourcePanel.showDataStructure' }))

    expect(open).toHaveBeenCalledWith(`/datastructures/${DATASTRUCTURE_ID}/${VERSION_ID}`, '_blank')
    open.mockRestore()
  })

  it('opens the selected Data source', async () => {
    const open = vi.spyOn(window, 'open').mockImplementation(() => null)
    renderPanel(selectedNode(MQTT_SOURCE))

    await userEvent.click(screen.getByRole('button', { name: 'dataSourcePanel.showDataSource' }))

    expect(open).toHaveBeenCalledWith('/datasources/mqtt-1', '_blank')
    open.mockRestore()
  })

  it('disables the Data source selection but keeps both Show buttons in a locked panel', () => {
    renderPanel(selectedNode(MQTT_SOURCE), vi.fn(), true)

    expect(screen.getByLabelText('dataSourcePanel.label')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'dataSourcePanel.showDataSource' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'dataSourcePanel.showDataStructure' })).toBeEnabled()
  })
})

describe('Data source selection dialog', () => {
  it('shows the selected Data source and opens the dialog with all Data sources', async () => {
    renderPanel(selectedNode(MQTT_SOURCE))

    const selection = screen.getByLabelText('dataSourcePanel.label')
    expect(selection).toHaveTextContent('Test MQTT Source')

    await userEvent.click(selection)

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('Test SQL Source')).toBeInTheDocument()
    expect(within(dialog).getByRole('checkbox', { name: 'Test MQTT Source' })).toBeChecked()
  })

  it('selects another Data source', async () => {
    const onUpdate = renderPanel(selectedNode(MQTT_SOURCE))

    await toggleInDialog('Test SQL Source')

    expect(onUpdate).toHaveBeenCalledWith({
      entityId: 'sql-1',
      entityName: 'Test SQL Source',
      configurationUrn: 'urn:test:sql-1',
      apiCallName: undefined,
      configured: true,
      entityMetadata: { connector: 'SQL' },
    })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('clears the selection when the selected Data source is unchecked', async () => {
    const onUpdate = renderPanel(selectedNode(MQTT_SOURCE))

    await toggleInDialog('Test MQTT Source')

    expect(onUpdate).toHaveBeenCalledWith(expect.objectContaining({ entityId: undefined, configured: false }))
  })

  it('filters the Data sources by the search text', async () => {
    renderPanel(selectedNode(MQTT_SOURCE))

    await userEvent.click(screen.getByLabelText('dataSourcePanel.label'))
    const dialog = await screen.findByRole('dialog')
    await userEvent.type(within(dialog).getByRole('searchbox'), 'weather')

    await waitFor(() => expect(within(dialog).queryByText('Test MQTT Source')).not.toBeInTheDocument())
    expect(within(dialog).getByText('Test SQL Source')).toBeInTheDocument()
  })
})
