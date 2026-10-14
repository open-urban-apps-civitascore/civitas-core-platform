import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { DATAPOOL_SCOPE_TYPES, Datasource, DatasourceStatusType } from '@/types/datasources'

import { useDatasourceForm } from './useDatasourceForm'

const mockUpdateDatasource = vi.fn()
const mockUpdateDatasourceReleased = vi.fn()

vi.mock('@/app/services/api/datasources/clientRequests', () => ({
  useUpdateDatasource: () => ({ mutateAsync: mockUpdateDatasource, isPending: false }),
  useUpdateDatasourceReleased: () => ({ mutateAsync: mockUpdateDatasourceReleased, isPending: false }),
  useReleaseDatasource: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUnreleaseDatasource: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn(), info: vi.fn() },
}))

const createDatasource = (dataSourceStatus: DatasourceStatusType): Datasource => ({
  id: 'ds-1',
  createdAt: '2024-01-01',
  modifiedAt: '2024-01-01',
  name: 'Test Datasource',
  description: 'A test datasource',
  dataSourceStatus,
  connector: {
    connectorType: 'MQTT',
    urls: ['mqtt://localhost:1883'],
    topics: ['sensors'],
    qos: 0,
    protocol_version: '3',
    connect_timeout: null,
    keepalive: null,
    tls: { enabled: false },
    user: 'mqtt-user',
    password: '********',
  },
  dataStructureVersion: {
    id: 'version-1',
    version: '1.0.0',
    description: null,
    dataStructureVersionStatus: 'AVAILABLE',
    dataStructureVersionSource: 'OWN',
    createdAt: '2024-01-01',
    modifiedAt: '2024-01-01',
    dataStructureId: 'structure-1',
  },
  inUse: false,
  inUseByReleased: false,
  datapoolScope: { type: DATAPOOL_SCOPE_TYPES.NONE },
})

const renderDatasourceForm = (datasource: Datasource) => renderHook(() => useDatasourceForm(datasource, [], [], [], []))

const editTechnicalFieldsAndDescription = (result: ReturnType<typeof renderDatasourceForm>['result']) => {
  act(() => {
    const { form } = result.current
    form.setValue('description', 'Changed description', { shouldDirty: true })
    form.setValue('configuration.topics', 'other-topic', { shouldDirty: true })
    form.setValue('dataStructureVersionId', 'version-2', { shouldDirty: true })
  })
}

const createHttpDatasource = (): Datasource => ({
  ...createDatasource('DRAFT'),
  connector: {
    connectorType: 'HTTP',
    baseUrl: 'https://api.example.org/v1',
    authentication: { type: 'BASIC', username: 'reader', password: '********' },
    apiCalls: [{ name: 'Get all stations', method: 'GET', path: '/stations' }],
  },
})

