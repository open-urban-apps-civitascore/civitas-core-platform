import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useForm, UseFormReturn } from 'react-hook-form'
import { describe, expect, it, vi } from 'vitest'

import { mockApiError } from '@/__mocks__/errors/apiError.mock'
import { apiRequest } from '@/app/services/api/request/apiRequest'
import { Form } from '@/components/ui/form'
import { ConnectionTestResult, MASKED_SECRET } from '@/types/connectionTest'
import { ConnectorFormToApiSchema, ConnectorType, EMPTY_PAGINATION } from '@/types/connectors'
import { DatasourceFormDraft } from '@/types/datasources'

import { ConnectionTest } from './ConnectionTest'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string, values?: Record<string, unknown>) =>
    values ? `${key}:${Object.values(values).join(',')}` : key,
}))

vi.mock('@/app/services/api/request/apiRequest', () => ({
  apiRequest: vi.fn(),
}))

const mockApiRequest = vi.mocked(apiRequest)

const DATASOURCE_ID = 'ds-1'

const SQL_CONFIGURATION = {
  driver: 'postgres',
  dsn: 'postgres://db.example.org:5432/city',
  table: 'measurements',
  columns: 'id,value',
  where: '',
  user: 'test.user1',
  password: 'secret',
}

const MQTT_CONFIGURATION = {
  urls: 'mqtt://broker.example.org:1883',
  topics: 'sensors',
  qos: '1',
  protocol_version: '3',
  connect_timeout: '',
  keepalive: '',
  tls: false,
  user: '',
  password: '',
}

const HTTP_CONFIGURATION = {
  baseUrl: 'https://api.example.org',
  authentication: { type: 'NONE', username: '', password: '' },
  apiCalls: [
    { name: 'Get all stations', path: '/stations', queryParameters: [], headers: [], pagination: EMPTY_PAGINATION },
  ],
}

const CONFIGURATIONS: Record<ConnectorType, Record<string, unknown>> = {
  SQL: SQL_CONFIGURATION,
  MQTT: MQTT_CONFIGURATION,
  HTTP: HTTP_CONFIGURATION,
}

const testResult = (overrides: Partial<ConnectionTestResult> = {}): ConnectionTestResult => ({
  success: true,
  reason: null,
  message: null,
  preview: null,
  previewTruncated: false,
  rowCount: null,
  httpStatus: null,
  ...overrides,
})

interface RenderOptions {
  connectorType?: ConnectorType
  configuration?: Record<string, unknown>
  isConnectorComplete?: boolean
  isConnectorChanged?: boolean
}

const renderConnectionTest = (options: RenderOptions = {}) => {
  const { connectorType = 'SQL', isConnectorComplete = true, isConnectorChanged = false } = options
  const configuration = options.configuration ?? CONFIGURATIONS[connectorType]
  let capturedForm: UseFormReturn<DatasourceFormDraft>

  const Wrapper = () => {
    const form = useForm<DatasourceFormDraft>({
      defaultValues: {
        id: DATASOURCE_ID,
        name: 'Test data source',
        description: 'Test description',
        dataSourceStatus: 'DRAFT',
        dataStructureVersionId: null,
        connectorType,
        configuration,
      },
    })
    capturedForm = form

    return (
      <Form {...form}>
        <span data-testid="isDirty">{String(form.formState.isDirty)}</span>
        <ConnectionTest
          form={form}
          datasourceId={DATASOURCE_ID}
          isConnectorComplete={isConnectorComplete}
          isConnectorChanged={isConnectorChanged}
        />
      </Form>
    )
  }

  const view = render(
    <QueryClientProvider client={new QueryClient()}>
      <Wrapper />
    </QueryClientProvider>,
  )
  return { ...view, form: capturedForm! }
}

const clickTest = (name = 'button') => fireEvent.click(screen.getByRole('button', { name }))

