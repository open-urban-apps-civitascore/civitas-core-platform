import { zodResolver } from '@hookform/resolvers/zod'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { Path, useForm, UseFormReturn, useFormState } from 'react-hook-form'
import { toast } from 'sonner'
import { describe, expect, it, vi } from 'vitest'

import {
  DISABLED_CHECKBOX_CLASS_NAME,
  DISABLED_INPUT_CLASS_NAME,
} from '@/components/form/fields/disabledFieldClassNames'
import { Form } from '@/components/ui/form'
import {
  CURSOR_PAGINATION_FORM,
  LIMIT_OFFSET_PAGINATION_FORM,
  PAGINATION_FORM,
} from '@/test-support/paginationFixtures'
import {
  ApiCallSnapshots,
  CollapsedApiCallSections,
  EMPTY_CURSOR_PAGINATION,
  EMPTY_LIMIT_OFFSET_PAGINATION,
  EMPTY_PAGINATION,
  HttpApiCallForm,
  HttpAuthenticationForm,
} from '@/types/connectors'
import { DatasourceFormDraft, DatasourceFormDraftSchema } from '@/types/datasources'

import { ApiCallsTab } from './ApiCallsTab'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string, values?: { name?: string }) => (values?.name ? `${key}:${values.name}` : key),
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn() },
}))

const API_CALL: HttpApiCallForm = {
  name: 'stations',
  path: '/stations',
  queryParameters: [{ name: 'status', value: 'active' }],
  headers: [{ name: 'Accept', value: 'application/json' }],
  pagination: EMPTY_PAGINATION,
}

interface RenderOptions {
  apiCalls?: HttpApiCallForm[]
  isReadOnly?: boolean
  isDatasourceReleased?: boolean
  dataStructureName?: string
  baseUrl?: string
  authentication?: HttpAuthenticationForm
}

const renderApiCallsTab = (options: RenderOptions = {}) => {
  const {
    apiCalls = [],
    isReadOnly = false,
    isDatasourceReleased = false,
    dataStructureName,
    baseUrl = '',
    authentication = { type: 'NONE' },
  } = options
  const defaultValues: DatasourceFormDraft = {
    id: '1',
    name: 'Test Data source',
    description: 'Description',
    dataSourceStatus: 'DRAFT',
    dataStructureVersionId: null,
    connectorType: 'HTTP',
    configuration: { baseUrl, authentication, apiCalls },
  }
  let form: UseFormReturn<DatasourceFormDraft> | undefined
  let isFormDirty = false
  let remountTab: (() => void) | undefined

  const Wrapper = () => {
    form = useForm<DatasourceFormDraft>({
      resolver: zodResolver(DatasourceFormDraftSchema),
      mode: 'onChange',
      defaultValues,
    })
    isFormDirty = useFormState({ control: form.control }).isDirty
    const [editingApiCalls, setEditingApiCalls] = useState<ApiCallSnapshots>(new Map())
    const [collapsedSections, setCollapsedSections] = useState<CollapsedApiCallSections>(new Map())
    const [tabKey, setTabKey] = useState(0)
    remountTab = () => setTabKey(key => key + 1)
    return (
      <Form {...form}>
        <ApiCallsTab
          key={tabKey}
          form={form}
          isReadOnly={isReadOnly}
          isDatasourceReleased={isDatasourceReleased}
          dataStructureName={dataStructureName}
          editingApiCalls={editingApiCalls}
          collapsedSections={collapsedSections}
          onEditingApiCallsChange={setEditingApiCalls}
          onApiCallAdd={vi.fn()}
          onApiCallRemove={vi.fn()}
          onCollapsedSectionsChange={setCollapsedSections}
        />
      </Form>
    )
  }

  render(<Wrapper />)
  const getApiCalls = () => form?.getValues('configuration.apiCalls') as HttpApiCallForm[] | undefined
  const isDirty = () => isFormDirty
  const resetForm = () => act(() => form?.reset(defaultValues))
  const remount = () => act(() => remountTab?.())
  const getApiTokenNameError = () => form?.getFieldState('configuration.authentication.name').error?.message
  const getError = (path: string) => form?.getFieldState(path as Path<DatasourceFormDraft>).error?.message
  const setServerError = (path: string) =>
    act(() => form?.setError(path as Path<DatasourceFormDraft>, { type: 'server', message: SERVER_ERROR }))
  return {
    user: userEvent.setup(),
    getApiCalls,
    isDirty,
    resetForm,
    remount,
    getApiTokenNameError,
    getError,
    setServerError,
  }
}

const SERVER_ERROR = 'datasources.errors.rejectedByServer'

const DISABLED_INPUT_CLASSES = DISABLED_INPUT_CLASS_NAME.split(' ')
const DISABLED_CHECKBOX_CLASSES = DISABLED_CHECKBOX_CLASS_NAME.split(' ')
const DISABLED_INPUT_CLASSES_WITHOUT_HEIGHT = DISABLED_INPUT_CLASSES.filter(className => className !== 'disabled:h-9')

const openApiCall = async (user: ReturnType<typeof userEvent.setup>) => {
  await user.click(screen.getByRole('button', { name: /stations/ }))
}

const selectOption = async (user: ReturnType<typeof userEvent.setup>, combobox: string | RegExp, option: string) => {
  await user.click(screen.getByRole('combobox', { name: combobox }))
  await user.click(screen.getByRole('option', { name: option }))
}

const openPagination = async (user: ReturnType<typeof userEvent.setup>, typeLabel: string) => {
  await openApiCall(user)
  await user.click(screen.getByRole('checkbox', { name: 'enable' }))
  await selectOption(user, 'type.label', typeLabel)
}