describe('useDatasourceForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('sends only metadata to the released endpoint for an AVAILABLE data source', async () => {
    const datasource = createDatasource('AVAILABLE')
    mockUpdateDatasourceReleased.mockResolvedValue({ data: datasource })
    const { result } = renderDatasourceForm(datasource)
    editTechnicalFieldsAndDescription(result)

    await act(async () => {
      await result.current.submitDatasource()
    })

    expect(mockUpdateDatasource).not.toHaveBeenCalled()
    expect(mockUpdateDatasourceReleased).toHaveBeenCalledTimes(1)
    const payload = mockUpdateDatasourceReleased.mock.calls[0][0]
    expect(payload).toMatchObject({ id: 'ds-1', name: 'Test Datasource', description: 'Changed description' })
    expect(payload).not.toHaveProperty('connector')
    expect(payload).not.toHaveProperty('dataStructureVersionId')
  })

  it('sends technical fields to the draft endpoint for a DRAFT data source', async () => {
    const datasource = createDatasource('DRAFT')
    mockUpdateDatasource.mockResolvedValue({ data: datasource })
    const { result } = renderDatasourceForm(datasource)
    editTechnicalFieldsAndDescription(result)

    await act(async () => {
      await result.current.submitDatasource()
    })

    expect(mockUpdateDatasourceReleased).not.toHaveBeenCalled()
    expect(mockUpdateDatasource).toHaveBeenCalledWith(
      expect.objectContaining({
        description: 'Changed description',
        connector: expect.objectContaining({ connectorType: 'MQTT', topics: ['other-topic'] }),
        dataStructureVersionId: 'version-2',
      }),
    )
  })

  it('sends every field of the connector in API format when one connector field changed', async () => {
    const datasource = createDatasource('DRAFT')
    mockUpdateDatasource.mockResolvedValue({ data: datasource })
    const { result } = renderDatasourceForm(datasource)
    act(() => {
      result.current.form.setValue('configuration.topics', 'other-topic', { shouldDirty: true })
    })

    await act(async () => {
      await result.current.submitDatasource()
    })

    const payload = mockUpdateDatasource.mock.calls[0][0]
    expect(payload.connector).toEqual({
      connectorType: 'MQTT',
      urls: ['mqtt://localhost:1883'],
      topics: ['other-topic'],
      qos: 0,
      protocol_version: '3',
      tls: { enabled: false },
      user: 'mqtt-user',
      password: '********',
    })
    expect(payload).not.toHaveProperty('connectorType')
    expect(payload).not.toHaveProperty('configuration')
  })

  it('sends a cleared connector field as blank so the backend clears it', async () => {
    const datasource = createDatasource('DRAFT')
    mockUpdateDatasource.mockResolvedValue({ data: datasource })
    const { result } = renderDatasourceForm(datasource)
    act(() => {
      result.current.form.setValue('configuration.user', '', { shouldDirty: true })
    })

    await act(async () => {
      await result.current.submitDatasource()
    })

    expect(mockUpdateDatasource.mock.calls[0][0].connector).toMatchObject({ connectorType: 'MQTT', user: '' })
  })

  it('sends the full field set of the new type when the connector type changes', async () => {
    const datasource = createDatasource('DRAFT')
    mockUpdateDatasource.mockResolvedValue({ data: datasource })
    const { result } = renderDatasourceForm(datasource)
    act(() => {
      result.current.form.setValue('connectorType', 'SQL', { shouldDirty: true })
    })

    await act(async () => {
      await result.current.submitDatasource()
    })

    expect(mockUpdateDatasource.mock.calls[0][0].connector).toEqual({
      connectorType: 'SQL',
      driver: 'postgres',
      dsn: '',
      user: '',
      password: '',
      table: '',
      columns: ['*'],
      where: '',
    })
  })

  it('does not send the connector when no connector field changed', async () => {
    const datasource = createDatasource('DRAFT')
    mockUpdateDatasource.mockResolvedValue({ data: datasource })
    const { result } = renderDatasourceForm(datasource)
    act(() => {
      result.current.form.setValue('description', 'Changed description', { shouldDirty: true })
    })

    await act(async () => {
      await result.current.submitDatasource()
    })

    expect(mockUpdateDatasource.mock.calls[0][0]).not.toHaveProperty('connector')
  })

  // The backend rejects an unknown key, so a status in the update would block the save before the release.
  it('does not send the status in the draft update when the status changes together with another field', async () => {
    const datasource = createDatasource('DRAFT')
    mockUpdateDatasource.mockResolvedValue({ data: datasource })
    const { result } = renderDatasourceForm(datasource)
    act(() => {
      result.current.form.setValue('description', 'Changed description', { shouldDirty: true })
      result.current.handleStatusChange('AVAILABLE')
    })

    await act(async () => {
      await result.current.submitDatasource()
    })

    const payload = mockUpdateDatasource.mock.calls[0][0]
    expect(payload).toMatchObject({ description: 'Changed description' })
    expect(payload).not.toHaveProperty('dataSourceStatus')
  })
})

describe('useDatasourceForm with an HTTP connector', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const submitAfter = async (
    edit: (form: ReturnType<typeof renderDatasourceForm>['result']['current']['form']) => void,
  ) => {
    const datasource = createHttpDatasource()
    mockUpdateDatasource.mockResolvedValue({ data: datasource })
    const { result } = renderDatasourceForm(datasource)
    act(() => edit(result.current.form))

    await act(async () => {
      await result.current.submitDatasource()
    })

    return mockUpdateDatasource.mock.calls[0][0].connector
  }

  it('marks the connector tab complete without API calls, but does not allow release', () => {
    const datasource = createHttpDatasource()
    const { result } = renderDatasourceForm({ ...datasource, connector: { ...datasource.connector!, apiCalls: [] } })

    expect(result.current.completedTabs).toContain('connector')
    expect(result.current.canStage).toBe(false)
  })

  it('allows release with an API call', () => {
    const { result } = renderDatasourceForm(createHttpDatasource())

    expect(result.current.completedTabs).toContain('connector')
    expect(result.current.canStage).toBe(true)
  })

  it('loads the masked password into the form', () => {
    const { result } = renderDatasourceForm(createHttpDatasource())

    expect(result.current.form.getValues('configuration')).toMatchObject({
      authentication: { type: 'BASIC', username: 'reader', password: '********' },
    })
  })

  it('sends the masked password back when the password did not change', async () => {
    const connector = await submitAfter(form =>
      form.setValue('configuration.baseUrl', 'https://api.example.org/v2', { shouldDirty: true }),
    )

    expect(connector).toEqual({
      connectorType: 'HTTP',
      baseUrl: 'https://api.example.org/v2',
      authentication: { type: 'BASIC', username: 'reader', password: '********' },
      apiCalls: [{ name: 'Get all stations', path: '/stations', queryParameters: [], headers: [], pagination: null }],
    })
  })

  it('sends a new password', async () => {
    const connector = await submitAfter(form =>
      form.setValue(
        'configuration.authentication',
        { type: 'BASIC', username: 'reader', password: 'new-secret' },
        { shouldDirty: true },
      ),
    )

    expect(connector.authentication).toEqual({ type: 'BASIC', username: 'reader', password: 'new-secret' })
  })

  it('sends only the type when the authentication changes to None', async () => {
    const connector = await submitAfter(form =>
      form.setValue(
        'configuration.authentication',
        { type: 'NONE', username: 'reader', password: '********' },
        { shouldDirty: true },
      ),
    )

    expect(connector.authentication).toEqual({ type: 'NONE' })
  })
})