describe('ConnectionTest', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('disables the button and shows a hint while the connector is incomplete', () => {
    renderConnectionTest({ isConnectorComplete: false })

    expect(screen.getByText('title')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'button' })).toBeDisabled()
    expect(screen.getByText('incompleteHint')).toBeInTheDocument()
  })

  it('enables the button without a hint when the connector is complete', () => {
    renderConnectionTest()

    expect(screen.getByRole('button', { name: 'button' })).toBeEnabled()
    expect(screen.queryByText('incompleteHint')).not.toBeInTheDocument()
  })

  it('disables the button and shows a hint when the values give no valid request', () => {
    const apiCalls = [{ ...HTTP_CONFIGURATION.apiCalls[0], path: 'stations' }]
    renderConnectionTest({ connectorType: 'HTTP', configuration: { ...HTTP_CONFIGURATION, apiCalls } })

    expect(screen.getByRole('button', { name: 'button' })).toBeDisabled()
    expect(screen.getByText('incompleteHint')).toBeInTheDocument()
  })

  it('shows a spinner and disables the button while the test runs', async () => {
    mockApiRequest.mockReturnValue(new Promise(() => {}))
    renderConnectionTest()

    clickTest()

    expect(await screen.findByText('connecting')).toBeInTheDocument()
    expect(screen.getByRole('status')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'button' })).toBeDisabled()
  })

  describe('successful test', () => {
    it('shows the SQL preview as CSV and offers to test again', async () => {
      const preview = 'id,value\n1,20.5\n2,21.0'
      mockApiRequest.mockResolvedValue({ data: testResult({ preview, rowCount: 2 }) })
      const { container } = renderConnectionTest()

      clickTest()

      expect(await screen.findByText('success')).toBeInTheDocument()
      expect(container.querySelector('pre')?.textContent).toBe(preview)
      expect(screen.getByRole('button', { name: 'testAgain' })).toBeEnabled()
    })

    it('shows the pretty-printed body without the HTTP status 200', async () => {
      mockApiRequest.mockResolvedValue({ data: testResult({ preview: '{"id":1}', httpStatus: 200 }) })
      const { container } = renderConnectionTest({ connectorType: 'HTTP' })

      clickTest()

      expect(await screen.findByText('success')).toBeInTheDocument()
      expect(container.querySelector('pre')?.textContent).toBe('{\n  "id": 1\n}')
      expect(screen.queryByText(/httpStatus/)).not.toBeInTheDocument()
    })

    it('shows an HTTP status other than 200', async () => {
      mockApiRequest.mockResolvedValue({ data: testResult({ httpStatus: 204 }) })
      renderConnectionTest({ connectorType: 'HTTP' })

      clickTest()

      expect(await screen.findByText('httpStatus:204')).toBeInTheDocument()
    })

    it('shows no preview for MQTT', async () => {
      mockApiRequest.mockResolvedValue({ data: testResult() })
      const { container } = renderConnectionTest({ connectorType: 'MQTT' })

      clickTest()

      expect(await screen.findByText('success')).toBeInTheDocument()
      expect(container.querySelector('pre')).not.toBeInTheDocument()
      expect(screen.queryByText(/httpStatus/)).not.toBeInTheDocument()
    })

    it('shows a hint instead of the truncation marker for a truncated preview', async () => {
      const preview = 'id,value\n1,20.5\n[truncated]'
      mockApiRequest.mockResolvedValue({ data: testResult({ preview, previewTruncated: true }) })
      const { container } = renderConnectionTest()

      clickTest()

      expect(await screen.findByText('previewTruncated')).toBeInTheDocument()
      expect(container.querySelector('pre')?.textContent).toBe('id,value\n1,20.5')
      expect(screen.queryByText(/\[truncated\]/)).not.toBeInTheDocument()
    })
  })

  describe('failed test', () => {
    it('shows the cause and the message', async () => {
      const message = 'FATAL: password authentication failed'
      mockApiRequest.mockResolvedValue({ data: testResult({ success: false, reason: 'SOURCE_ERROR', message }) })
      renderConnectionTest()

      clickTest()

      expect(await screen.findByText('failure')).toBeInTheDocument()
      expect(screen.getByText('cause causes.SOURCE_ERROR')).toBeInTheDocument()
      expect(screen.getByText(`details ${message}`)).toBeInTheDocument()
    })

    it('shows the HTTP status and the body for HTTP', async () => {
      mockApiRequest.mockResolvedValue({
        data: testResult({
          success: false,
          reason: 'SOURCE_ERROR',
          message: 'HTTP 500',
          preview: '{"error":"down"}',
          httpStatus: 500,
        }),
      })
      const { container } = renderConnectionTest({ connectorType: 'HTTP' })

      clickTest()

      expect(await screen.findByText('failure')).toBeInTheDocument()
      expect(screen.getByText('httpStatus:500')).toBeInTheDocument()
      expect(container.querySelector('pre')?.textContent).toBe('{\n  "error": "down"\n}')
    })
  })

  describe('error response', () => {
    it('shows the cause, the detail and the field error messages for a validation error', async () => {
      mockApiRequest.mockRejectedValue(
        mockApiError(400, 'Input validation failed', 'urn:civitas:error:VALIDATION_FAILED', {
          fieldErrors: [{ field: 'connector.table', message: 'must not be blank' }],
        }),
      )
      renderConnectionTest()

      clickTest()

      expect(await screen.findByText('failure')).toBeInTheDocument()
      expect(screen.getByText('cause causes.VALIDATION_FAILED')).toBeInTheDocument()
      expect(screen.getByText('details Input validation failed must not be blank')).toBeInTheDocument()
    })

    it.each([
      [403, 'FORBIDDEN'],
      [404, 'NOT_FOUND'],
      [429, 'TOO_MANY_REQUESTS'],
      [502, 'EXTERNAL_SYSTEM_ERROR'],
      [504, 'EXTERNAL_SYSTEM_TIMEOUT'],
    ])('shows the cause and the detail for status %i', async (status, code) => {
      mockApiRequest.mockRejectedValue(mockApiError(status, `Detail ${status}`, `urn:civitas:error:${code}`))
      renderConnectionTest()

      clickTest()

      expect(await screen.findByText(`cause causes.${code}`)).toBeInTheDocument()
      expect(screen.getByText(`details Detail ${status}`)).toBeInTheDocument()
    })

    it('shows the unknown cause for a code without text', async () => {
      mockApiRequest.mockRejectedValue(mockApiError(500, 'Detail', 'urn:civitas:error:INTERNAL_ERROR'))
      renderConnectionTest()

      clickTest()

      expect(await screen.findByText('cause causes.UNKNOWN')).toBeInTheDocument()
    })
  })

  it('tests again with the current values', async () => {
    mockApiRequest.mockResolvedValue({ data: testResult() })
    renderConnectionTest()

    clickTest()
    await screen.findByText('success')
    clickTest('testAgain')

    await waitFor(() => expect(mockApiRequest).toHaveBeenCalledTimes(2))
    expect(mockApiRequest.mock.calls[1][0].data).toEqual(mockApiRequest.mock.calls[0][0].data)
  })

  it('hides the result when a connector field changes', async () => {
    mockApiRequest.mockResolvedValue({ data: testResult() })
    const { form } = renderConnectionTest()

    clickTest()
    await screen.findByText('success')
    act(() => form.setValue('configuration.table', 'other_table'))

    await waitFor(() => expect(screen.queryByText('success')).not.toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'button' })).toBeEnabled()
  })

  describe('masked password', () => {
    const maskedConfiguration = { ...SQL_CONFIGURATION, password: MASKED_SECRET }

    it('asks for the password again for a changed connector and sends no request', () => {
      renderConnectionTest({ configuration: maskedConfiguration, isConnectorChanged: true })

      clickTest()

      expect(screen.getByText('reenterPassword')).toBeInTheDocument()
      expect(mockApiRequest).not.toHaveBeenCalled()
    })

    it('hides the hint when a password is typed', async () => {
      const { form } = renderConnectionTest({ configuration: maskedConfiguration, isConnectorChanged: true })

      clickTest()
      act(() => form.setValue('configuration.password', 'new-secret'))

      await waitFor(() => expect(screen.queryByText('reenterPassword')).not.toBeInTheDocument())
    })

    it('sends the request for an unchanged connector', async () => {
      mockApiRequest.mockResolvedValue({ data: testResult() })
      renderConnectionTest({ configuration: maskedConfiguration })

      clickTest()

      await waitFor(() => expect(mockApiRequest).toHaveBeenCalledTimes(1))
      expect(screen.queryByText('reenterPassword')).not.toBeInTheDocument()
    })
  })

  it('sends the connector values and the name of the first API call', async () => {
    mockApiRequest.mockResolvedValue({ data: testResult() })
    renderConnectionTest({ connectorType: 'HTTP' })

    clickTest()

    await waitFor(() => expect(mockApiRequest).toHaveBeenCalledTimes(1))
    expect(mockApiRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'POST',
        endpoint: `/datasources/${DATASOURCE_ID}/connection-test`,
        data: {
          connector: ConnectorFormToApiSchema.parse({ connectorType: 'HTTP', configuration: HTTP_CONFIGURATION }),
          apiCall: 'Get all stations',
        },
      }),
    )
  })

  it('keeps the form clean after a test', async () => {
    mockApiRequest.mockResolvedValue({ data: testResult() })
    renderConnectionTest()
    expect(screen.getByTestId('isDirty')).toHaveTextContent('false')

    clickTest()
    await screen.findByText('success')

    expect(screen.getByTestId('isDirty')).toHaveTextContent('false')
  })
})
