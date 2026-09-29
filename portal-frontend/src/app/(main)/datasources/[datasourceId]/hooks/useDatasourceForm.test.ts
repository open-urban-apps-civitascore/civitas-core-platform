import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { DATAPOOL_SCOPE_TYPES, Datasource, DATASOURCE_STATUS_TYPES, DatasourceStatusType } from '@/types/datasources'

import { useDatasourceForm } from './useDatasourceForm'

const mockToastInfo = vi.fn()

vi.mock('sonner', () => ({
  toast: {
    info: (...args: unknown[]) => mockToastInfo(...args),
    error: vi.fn(),
    success: vi.fn(),
  },
}))

vi.mock('next-intl', () => ({
  useTranslations: (namespace: string) => (key: string) => `${namespace}.${key}`,
}))

const idleMutation = { mutateAsync: vi.fn(), isPending: false }

vi.mock('@/app/services/api/datasources/clientRequests', () => ({
  useUpdateDatasource: () => idleMutation,
  useUpdateDatasourceReleased: () => idleMutation,
  useReleaseDatasource: () => idleMutation,
  useUnreleaseDatasource: () => idleMutation,
}))

const buildDatasource = (overrides: Partial<Datasource> = {}): Datasource => ({
  id: 'datasource-1',
  createdAt: '2025-01-01T00:00:00Z',
  modifiedAt: '2025-01-01T00:00:00Z',
  name: 'traffic-sensor',
  description: 'Traffic sensor data',
  dataSourceStatus: DATASOURCE_STATUS_TYPES.AVAILABLE,
  connectorType: 'MQTT',
  configuration: { urls: ['tcp://broker:1883'], topics: ['sensor/data'], qos: 1 },
  dataStructureVersion: null,
  inUse: false,
  inUseByReleased: false,
  datapoolScope: { type: DATAPOOL_SCOPE_TYPES.ALL },
  ...overrides,
})

const renderDatasourceForm = (datasource: Datasource) => renderHook(() => useDatasourceForm(datasource, [], [], [], []))

describe('useDatasourceForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('canSetDraft', () => {
    it('forbids setting an in-use data source back to draft and explains why', () => {
      const { result } = renderDatasourceForm(buildDatasource({ inUseByReleased: true }))

      expect(result.current.canSetDraft).toBe(false)
      expect(result.current.statusHint).toBe('datasources.messages.isInUseStatusHint')
    })

    it('allows setting a data source no pipeline uses back to draft', () => {
      const { result } = renderDatasourceForm(buildDatasource({ inUseByReleased: false }))

      expect(result.current.canSetDraft).toBe(true)
      expect(result.current.statusHint).toBeUndefined()
    })

    it('allows draft for a data source only draft pipelines use', () => {
      const { result } = renderDatasourceForm(buildDatasource({ inUse: true, inUseByReleased: false }))

      expect(result.current.canSetDraft).toBe(true)
      expect(result.current.isConnectorLocked).toBe(false)
    })

    it('allows draft for a data source that is already in draft', () => {
      const { result } = renderDatasourceForm(
        buildDatasource({ dataSourceStatus: DATASOURCE_STATUS_TYPES.DRAFT, inUseByReleased: false }),
      )

      expect(result.current.canSetDraft).toBe(true)
    })
  })

  describe('isConnectorLocked', () => {
    it.each([
      [DATASOURCE_STATUS_TYPES.AVAILABLE, true, true],
      [DATASOURCE_STATUS_TYPES.AVAILABLE, false, false],
      [DATASOURCE_STATUS_TYPES.DRAFT, true, false],
      [DATASOURCE_STATUS_TYPES.DRAFT, false, false],
    ] as const)(
      'status=%s and inUseByReleased=%s locks the connector: %s',
      (dataSourceStatus, inUseByReleased, expected) => {
        const { result } = renderDatasourceForm(
          buildDatasource({ dataSourceStatus, inUse: inUseByReleased, inUseByReleased }),
        )

        expect(result.current.isConnectorLocked).toBe(expected)
      },
    )
  })

  describe('automatic revert to draft on incomplete data', () => {
    // A data source missing a data structure version fails DatasourceFormAvailableSchema, so the
    // hook would normally switch the staged status to DRAFT.
    const incomplete = { dataStructureVersion: null, description: '' } as Partial<Datasource>

    it('reverts an AVAILABLE data source that is not in use', async () => {
      const { result } = renderDatasourceForm(buildDatasource({ ...incomplete, inUseByReleased: false }))

      await waitFor(() =>
        expect(result.current.dataSourceStatus).toBe<DatasourceStatusType>(DATASOURCE_STATUS_TYPES.DRAFT),
      )
      expect(result.current.canStage).toBe(false)
      expect(mockToastInfo).toHaveBeenCalled()
    })

    it('keeps an in-use data source on AVAILABLE, since the unrelease would be rejected', async () => {
      const { result } = renderDatasourceForm(buildDatasource({ ...incomplete, inUseByReleased: true }))

      await waitFor(() => expect(result.current.canStage).toBe(false))
      expect(result.current.dataSourceStatus).toBe<DatasourceStatusType>(DATASOURCE_STATUS_TYPES.AVAILABLE)
      expect(mockToastInfo).not.toHaveBeenCalled()
    })
  })
})
