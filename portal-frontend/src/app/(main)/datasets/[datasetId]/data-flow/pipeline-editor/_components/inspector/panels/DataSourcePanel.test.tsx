import { render, screen, within } from '@testing-library/react'
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
  usePermissions: () => ({ hasPermission: () => true }),
}))

vi.mock('@/app/services/api/datasets/clientRequests', () => ({
  useGetDataset: () => ({ data: undefined, isLoading: false, isError: false }),
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

const datasource = (id: string, name: string, connector: Record<string, unknown>): Datasource =>
  ({
    id,
    name,
    description: null,
    dataSourceStatus: 'AVAILABLE',
    connector,
    configurationUrn: `urn:test:${id}`,
  }) as unknown as Datasource

const apiCall = (name: string, method: string | null = 'GET') => ({ name, method, path: '/items' })

const HTTP_ONE_CALL = datasource('http-1', 'Test HTTP Source 1', {
  connectorType: 'HTTP',
  apiCalls: [apiCall('Test Call 1')],
})
const HTTP_TWO_CALLS = datasource('http-2', 'Test HTTP Source 2', {
  connectorType: 'HTTP',
  apiCalls: [apiCall('Test Call 1'), apiCall('Test Call 2')],
})
const SQL_SOURCE = datasource('sql-1', 'Test SQL Source', { connectorType: 'SQL' })

const nodeData: DataSourceNodeData = {
  label: 'Data Source',
  configured: false,
  entityType: 'datasource',
}

const renderPanel = (overrides: Partial<DataSourceNodeData> = {}, onUpdate = vi.fn()) => {
  render(<DataSourcePanel data={{ ...nodeData, ...overrides }} onUpdate={onUpdate} />)
  return onUpdate
}

const selectedNode = (entity: Datasource): Partial<DataSourceNodeData> => ({
  entityId: entity.id,
  entityName: entity.name,
  configured: true,
  entityMetadata: { connector: String(entity.connector?.connectorType) },
})

const chooseOption = async (comboboxIndex: number, optionName: string | RegExp) => {
  await userEvent.click(screen.getAllByRole('combobox')[comboboxIndex])
  await userEvent.click(screen.getByRole('option', { name: optionName }))
}

beforeEach(() => {
  mockUseGetDatasources.mockReturnValue({
    data: { data: [HTTP_ONE_CALL, HTTP_TWO_CALLS, SQL_SOURCE] },
    isLoading: false,
    isError: false,
    error: null,
  })
})

describe('DataSourcePanel', () => {
  it('sets the only API call when an HTTP data source with one API call is selected', async () => {
    const onUpdate = renderPanel()

    await chooseOption(0, 'Test HTTP Source 1')

    expect(onUpdate).toHaveBeenCalledWith(expect.objectContaining({ entityId: 'http-1', apiCallName: 'Test Call 1' }))
  })

  it('sets no API call when an HTTP data source with two API calls is selected', async () => {
    const onUpdate = renderPanel()

    await chooseOption(0, 'Test HTTP Source 2')

    expect(onUpdate).toHaveBeenCalledWith(expect.objectContaining({ entityId: 'http-2', apiCallName: undefined }))
  })

  it('sets the API call chosen in the API call select', async () => {
    const onUpdate = renderPanel(selectedNode(HTTP_TWO_CALLS))

    await chooseOption(1, /Test Call 2/)

    expect(onUpdate).toHaveBeenCalledWith({ apiCallName: 'Test Call 2' })
  })

  it('shows the method before each API call name', async () => {
    renderPanel(selectedNode(HTTP_TWO_CALLS))

    await userEvent.click(screen.getAllByRole('combobox')[1])

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

    await userEvent.click(screen.getAllByRole('combobox')[1])

    expect(screen.getByRole('option', { name: 'Test Call 1' })).toBeInTheDocument()
  })

  it('shows no API call select for a SQL data source', () => {
    renderPanel(selectedNode(SQL_SOURCE))

    expect(screen.getAllByRole('combobox')).toHaveLength(1)
    expect(screen.queryByText('dataSourcePanel.apiCall')).not.toBeInTheDocument()
  })

  it('clears the API call when the data source is cleared', async () => {
    const onUpdate = renderPanel({ ...selectedNode(HTTP_ONE_CALL), apiCallName: 'Test Call 1' })

    await chooseOption(0, 'entitySelector.clearSelection')

    expect(onUpdate).toHaveBeenCalledWith(expect.objectContaining({ entityId: undefined, apiCallName: undefined }))
  })

  it('shows the stored API call name when the data source no longer has it', () => {
    renderPanel({ ...selectedNode(HTTP_ONE_CALL), apiCallName: 'Test Old Call' })

    expect(screen.getAllByRole('combobox')[1]).toHaveTextContent('Test Old Call')
    expect(screen.getAllByRole('combobox')[1]).not.toHaveTextContent('GET')
  })

  it('marks the API call select as invalid when the data source no longer has the stored name', () => {
    renderPanel({ ...selectedNode(HTTP_ONE_CALL), apiCallName: 'Test Old Call' })

    expect(screen.getAllByRole('combobox')[1]).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByText('dataSourcePanel.apiCallNotFound')).toBeInTheDocument()
  })

  it('shows the stored API call name in grey when the data source no longer has it', async () => {
    renderPanel({ ...selectedNode(HTTP_ONE_CALL), apiCallName: 'Test Old Call' })

    await userEvent.click(screen.getAllByRole('combobox')[1])

    const option = screen.getByRole('option', { name: 'Test Old Call' })
    expect(within(option).getByText('Test Old Call')).toHaveClass('text-muted-foreground')
    const currentOption = screen.getByRole('option', { name: 'GET Test Call 1' })
    expect(within(currentOption).getByText(/Test Call 1/)).not.toHaveClass('text-muted-foreground')
  })

  it('does not mark an API call that the data source has', () => {
    renderPanel({ ...selectedNode(HTTP_ONE_CALL), apiCallName: 'Test Call 1' })

    expect(screen.getAllByRole('combobox')[1]).toHaveAttribute('aria-invalid', 'false')
    expect(screen.queryByText('dataSourcePanel.apiCallNotFound')).not.toBeInTheDocument()
  })

  it('does not mark a missing API call', () => {
    renderPanel(selectedNode(HTTP_TWO_CALLS))

    expect(screen.getAllByRole('combobox')[1]).toHaveAttribute('aria-invalid', 'false')
    expect(screen.queryByText('dataSourcePanel.apiCallNotFound')).not.toBeInTheDocument()
  })

  it('shows a placeholder when the HTTP data source has no API calls', () => {
    const httpNoCalls = datasource('http-3', 'Test HTTP Source 3', { connectorType: 'HTTP', apiCalls: [] })
    mockUseGetDatasources.mockReturnValue({ data: { data: [httpNoCalls] }, isLoading: false, isError: false })

    renderPanel(selectedNode(httpNoCalls))

    expect(screen.getAllByRole('combobox')[1]).toHaveTextContent('dataSourcePanel.noApiCalls')
  })
})