describe('ApiCallsTab', () => {
  it('shows the empty state without API calls', () => {
    renderApiCallsTab()

    expect(screen.getByText('empty.title')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'add' })).toBeInTheDocument()
  })

  it('creates an open API call and focuses the path', async () => {
    const { user, getApiCalls } = renderApiCallsTab()

    await user.click(screen.getByRole('button', { name: 'add' }))

    expect(getApiCalls()).toEqual([
      { name: '', path: '', queryParameters: [], headers: [], pagination: EMPTY_PAGINATION },
    ])
    await waitFor(() => expect(screen.getByRole('textbox', { name: 'path.label' })).toHaveFocus())
  })

  it('hides the create button while one API call exists', () => {
    renderApiCallsTab({ apiCalls: [API_CALL] })

    expect(screen.queryByRole('button', { name: 'add' })).not.toBeInTheDocument()
  })

  it('shows method, name, path with query and data structure in the closed row', () => {
    renderApiCallsTab({ apiCalls: [API_CALL], dataStructureName: 'ChargingStation' })

    expect(screen.getByText('GET')).toBeInTheDocument()
    expect(screen.getByText('stations')).toBeInTheDocument()
    expect(screen.getByText('/stations?status=active → ChargingStation')).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: 'path.label' })).not.toBeInTheDocument()
  })

  it('shows no arrow without a data structure', () => {
    renderApiCallsTab({ apiCalls: [API_CALL] })

    expect(screen.getByText('/stations?status=active')).toBeInTheDocument()
  })

  it('opens the edit view on a click on the row', async () => {
    const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })

    await openApiCall(user)

    expect(screen.getByRole('textbox', { name: 'path.label' })).toHaveValue('/stations')
    expect(screen.queryByRole('button', { name: 'Open menu' })).not.toBeInTheDocument()
  })

  it('shows the name in the edit view as text, not as a button', async () => {
    const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
    await openApiCall(user)

    expect(screen.getByText('stations')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'stations' })).not.toBeInTheDocument()
  })

  it.each([
    ['a form reset', 'resetForm'],
    ['mounting the tab again', 'remount'],
  ] as const)('keeps the request step collapsed after %s', async (_name, action) => {
    const rendered = renderApiCallsTab({ apiCalls: [API_CALL] })
    await openApiCall(rendered.user)
    await rendered.user.click(screen.getByRole('button', { name: /^request\.title/ }))

    rendered[action]()

    expect(screen.getByText('request.title')).toBeInTheDocument()
    expect(screen.queryByText('request.question')).not.toBeInTheDocument()
  })

  it('keeps the edit view of the following API call after a deletion', async () => {
    const otherApiCall = { ...API_CALL, name: 'sensors', path: '/sensors', queryParameters: [] }
    const { user } = renderApiCallsTab({ apiCalls: [API_CALL, otherApiCall] })
    await user.click(screen.getByRole('button', { name: /sensors/ }))

    await user.click(screen.getByRole('button', { name: 'Open menu' }))
    await user.click(await screen.findByText('delete'))
    await user.click(screen.getByTestId('confirmButton'))

    expect(screen.getByRole('textbox', { name: 'path.label' })).toHaveValue('/sensors')
  })

  it('keeps the API call open after a form reset', async () => {
    const { user, resetForm } = renderApiCallsTab({ apiCalls: [API_CALL] })
    await openApiCall(user)

    resetForm()

    expect(screen.getByRole('textbox', { name: 'path.label' })).toBeInTheDocument()
  })

  it('keeps the API call open after the tab is mounted again', async () => {
    const { user, remount } = renderApiCallsTab({ apiCalls: [API_CALL] })
    await openApiCall(user)

    remount()

    expect(screen.getByRole('textbox', { name: 'path.label' })).toBeInTheDocument()
  })

  it('collapses and expands the request step', async () => {
    const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
    await openApiCall(user)
    const requestStep = screen.getByRole('button', { name: /^request\.title/ })

    await user.click(requestStep)
    expect(screen.queryByText('request.question')).not.toBeInTheDocument()

    await user.click(requestStep)
    expect(screen.getByText('request.question')).toBeInTheDocument()
  })

  it('keeps the changes and shows the row after apply', async () => {
    const { user, getApiCalls } = renderApiCallsTab({ apiCalls: [API_CALL] })
    await openApiCall(user)

    await user.type(screen.getByRole('textbox', { name: 'path.label' }), '/v2')
    await user.click(screen.getByRole('button', { name: 'apply' }))

    expect(screen.queryByRole('textbox', { name: 'path.label' })).not.toBeInTheDocument()
    expect(screen.getByText('/stations/v2?status=active')).toBeInTheDocument()
    expect(getApiCalls()?.[0].path).toBe('/stations/v2')
  })

  it('restores path, name, query parameters and headers after cancel', async () => {
    const { user, getApiCalls, isDirty } = renderApiCallsTab({ apiCalls: [API_CALL] })
    await openApiCall(user)

    await user.type(screen.getByRole('textbox', { name: 'path.label' }), '/v2')
    await user.clear(screen.getByRole('textbox', { name: /^name\.label/ }))
    await user.click(screen.getByRole('button', { name: 'parameters.addQuery' }))
    await user.click(screen.getByRole('button', { name: 'parameters.header remove' }))
    await user.click(screen.getByRole('button', { name: 'cancel' }))

    expect(screen.queryByRole('textbox', { name: 'path.label' })).not.toBeInTheDocument()
    expect(getApiCalls()).toEqual([API_CALL])
    expect(isDirty()).toBe(false)
  })

  it('clears the errors of the discarded values after cancel', async () => {
    const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
    await openApiCall(user)
    await user.type(screen.getByDisplayValue('Accept'), ' x')
    expect(await screen.findByText('datasources.errors.invalidHeaderName')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'cancel' }))

    const apiCallRow = screen.getByRole('button', { name: /stations/ }).parentElement
    await waitFor(() => expect(apiCallRow).not.toHaveClass('border-destructive'))
    await openApiCall(user)
    expect(screen.getByDisplayValue('Accept')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText('datasources.errors.invalidHeaderName')).not.toBeInTheDocument())
  })

  it('removes a created API call after cancel', async () => {
    const { user, getApiCalls } = renderApiCallsTab()
    await user.click(screen.getByRole('button', { name: 'add' }))

    await user.click(screen.getByRole('button', { name: 'cancel' }))

    expect(getApiCalls()).toEqual([])
    expect(screen.getByText('empty.title')).toBeInTheDocument()
  })

  it('restores the applied values after cancel', async () => {
    const { user, getApiCalls } = renderApiCallsTab({ apiCalls: [API_CALL] })
    await openApiCall(user)
    await user.type(screen.getByRole('textbox', { name: 'path.label' }), '/v2')
    await user.click(screen.getByRole('button', { name: 'apply' }))

    await openApiCall(user)
    await user.type(screen.getByRole('textbox', { name: 'path.label' }), '/v3')
    await user.click(screen.getByRole('button', { name: 'cancel' }))

    expect(getApiCalls()?.[0].path).toBe('/stations/v2')
  })

  it('shows apply and cancel while the request step is collapsed', async () => {
    const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
    await openApiCall(user)

    await user.click(screen.getByRole('button', { name: /^request\.title/ }))

    expect(screen.getByRole('button', { name: 'apply' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'cancel' })).toBeInTheDocument()
  })

  it.each([
    ['read-only', { isReadOnly: true }],
    ['released', { isDatasourceReleased: true }],
  ])('shows only close when %s', async (_name, options) => {
    const { user } = renderApiCallsTab({ apiCalls: [API_CALL], ...options })
    await openApiCall(user)

    expect(screen.queryByRole('button', { name: 'apply' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'cancel' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'close' }))

    expect(screen.queryByRole('textbox', { name: 'path.label' })).not.toBeInTheDocument()
  })

  describe('copy URL', () => {
    it('copies the URL with host and shows a toast', async () => {
      const { user } = renderApiCallsTab({ apiCalls: [API_CALL], baseUrl: 'https://api.example.org/v1/' })
      await openApiCall(user)

      await user.click(screen.getByRole('button', { name: 'copyUrl' }))

      expect(await navigator.clipboard.readText()).toBe('https://api.example.org/v1/stations?status=active')
      expect(toast.success).toHaveBeenCalledWith('copySuccess')
    })

    it('copies only the path without a host', async () => {
      const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
      await openApiCall(user)

      await user.click(screen.getByRole('button', { name: 'copyUrl' }))

      expect(await navigator.clipboard.readText()).toBe('/stations?status=active')
    })

    it('keeps the request step open after a copy', async () => {
      const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
      await openApiCall(user)

      await user.click(screen.getByRole('button', { name: 'copyUrl' }))

      expect(screen.getByText('request.question')).toBeInTheDocument()
    })
  })

  it('collapses the request step on a click on the free space of the row', async () => {
    const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
    await openApiCall(user)

    await user.click(screen.getByText('request.title').closest('div')!.lastElementChild!)

    expect(screen.queryByText('request.question')).not.toBeInTheDocument()
  })

  it('shows GET as the only, disabled method', async () => {
    const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
    await openApiCall(user)

    const method = screen.getByRole('combobox', { name: 'method.label' })
    expect(method).toBeDisabled()
    expect(method).toHaveTextContent('GET')
    expect(method.querySelector('svg')).toBeInTheDocument()
  })

  it('shows query parameters before headers', async () => {
    const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
    await openApiCall(user)

    const names = screen.getAllByRole('textbox', { name: /name$/ })
    expect(names.map(input => input.getAttribute('aria-label'))).toEqual([
      'parameters.query name',
      'parameters.header name',
    ])
  })

  it('adds and removes query parameters and headers', async () => {
    const { user, getApiCalls } = renderApiCallsTab({ apiCalls: [API_CALL] })
    await openApiCall(user)

    await user.click(screen.getByRole('button', { name: 'parameters.addQuery' }))
    await user.type(screen.getAllByRole('textbox', { name: 'parameters.query name' })[1], 'limit')
    await user.click(screen.getByRole('button', { name: 'parameters.addHeader' }))
    await user.click(screen.getAllByRole('button', { name: 'parameters.header remove' })[0])

    expect(getApiCalls()?.[0]).toMatchObject({
      queryParameters: [
        { name: 'status', value: 'active' },
        { name: 'limit', value: '' },
      ],
      headers: [{ name: '', value: '' }],
    })
  })

  describe('error of the backend at a header', () => {
    const API_CALL_WITH_HEADERS: HttpApiCallForm = {
      ...API_CALL,
      headers: [
        { name: 'Accept', value: 'application/json' },
        { name: 'X-Test', value: '5 €' },
      ],
    }

    it('keeps the error at the same header when the user removes another header', async () => {
      const { user, getError, setServerError } = renderApiCallsTab({ apiCalls: [API_CALL_WITH_HEADERS] })
      await openApiCall(user)
      setServerError('configuration.apiCalls.0.headers.1.value')

      await user.click(screen.getAllByRole('button', { name: 'parameters.header remove' })[0])

      await waitFor(() => expect(getError('configuration.apiCalls.0.headers.0.value')).toBe(SERVER_ERROR))
      expect(screen.getByText(SERVER_ERROR)).toBeInTheDocument()
    })

    it('keeps the error when the user changes the name of another header', async () => {
      const { user, getApiCalls, getError, setServerError } = renderApiCallsTab({ apiCalls: [API_CALL_WITH_HEADERS] })
      await openApiCall(user)
      setServerError('configuration.apiCalls.0.headers.1.value')

      await user.type(screen.getAllByRole('textbox', { name: 'parameters.header name' })[0], '-2')
      await act(async () => {})

      expect(getApiCalls()?.[0].headers[0].name).toBe('Accept-2')
      expect(getError('configuration.apiCalls.0.headers.1.value')).toBe(SERVER_ERROR)
    })

    it('removes the error when the user edits the name of the header', async () => {
      const { user, getError, setServerError } = renderApiCallsTab({ apiCalls: [API_CALL_WITH_HEADERS] })
      await openApiCall(user)
      setServerError('configuration.apiCalls.0.headers.1.name')

      await user.type(screen.getAllByRole('textbox', { name: 'parameters.header name' })[1], '-2')
      await act(async () => {})

      expect(getError('configuration.apiCalls.0.headers.1.name')).toBeUndefined()
    })

    it('removes the error when the user edits the header', async () => {
      const { user, getError, setServerError } = renderApiCallsTab({ apiCalls: [API_CALL_WITH_HEADERS] })
      await openApiCall(user)
      setServerError('configuration.apiCalls.0.headers.1.value')

      await user.clear(screen.getAllByRole('textbox', { name: 'parameters.header value' })[1])

      await waitFor(() => expect(getError('configuration.apiCalls.0.headers.1.value')).toBeUndefined())
    })
  })

  describe('API token name', () => {
    const API_TOKEN: HttpAuthenticationForm = { type: 'API_TOKEN', name: 'X-API-Key', value: 'key-1', in: 'HEADER' }
    const COLLISION = 'datasources.errors.apiTokenNameCollision'

    it('shows and clears the error when a header gets and loses the API token name', async () => {
      const { user, getApiTokenNameError } = renderApiCallsTab({ apiCalls: [API_CALL], authentication: API_TOKEN })
      await openApiCall(user)
      const headerName = screen.getByRole('textbox', { name: 'parameters.header name' })

      await user.clear(headerName)
      await user.type(headerName, 'x-api-key')
      await waitFor(() => expect(getApiTokenNameError()).toBe(COLLISION))
      expect(screen.getByText('datasources.errors.parameterApiTokenDuplicate')).toBeInTheDocument()

      await user.type(headerName, '-2')
      await waitFor(() => expect(getApiTokenNameError()).toBeUndefined())
      expect(screen.queryByText('datasources.errors.parameterApiTokenDuplicate')).not.toBeInTheDocument()
    })

    it('clears the error when the API call with the API token name is deleted', async () => {
      const { user, getApiTokenNameError } = renderApiCallsTab({ apiCalls: [API_CALL], authentication: API_TOKEN })
      await openApiCall(user)
      const headerName = screen.getByRole('textbox', { name: 'parameters.header name' })
      await user.clear(headerName)
      await user.type(headerName, 'X-API-Key')
      await user.click(screen.getByRole('button', { name: 'apply' }))
      await waitFor(() => expect(getApiTokenNameError()).toBe(COLLISION))

      await user.click(screen.getByRole('button', { name: 'Open menu' }))
      await user.click(await screen.findByText('delete'))
      await user.click(screen.getByTestId('confirmButton'))

      await waitFor(() => expect(getApiTokenNameError()).toBeUndefined())
    })

    it('clears the error when the change of the header is cancelled', async () => {
      const { user, getApiTokenNameError } = renderApiCallsTab({ apiCalls: [API_CALL], authentication: API_TOKEN })
      await openApiCall(user)
      const headerName = screen.getByRole('textbox', { name: 'parameters.header name' })
      await user.clear(headerName)
      await user.type(headerName, 'X-API-Key')
      await waitFor(() => expect(getApiTokenNameError()).toBe(COLLISION))

      await user.click(screen.getByRole('button', { name: 'cancel' }))

      await waitFor(() => expect(getApiTokenNameError()).toBeUndefined())
    })
  })

  it('deletes the API call after confirmation', async () => {
    const { user, getApiCalls } = renderApiCallsTab({ apiCalls: [API_CALL] })

    await user.click(screen.getByRole('button', { name: 'Open menu' }))
    await user.click(await screen.findByText('delete'))
    expect(screen.getByText('delete.description:stations')).toBeInTheDocument()
    await user.click(screen.getByTestId('confirmButton'))

    expect(getApiCalls()).toEqual([])
    expect(screen.getByRole('button', { name: 'add' })).toBeInTheDocument()
  })

  it('keeps the API call when the deletion is cancelled', async () => {
    const { user, getApiCalls } = renderApiCallsTab({ apiCalls: [API_CALL] })

    await user.click(screen.getByRole('button', { name: 'Open menu' }))
    await user.click(await screen.findByText('delete'))
    await user.click(screen.getByTestId('discardButton'))

    expect(getApiCalls()).toHaveLength(1)
    expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
  })

  it('shows the path error', async () => {
    const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
    await openApiCall(user)

    await user.clear(screen.getByRole('textbox', { name: 'path.label' }))
    await user.type(screen.getByRole('textbox', { name: 'path.label' }), 'stations')

    expect(await screen.findByText('datasources.errors.invalidPath')).toBeInTheDocument()
  })

  it.each([
    ['read-only', { isReadOnly: true }],
    ['released', { isDatasourceReleased: true }],
  ])('allows no changes when %s', async (_name, options) => {
    const { user } = renderApiCallsTab({ apiCalls: [API_CALL], ...options })

    expect(screen.queryByRole('button', { name: 'Open menu' })).not.toBeInTheDocument()
    await openApiCall(user)

    const inputs = screen.getAllByRole('textbox')
    inputs.forEach(input => expect(input).toBeDisabled())
    const pathInput = screen.getByRole('textbox', { name: 'path.label' })
    const nameInput = screen.getByRole('textbox', { name: /^name\.label/ })
    const parameterInputs = inputs.filter(input => input !== pathInput && input !== nameInput)
    parameterInputs.forEach(input => expect(input).toHaveClass(...DISABLED_INPUT_CLASSES))
    expect(pathInput).toHaveClass('disabled:opacity-100')
    expect(screen.getByRole('group')).toHaveClass('h-12', 'border-transparent', 'shadow-none')
    expect(nameInput).toHaveClass(...DISABLED_INPUT_CLASSES_WITHOUT_HEIGHT, 'h-12', 'disabled:h-12')
    expect(nameInput).not.toHaveClass('disabled:h-9')
    expect(screen.queryByRole('button', { name: 'parameters.addQuery' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'parameters.addHeader' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /remove$/ })).not.toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'method.label' }).querySelector('svg')).not.toBeInTheDocument()
  })

  describe('pagination', () => {
    it('shows the fields with start page 0 when checked', async () => {
      const { user, getApiCalls } = renderApiCallsTab({ apiCalls: [API_CALL] })
      await openApiCall(user)
      expect(screen.queryByRole('textbox', { name: /^pageParameter\.label/ })).not.toBeInTheDocument()

      await user.click(screen.getByRole('checkbox', { name: 'enable' }))

      expect(screen.getByRole('combobox', { name: 'type.label' })).toBeEnabled()
      expect(screen.getByRole('textbox', { name: 'startPage.label' })).toHaveValue('0')
      expect(getApiCalls()?.[0].pagination).toEqual({ ...EMPTY_PAGINATION, isEnabled: true })
    })

    it('keeps the values when unchecked and checked again', async () => {
      const { user, getApiCalls } = renderApiCallsTab({ apiCalls: [{ ...API_CALL, pagination: PAGINATION_FORM }] })
      await openApiCall(user)

      await user.click(screen.getByRole('checkbox', { name: 'enable' }))

      expect(getApiCalls()?.[0].pagination).toEqual({ ...PAGINATION_FORM, isEnabled: false })

      await user.click(screen.getByRole('checkbox', { name: 'enable' }))

      expect(getApiCalls()?.[0].pagination).toEqual(PAGINATION_FORM)
      expect(screen.getByRole('textbox', { name: /^pageParameter\.label/ })).toHaveValue('page')
    })

    it('hides only the fields when unchecked', async () => {
      const { user } = renderApiCallsTab({ apiCalls: [{ ...API_CALL, pagination: PAGINATION_FORM }] })
      await openApiCall(user)

      await user.click(screen.getByRole('checkbox', { name: 'enable' }))

      expect(screen.getByRole('checkbox', { name: 'enable' })).toBeInTheDocument()
      expect(screen.queryByRole('textbox', { name: /^pageParameter\.label/ })).not.toBeInTheDocument()
    })

    it('writes the entered values into the form', async () => {
      const { user, getApiCalls } = renderApiCallsTab({ apiCalls: [API_CALL] })
      await openApiCall(user)
      await user.click(screen.getByRole('checkbox', { name: 'enable' }))

      await user.type(screen.getByRole('textbox', { name: /^pageParameter\.label/ }), 'page')
      await user.type(screen.getByRole('textbox', { name: /^pageSizeParameter\.label/ }), 'size')
      await user.type(screen.getByRole('textbox', { name: /^pageSize\.label/ }), '100')
      await user.clear(screen.getByRole('textbox', { name: 'startPage.label' }))
      await user.type(screen.getByRole('textbox', { name: 'startPage.label' }), '1')
      await user.type(screen.getByRole('textbox', { name: 'endPage.label' }), '10')

      expect(getApiCalls()?.[0].pagination).toEqual(PAGINATION_FORM)
    })

    it('shows the page size error and marks the field as invalid', async () => {
      const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
      await openApiCall(user)
      await user.click(screen.getByRole('checkbox', { name: 'enable' }))

      await user.type(screen.getByRole('textbox', { name: /^pageSize\.label/ }), '0')

      expect(await screen.findByText('datasources.errors.pageSizeMin')).toBeInTheDocument()
      expect(screen.getByRole('textbox', { name: /^pageSize\.label/ })).toHaveAttribute('aria-invalid', 'true')
    })

    it('hides the errors when unchecked', async () => {
      const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
      await openApiCall(user)
      await user.click(screen.getByRole('checkbox', { name: 'enable' }))
      await user.type(screen.getByRole('textbox', { name: /^pageSize\.label/ }), '0')
      expect(await screen.findByText('datasources.errors.pageSizeMin')).toBeInTheDocument()

      await user.click(screen.getByRole('checkbox', { name: 'enable' }))

      await waitFor(() => expect(screen.queryByText('datasources.errors.pageSizeMin')).not.toBeInTheDocument())
    })

    it('shows the error at both fields and clears it when the page size parameter has the page parameter name', async () => {
      const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
      await openApiCall(user)
      await user.click(screen.getByRole('checkbox', { name: 'enable' }))
      await user.type(screen.getByRole('textbox', { name: /^pageParameter\.label/ }), 'page')
      await user.type(screen.getByRole('textbox', { name: /^pageSizeParameter\.label/ }), 'page')
      expect(await screen.findAllByText('datasources.errors.paginationParameterDuplicate')).toHaveLength(2)

      await user.type(screen.getByRole('textbox', { name: /^pageParameter\.label/ }), 's')

      await waitFor(() =>
        expect(screen.queryByText('datasources.errors.paginationParameterDuplicate')).not.toBeInTheDocument(),
      )
    })

    it('restores the pagination after cancel', async () => {
      const apiCall = { ...API_CALL, pagination: PAGINATION_FORM }
      const { user, getApiCalls } = renderApiCallsTab({ apiCalls: [apiCall] })
      await openApiCall(user)

      await user.type(screen.getByRole('textbox', { name: /^pageSize\.label/ }), '0')
      await user.click(screen.getByRole('checkbox', { name: 'enable' }))
      await user.click(screen.getByRole('button', { name: 'cancel' }))

      expect(getApiCalls()).toEqual([apiCall])
    })

    it.each([
      ['read-only', { isReadOnly: true }],
      ['released', { isDatasourceReleased: true }],
    ])('disables the checkbox and the fields when %s', async (_name, options) => {
      const { user } = renderApiCallsTab({ apiCalls: [{ ...API_CALL, pagination: PAGINATION_FORM }], ...options })
      await openApiCall(user)

      expect(screen.getByRole('checkbox', { name: 'enable' })).toBeDisabled()
      expect(screen.getByRole('combobox', { name: 'type.label' })).toBeDisabled()
      expect(screen.getByRole('textbox', { name: /^pageParameter\.label/ })).toBeDisabled()
      expect(screen.getByRole('textbox', { name: 'endPage.label' })).toBeDisabled()
      const checkbox = screen.getByRole('checkbox', { name: 'enable' })
      expect(checkbox).toHaveClass('border-input', 'disabled:opacity-50')
      DISABLED_CHECKBOX_CLASSES.forEach(className => expect(checkbox).not.toHaveClass(className))
      const fields = [...screen.getAllByRole('combobox'), ...screen.getAllByRole('textbox')]
      const requestFields = [
        screen.getByRole('combobox', { name: 'method.label' }),
        screen.getByRole('textbox', { name: 'path.label' }),
        screen.getByRole('textbox', { name: /^name\.label/ }),
      ]
      const paginationFields = fields.filter(field => !requestFields.includes(field))
      paginationFields.forEach(field => expect(field).toHaveClass(...DISABLED_INPUT_CLASSES))
    })

    it('disables the cursor fields when read-only', async () => {
      const { user } = renderApiCallsTab({
        apiCalls: [{ ...API_CALL, pagination: CURSOR_PAGINATION_FORM }],
        isReadOnly: true,
      })
      await openApiCall(user)

      const cursorIn = screen.getByRole('combobox', { name: 'cursorIn.label' })
      expect(cursorIn).toBeDisabled()
      expect(cursorIn).toHaveClass(...DISABLED_INPUT_CLASSES)
      const labels = [/^cursorPath\.label/, /^cursorParameter\.label/, 'limitParameter.label', 'limit.label']
      labels.forEach(name => {
        const field = screen.getByRole('textbox', { name })
        expect(field).toBeDisabled()
        expect(field).toHaveClass(...DISABLED_INPUT_CLASSES)
      })
    })

    it('offers all pagination types', async () => {
      const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
      await openApiCall(user)
      await user.click(screen.getByRole('checkbox', { name: 'enable' }))

      await user.click(screen.getByRole('combobox', { name: 'type.label' }))

      expect(screen.getByRole('option', { name: 'type.pageNumber' })).toBeInTheDocument()
      expect(screen.getByRole('option', { name: 'type.limitOffset' })).toBeInTheDocument()
      expect(screen.getByRole('option', { name: 'type.cursor' })).toBeInTheDocument()
    })

    describe('cursor', () => {
      it('shows the cursor fields and the hint instead of the page number fields', async () => {
        const { user, getApiCalls } = renderApiCallsTab({ apiCalls: [API_CALL] })

        await openPagination(user, 'type.cursor')

        expect(screen.getByText('cursorHint')).toBeInTheDocument()
        expect(screen.getByRole('textbox', { name: /^cursorPath\.label/ })).toBeInTheDocument()
        expect(screen.getByRole('textbox', { name: 'limit.label' })).toBeInTheDocument()
        expect(screen.queryByRole('textbox', { name: /^pageParameter\.label/ })).not.toBeInTheDocument()
        expect(getApiCalls()?.[0].pagination).toEqual({ ...EMPTY_CURSOR_PAGINATION, isEnabled: true })
      })

      it('clears the cursor values when switching back to page number', async () => {
        const { user, getApiCalls } = renderApiCallsTab({ apiCalls: [API_CALL] })
        await openPagination(user, 'type.cursor')
        await user.type(screen.getByRole('textbox', { name: /^cursorPath\.label/ }), '$.next')

        await selectOption(user, 'type.label', 'type.pageNumber')

        expect(screen.queryByText('cursorHint')).not.toBeInTheDocument()
        expect(getApiCalls()?.[0].pagination).toEqual({ ...EMPTY_PAGINATION, isEnabled: true })
      })

      it('sends the cursor as query parameter by default and as header when selected', async () => {
        const { user, getApiCalls } = renderApiCallsTab({ apiCalls: [API_CALL] })
        await openPagination(user, 'type.cursor')
        expect(screen.getByRole('combobox', { name: /^cursorIn\.label/ })).toHaveTextContent('cursorIn.query')

        await selectOption(user, /^cursorIn\.label/, 'cursorIn.header')

        expect(getApiCalls()?.[0].pagination).toMatchObject({ cursorIn: 'HEADER' })
      })

      it('marks the form as dirty only while the cursor location differs from the saved one', async () => {
        const { user, isDirty } = renderApiCallsTab({ apiCalls: [{ ...API_CALL, pagination: CURSOR_PAGINATION_FORM }] })
        await openApiCall(user)

        await selectOption(user, 'cursorIn.label', 'cursorIn.header')
        expect(isDirty()).toBe(true)

        await selectOption(user, 'cursorIn.label', 'cursorIn.query')
        expect(isDirty()).toBe(false)
      })

      it('writes the entered values into the form', async () => {
        const { user, getApiCalls } = renderApiCallsTab({ apiCalls: [API_CALL] })
        await openPagination(user, 'type.cursor')

        await user.type(screen.getByRole('textbox', { name: /^cursorPath\.label/ }), '$.next_cursor')
        await user.type(screen.getByRole('textbox', { name: /^cursorParameter\.label/ }), 'cursor')
        await user.type(screen.getByRole('textbox', { name: 'limitParameter.label' }), 'limit')
        await user.type(screen.getByRole('textbox', { name: 'limit.label' }), '20')

        expect(getApiCalls()?.[0].pagination).toEqual(CURSOR_PAGINATION_FORM)
      })

      it('marks cursor path, cursor parameter and send cursor as as required', async () => {
        const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })

        await openPagination(user, 'type.cursor')

        expect(screen.getByRole('textbox', { name: 'cursorPath.label *' })).toBeInTheDocument()
        expect(screen.getByRole('textbox', { name: 'cursorParameter.label *' })).toBeInTheDocument()
        const cursorInLabel = screen.getByText('cursorIn.label', { selector: 'label' })
        expect(cursorInLabel).toHaveTextContent('cursorIn.label*')
        expect(cursorInLabel).toHaveAttribute('for', screen.getByRole('combobox', { name: 'cursorIn.label' }).id)
        expect(screen.getByRole('textbox', { name: 'limitParameter.label' })).toBeInTheDocument()
      })

      it('shows the error of an invalid cursor path', async () => {
        const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
        await openPagination(user, 'type.cursor')

        await user.type(screen.getByRole('textbox', { name: /^cursorPath\.label/ }), 'next')

        expect(await screen.findByText('datasources.errors.invalidCursorPath')).toBeInTheDocument()
      })

      it('checks the cursor parameter again when the cursor is sent as header', async () => {
        const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
        await openPagination(user, 'type.cursor')
        await user.type(screen.getByRole('textbox', { name: /^cursorParameter\.label/ }), 'my cursor')
        expect(screen.queryByText('datasources.errors.invalidHeaderName')).not.toBeInTheDocument()

        await selectOption(user, /^cursorIn\.label/, 'cursorIn.header')

        expect(await screen.findByText('datasources.errors.invalidHeaderName')).toBeInTheDocument()
      })

      it('shows and clears the error when a query parameter gets and loses the cursor name', async () => {
        const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
        await openPagination(user, 'type.cursor')
        await user.type(screen.getByRole('textbox', { name: /^cursorParameter\.label/ }), 'cursor')
        const queryParameterName = screen.getByRole('textbox', { name: 'parameters.query name' })

        await user.clear(queryParameterName)
        await user.type(queryParameterName, 'cursor')
        expect(await screen.findByText('datasources.errors.paginationQueryDuplicate')).toBeInTheDocument()

        await user.type(queryParameterName, 's')
        await waitFor(() =>
          expect(screen.queryByText('datasources.errors.paginationQueryDuplicate')).not.toBeInTheDocument(),
        )
      })

      it('shows the error also at the query parameter with the cursor name', async () => {
        const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
        await openPagination(user, 'type.cursor')
        await user.type(screen.getByRole('textbox', { name: /^cursorParameter\.label/ }), 'cursor')
        const queryParameterName = screen.getByRole('textbox', { name: 'parameters.query name' })

        await user.clear(queryParameterName)
        await user.type(queryParameterName, 'cursor')

        expect(await screen.findByText('datasources.errors.parameterPaginationDuplicate')).toBeInTheDocument()
        expect(screen.getByText('datasources.errors.paginationQueryDuplicate')).toBeInTheDocument()
      })

      it('clears the error at the query parameter when the cursor parameter changes', async () => {
        const apiCall = { ...API_CALL, queryParameters: [{ name: 'cursor', value: 'x' }] }
        const { user } = renderApiCallsTab({ apiCalls: [apiCall] })
        await openPagination(user, 'type.cursor')
        await user.type(screen.getByRole('textbox', { name: /^cursorParameter\.label/ }), 'cursor')
        expect(await screen.findByText('datasources.errors.parameterPaginationDuplicate')).toBeInTheDocument()

        await user.type(screen.getByRole('textbox', { name: /^cursorParameter\.label/ }), 's')

        await waitFor(() =>
          expect(screen.queryByText('datasources.errors.parameterPaginationDuplicate')).not.toBeInTheDocument(),
        )
      })

      it('clears the error at the query parameter when the pagination is unchecked', async () => {
        const apiCall = { ...API_CALL, queryParameters: [{ name: 'cursor', value: 'x' }] }
        const { user } = renderApiCallsTab({ apiCalls: [apiCall] })
        await openPagination(user, 'type.cursor')
        await user.type(screen.getByRole('textbox', { name: /^cursorParameter\.label/ }), 'cursor')
        expect(await screen.findByText('datasources.errors.parameterPaginationDuplicate')).toBeInTheDocument()

        await user.click(screen.getByRole('checkbox', { name: 'enable' }))

        await waitFor(() =>
          expect(screen.queryByText('datasources.errors.parameterPaginationDuplicate')).not.toBeInTheDocument(),
        )
      })

      it('clears the error when the query parameter with the cursor name is removed', async () => {
        const apiCall = { ...API_CALL, queryParameters: [{ name: 'cursor', value: 'x' }] }
        const { user } = renderApiCallsTab({ apiCalls: [apiCall] })
        await openPagination(user, 'type.cursor')
        await user.type(screen.getByRole('textbox', { name: /^cursorParameter\.label/ }), 'cursor')
        expect(await screen.findByText('datasources.errors.paginationQueryDuplicate')).toBeInTheDocument()

        await user.click(screen.getByRole('button', { name: 'parameters.query remove' }))

        await waitFor(() =>
          expect(screen.queryByText('datasources.errors.paginationQueryDuplicate')).not.toBeInTheDocument(),
        )
      })

      it('shows and clears the error when a header gets the cursor name and is removed', async () => {
        const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
        await openPagination(user, 'type.cursor')
        await selectOption(user, /^cursorIn\.label/, 'cursorIn.header')
        await user.type(screen.getByRole('textbox', { name: /^cursorParameter\.label/ }), 'cursor')
        const headerName = screen.getByRole('textbox', { name: 'parameters.header name' })

        await user.clear(headerName)
        await user.type(headerName, 'Cursor')
        expect(await screen.findByText('datasources.errors.paginationHeaderDuplicate')).toBeInTheDocument()

        await user.click(screen.getByRole('button', { name: 'parameters.header remove' }))
        await waitFor(() =>
          expect(screen.queryByText('datasources.errors.paginationHeaderDuplicate')).not.toBeInTheDocument(),
        )
      })

      it('shows no error of the old values after switching the type', async () => {
        const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
        await openPagination(user, 'type.cursor')
        await user.type(screen.getByRole('textbox', { name: /^cursorPath\.label/ }), 'next')
        expect(await screen.findByText('datasources.errors.invalidCursorPath')).toBeInTheDocument()

        await selectOption(user, 'type.label', 'type.pageNumber')
        await selectOption(user, 'type.label', 'type.cursor')

        expect(screen.getByRole('textbox', { name: /^cursorPath\.label/ })).toHaveValue('')
        expect(screen.queryByText('datasources.errors.invalidCursorPath')).not.toBeInTheDocument()
      })

      it('clears the duplicate error at both fields when the cursor parameter changes', async () => {
        const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
        await openPagination(user, 'type.cursor')
        await user.type(screen.getByRole('textbox', { name: /^cursorParameter\.label/ }), 'cursor')
        await user.type(screen.getByRole('textbox', { name: 'limitParameter.label' }), 'cursor')
        expect(await screen.findAllByText('datasources.errors.paginationParameterDuplicate')).toHaveLength(2)

        await user.type(screen.getByRole('textbox', { name: /^cursorParameter\.label/ }), 's')

        await waitFor(() =>
          expect(screen.queryByText('datasources.errors.paginationParameterDuplicate')).not.toBeInTheDocument(),
        )
      })
    })

    describe('limit/offset', () => {
      it('shows the limit/offset fields instead of the page number fields', async () => {
        const { user, getApiCalls } = renderApiCallsTab({ apiCalls: [API_CALL] })

        await openPagination(user, 'type.limitOffset')

        expect(screen.getByRole('textbox', { name: /^offsetParameter\.label/ })).toBeInTheDocument()
        expect(screen.getByRole('textbox', { name: 'offset.label' })).toHaveValue('0')
        expect(screen.getByRole('textbox', { name: 'limitParameter.label' })).toBeInTheDocument()
        expect(screen.getByRole('textbox', { name: 'limit.label' })).toBeInTheDocument()
        expect(screen.getByRole('textbox', { name: 'countPath.label' })).toBeInTheDocument()
        expect(screen.queryByRole('textbox', { name: /^pageParameter\.label/ })).not.toBeInTheDocument()
        expect(screen.queryByText('cursorHint')).not.toBeInTheDocument()
        expect(getApiCalls()?.[0].pagination).toEqual({ ...EMPTY_LIMIT_OFFSET_PAGINATION, isEnabled: true })
      })

      it('clears the limit/offset values when switching to cursor', async () => {
        const { user, getApiCalls } = renderApiCallsTab({ apiCalls: [API_CALL] })
        await openPagination(user, 'type.limitOffset')
        await user.type(screen.getByRole('textbox', { name: /^offsetParameter\.label/ }), 'offset')

        await selectOption(user, 'type.label', 'type.cursor')

        expect(getApiCalls()?.[0].pagination).toEqual({ ...EMPTY_CURSOR_PAGINATION, isEnabled: true })
      })

      it('writes the entered values into the form', async () => {
        const { user, getApiCalls } = renderApiCallsTab({ apiCalls: [API_CALL] })
        await openPagination(user, 'type.limitOffset')

        await user.type(screen.getByRole('textbox', { name: /^offsetParameter\.label/ }), 'offset')
        await user.clear(screen.getByRole('textbox', { name: 'offset.label' }))
        await user.type(screen.getByRole('textbox', { name: 'offset.label' }), '80')
        await user.type(screen.getByRole('textbox', { name: 'limitParameter.label' }), 'limit')
        await user.type(screen.getByRole('textbox', { name: 'limit.label' }), '20')
        await user.type(screen.getByRole('textbox', { name: 'countPath.label' }), '$.meta.count')

        expect(getApiCalls()?.[0].pagination).toEqual(LIMIT_OFFSET_PAGINATION_FORM)
      })

      it('marks only the offset parameter as required', async () => {
        const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })

        await openPagination(user, 'type.limitOffset')

        expect(screen.getByRole('textbox', { name: 'offsetParameter.label *' })).toBeInTheDocument()
        expect(screen.getByRole('textbox', { name: 'offset.label' })).toBeInTheDocument()
        expect(screen.getByRole('textbox', { name: 'limitParameter.label' })).toBeInTheDocument()
        expect(screen.getByRole('textbox', { name: 'limit.label' })).toBeInTheDocument()
        expect(screen.getByRole('textbox', { name: 'countPath.label' })).toBeInTheDocument()
      })

      it.each([
        ['offset.label', '-1', 'pageNumberInteger'],
        ['limit.label', '0', 'limitInteger'],
        ['countPath.label', 'meta.count', 'invalidCountPath'],
      ])('shows the error of an invalid value in %s', async (label, value, error) => {
        const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
        await openPagination(user, 'type.limitOffset')
        const field = screen.getByRole('textbox', { name: label })

        await user.clear(field)
        await user.type(field, value)

        await waitFor(() => expect(field).toHaveAccessibleDescription(`datasources.errors.${error}`))
      })

      it('shows and clears the error when a query parameter gets and loses the offset parameter name', async () => {
        const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
        await openPagination(user, 'type.limitOffset')
        const offsetParameter = screen.getByRole('textbox', { name: /^offsetParameter\.label/ })
        await user.type(offsetParameter, 'offset')
        const queryParameterName = screen.getByRole('textbox', { name: 'parameters.query name' })

        await user.clear(queryParameterName)
        await user.type(queryParameterName, 'offset')
        await waitFor(() =>
          expect(offsetParameter).toHaveAccessibleDescription('datasources.errors.paginationQueryDuplicate'),
        )
        expect(queryParameterName).toHaveAccessibleDescription('datasources.errors.parameterPaginationDuplicate')

        await user.type(queryParameterName, 's')
        await waitFor(() => expect(offsetParameter).toHaveAccessibleDescription(''))
        expect(queryParameterName).toHaveAccessibleDescription('')
      })

      it('disables the limit/offset fields when read-only', async () => {
        const { user } = renderApiCallsTab({
          apiCalls: [{ ...API_CALL, pagination: LIMIT_OFFSET_PAGINATION_FORM }],
          isReadOnly: true,
        })
        await openApiCall(user)

        const labels = [
          /^offsetParameter\.label/,
          'offset.label',
          'limitParameter.label',
          'limit.label',
          'countPath.label',
        ]
        labels.forEach(name => {
          const field = screen.getByRole('textbox', { name })
          expect(field).toBeDisabled()
          expect(field).toHaveClass(...DISABLED_INPUT_CLASSES)
        })
      })
    })

    it('collapses and expands the pagination section', async () => {
      const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
      await openApiCall(user)
      const paginationSection = screen.getByRole('button', { name: 'title' })

      await user.click(paginationSection)
      expect(screen.queryByRole('checkbox', { name: 'enable' })).not.toBeInTheDocument()

      await user.click(paginationSection)
      expect(screen.getByRole('checkbox', { name: 'enable' })).toBeInTheDocument()
    })
  })

  describe('collapse all', () => {
    it('collapses and expands request and pagination together', async () => {
      const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
      await openApiCall(user)

      await user.click(screen.getByRole('button', { name: 'collapseAll' }))
      expect(screen.queryByText('request.question')).not.toBeInTheDocument()
      expect(screen.queryByRole('checkbox', { name: 'enable' })).not.toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: 'expandAll' }))
      expect(screen.getByText('request.question')).toBeInTheDocument()
      expect(screen.getByRole('checkbox', { name: 'enable' })).toBeInTheDocument()
    })

    it('collapses all when only one section is open', async () => {
      const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
      await openApiCall(user)
      await user.click(screen.getByRole('button', { name: /^request\.title/ }))

      await user.click(screen.getByRole('button', { name: 'collapseAll' }))

      expect(screen.queryByRole('checkbox', { name: 'enable' })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'expandAll' })).toBeInTheDocument()
    })

    it('opens all sections when the edit view opens again', async () => {
      const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
      await openApiCall(user)
      await user.click(screen.getByRole('button', { name: 'collapseAll' }))
      await user.click(screen.getByRole('button', { name: 'apply' }))

      await openApiCall(user)

      expect(screen.getByText('request.question')).toBeInTheDocument()
      expect(screen.getByRole('checkbox', { name: 'enable' })).toBeInTheDocument()
    })

    it.each([
      ['a form reset', 'resetForm'],
      ['mounting the tab again', 'remount'],
    ] as const)('keeps all sections collapsed after %s', async (_name, action) => {
      const rendered = renderApiCallsTab({ apiCalls: [API_CALL] })
      await openApiCall(rendered.user)
      await rendered.user.click(screen.getByRole('button', { name: 'collapseAll' }))

      rendered[action]()

      expect(screen.queryByText('request.question')).not.toBeInTheDocument()
      expect(screen.queryByRole('checkbox', { name: 'enable' })).not.toBeInTheDocument()
    })

    it('is available when read-only', async () => {
      const { user } = renderApiCallsTab({ apiCalls: [API_CALL], isReadOnly: true })
      await openApiCall(user)

      await user.click(screen.getByRole('button', { name: 'collapseAll' }))

      expect(screen.queryByText('request.question')).not.toBeInTheDocument()
    })
  })

  it('shows the available info when released and editable', () => {
    renderApiCallsTab({ apiCalls: [API_CALL], isDatasourceReleased: true })

    expect(screen.getByText('availableInfo')).toBeInTheDocument()
  })
})
