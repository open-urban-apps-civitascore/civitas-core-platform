import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { Path, UseFormReturn } from 'react-hook-form'
import { describe, expect, it, vi } from 'vitest'

import { Form } from '@/components/ui/form'
import { DATAPOOL_SCOPE_TYPES, Datasource, DatasourceFormDraft } from '@/types/datasources'

import { useDatasourceForm } from '../../hooks/useDatasourceForm'
import { ConnectorTab } from './ConnectorTab'

vi.mock('@/app/services/api/datasources/clientRequests', async importOriginal => ({
  ...(await importOriginal<typeof import('@/app/services/api/datasources/clientRequests')>()),
  useUpdateDatasource: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUpdateDatasourceReleased: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useReleaseDatasource: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUnreleaseDatasource: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn(), info: vi.fn() },
}))

const API_CALL = { name: 'Get all stations', method: 'GET', path: '/stations' }

const createHttpDatasource = (
  baseUrl: string,
  authentication: Record<string, unknown>,
  apiCall: Record<string, unknown>,
): Datasource => ({
  id: 'ds-1',
  createdAt: '2024-01-01',
  modifiedAt: '2024-01-01',
  name: 'Test Datasource',
  description: 'A test datasource',
  dataSourceStatus: 'DRAFT',
  connector: {
    connectorType: 'HTTP',
    baseUrl,
    authentication,
    apiCalls: [apiCall],
  },
  dataStructureVersion: null,
  inUse: false,
  inUseByReleased: false,
  datapoolScope: { type: DATAPOOL_SCOPE_TYPES.NONE },
})

const renderConnectorTab = (
  authentication: Record<string, unknown>,
  baseUrl = 'https://api.example.org/v1',
  apiCall: Record<string, unknown> = API_CALL,
) => {
  const datasource = createHttpDatasource(baseUrl, authentication, apiCall)
  let form: UseFormReturn<DatasourceFormDraft> | undefined
  const Wrapper = () => {
    const datasourceForm = useDatasourceForm(datasource, [], [], [], [])
    form = datasourceForm.form
    return (
      <Form {...datasourceForm.form}>
        <ConnectorTab
          form={datasourceForm.form}
          datasourceId={datasource.id}
          connectorType="HTTP"
          isConnectorComplete={false}
          isConnectorChanged={false}
        />
      </Form>
    )
  }
  render(
    <QueryClientProvider client={new QueryClient()}>
      <Wrapper />
    </QueryClientProvider>,
  )
  const getFieldError = (path: string) => form?.getFieldState(path as Path<DatasourceFormDraft>).error
  const setServerError = (path: string) =>
    act(() =>
      form?.setError(path as Path<DatasourceFormDraft>, {
        type: 'server',
        message: 'datasources.errors.rejectedByServer',
      }),
    )
  return { getError: (path: string) => getFieldError(path)?.message, getFieldError, setServerError }
}

const selectAuthenticationType = async (labelKey: string) => {
  fireEvent.click(screen.getByLabelText(/http\.authentication\.label/))
  fireEvent.click(await screen.findByText(labelKey))
  await act(async () => {})
}

describe('ConnectorTab with the authentication of the Data source form', () => {
  it('shows no error at the authentication after the user selects API token', async () => {
    const { getFieldError } = renderConnectorTab({ type: 'BASIC', username: 'reader', password: '********' })

    await selectAuthenticationType('http.authentication.apiToken')

    expect(await screen.findByLabelText(/http\.apiTokenIn\.label/)).toBeInTheDocument()
    expect(getFieldError('configuration.authentication')).toBeUndefined()
  })

  it('shows and clears the https error at an http API host when the authentication changes', async () => {
    renderConnectorTab({ type: 'NONE' }, 'http://api.example.org')

    await selectAuthenticationType('http.authentication.bearerToken')
    expect(await screen.findByText('datasources.errors.baseUrlHttpsRequired')).toBeInTheDocument()

    await selectAuthenticationType('http.authentication.none')
    await waitFor(() => expect(screen.queryByText('datasources.errors.baseUrlHttpsRequired')).not.toBeInTheDocument())
  })

  it.each([
    ['QUERY', 'http.apiTokenIn.query', { queryParameters: [{ name: 'key', value: 'x' }] }],
    ['HEADER', 'http.apiTokenIn.header', { headers: [{ name: 'key', value: 'x' }] }],
  ])('checks the API token name again when the user sends the token as %s', async (location, optionKey, parameters) => {
    const otherLocation = location === 'QUERY' ? 'HEADER' : 'QUERY'
    renderConnectorTab({ type: 'API_TOKEN', name: 'key', value: '********', in: otherLocation }, undefined, {
      ...API_CALL,
      ...parameters,
    })
    expect(screen.queryByText('datasources.errors.apiTokenNameCollision')).not.toBeInTheDocument()

    fireEvent.click(screen.getByLabelText(/http\.apiTokenIn\.label/))
    fireEvent.click(await screen.findByText(optionKey))

    expect(await screen.findByText('datasources.errors.apiTokenNameCollision')).toBeInTheDocument()
  })

  it('shows and clears the error at the query parameter when the API token name changes', async () => {
    const { getError } = renderConnectorTab(
      { type: 'API_TOKEN', name: 'other', value: '********', in: 'QUERY' },
      undefined,
      {
        ...API_CALL,
        queryParameters: [{ name: 'key', value: 'x' }],
      },
    )
    const parameterPath = 'configuration.apiCalls.0.queryParameters.0.name'
    const name = screen.getByLabelText(/http\.apiTokenName\.label/)

    fireEvent.change(name, { target: { value: 'key' } })
    await waitFor(() => expect(getError(parameterPath)).toBe('datasources.errors.parameterApiTokenDuplicate'))

    fireEvent.change(name, { target: { value: 'key-2' } })
    await waitFor(() => expect(getError(parameterPath)).toBeUndefined())
  })

  it('clears the error at the query parameter when the user selects another authentication', async () => {
    const { getError } = renderConnectorTab(
      { type: 'API_TOKEN', name: 'other', value: '********', in: 'QUERY' },
      undefined,
      {
        ...API_CALL,
        queryParameters: [{ name: 'key', value: 'x' }],
      },
    )
    const parameterPath = 'configuration.apiCalls.0.queryParameters.0.name'
    fireEvent.change(screen.getByLabelText(/http\.apiTokenName\.label/), { target: { value: 'key' } })
    await waitFor(() => expect(getError(parameterPath)).toBe('datasources.errors.parameterApiTokenDuplicate'))

    await selectAuthenticationType('http.authentication.none')

    await waitFor(() => expect(getError(parameterPath)).toBeUndefined())
  })

  it('removes the error of the backend when the user edits the API token name', async () => {
    const { getError, setServerError } = renderConnectorTab({
      type: 'API_TOKEN',
      name: 'key',
      value: '********',
      in: 'HEADER',
    })
    setServerError('configuration.authentication.name')

    fireEvent.change(screen.getByLabelText(/http\.apiTokenName\.label/), { target: { value: 'key-2' } })
    await act(async () => {})

    expect(getError('configuration.authentication.name')).toBeUndefined()
  })

  it('shows no error after loading an http API host with an authentication', () => {
    renderConnectorTab({ type: 'BEARER_TOKEN', token: '********' }, 'http://api.example.org')

    expect(screen.queryByText('datasources.errors.baseUrlHttpsRequired')).not.toBeInTheDocument()
  })
})
