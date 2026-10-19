import { zodResolver } from '@hookform/resolvers/zod'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { useForm, UseFormReturn } from 'react-hook-form'
import { toast } from 'sonner'
import { describe, expect, it, vi } from 'vitest'

import { Form } from '@/components/ui/form'
import { PAGINATION_FORM } from '@/test-support/paginationFixtures'
import { ApiCallSnapshots, CollapsedApiCallSections, EMPTY_PAGINATION, HttpApiCallForm } from '@/types/connectors'
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
}

const renderApiCallsTab = (options: RenderOptions = {}) => {
  const { apiCalls = [], isReadOnly = false, isDatasourceReleased = false, dataStructureName, baseUrl = '' } = options
  const defaultValues: DatasourceFormDraft = {
    id: '1',
    name: 'Test Data source',
    description: 'Description',
    dataSourceStatus: 'DRAFT',
    dataStructureVersionId: null,
    connectorType: 'HTTP',
    configuration: { baseUrl, authentication: { type: 'NONE', username: '', password: '' }, apiCalls },
  }
  let form: UseFormReturn<DatasourceFormDraft> | undefined
  let remountTab: (() => void) | undefined

  const Wrapper = () => {
    form = useForm<DatasourceFormDraft>({
      resolver: zodResolver(DatasourceFormDraftSchema),
      mode: 'onChange',
      defaultValues,
    })
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
  const isDirty = () => form?.formState.isDirty
  const resetForm = () => act(() => form?.reset(defaultValues))
  const remount = () => act(() => remountTab?.())
  return { user: userEvent.setup(), getApiCalls, isDirty, resetForm, remount }
}

const openApiCall = async (user: ReturnType<typeof userEvent.setup>) => {
  await user.click(screen.getByRole('button', { name: /stations/ }))
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
    expect(screen.queryByRole('button', { name: 'parameters.addQuery' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'parameters.addHeader' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /remove$/ })).not.toBeInTheDocument()
  })

  describe('pagination', () => {
    it('shows the fields with start page 0 when checked', async () => {
      const { user, getApiCalls } = renderApiCallsTab({ apiCalls: [API_CALL] })
      await openApiCall(user)
      expect(screen.queryByRole('textbox', { name: /^pageParameter\.label/ })).not.toBeInTheDocument()

      await user.click(screen.getByRole('checkbox', { name: 'enable' }))

      expect(screen.getByRole('combobox', { name: 'type.label' })).toBeDisabled()
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

    it('shows the page size error', async () => {
      const { user } = renderApiCallsTab({ apiCalls: [API_CALL] })
      await openApiCall(user)
      await user.click(screen.getByRole('checkbox', { name: 'enable' }))

      await user.type(screen.getByRole('textbox', { name: /^pageSize\.label/ }), '0')

      expect(await screen.findByText('datasources.errors.pageSizeMin')).toBeInTheDocument()
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
      expect(screen.getByRole('textbox', { name: /^pageParameter\.label/ })).toBeDisabled()
      expect(screen.getByRole('textbox', { name: 'endPage.label' })).toBeDisabled()
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
