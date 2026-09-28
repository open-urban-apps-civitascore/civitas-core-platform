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
  connectorType: 'MQTT',
  configuration: {
    urls: ['mqtt://localhost:1883'],
    topics: ['sensors'],
    qos: 0,
    protocol_version: '3',
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
    expect(payload).not.toHaveProperty('connectorType')
    expect(payload).not.toHaveProperty('configuration')
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
        configuration: expect.objectContaining({ topics: ['other-topic'] }),
        dataStructureVersionId: 'version-2',
      }),
    )
  })
})
