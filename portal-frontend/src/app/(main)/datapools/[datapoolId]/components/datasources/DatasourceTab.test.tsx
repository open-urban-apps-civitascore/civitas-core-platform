import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { mockDatasources } from '@/__mocks__/datasources/datasources.mock'
import { useGetDatasources } from '@/app/services/api/datasources/clientRequests'
import { Datasource } from '@/types/datasources'

import { DatasourceTab } from './DatasourceTab'

const mockSetTotalPages = vi.fn()
const mockSetSearchParam = vi.fn()
const mockSetPaginationParams = vi.fn()
const mockSetSortingParams = vi.fn()

vi.mock('next/navigation', () => ({
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/datapools/dp1',
}))

vi.mock('@/contexts/unsaved-changes/UnsavedChangesContext', () => ({
  useUnsavedChanges: () => ({
    hasUnsavedChanges: false,
    requestNavigation: vi.fn(),
  }),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
  useLocale: () => 'en',
}))

vi.mock('@/app/services/api/datasources/clientRequests', () => ({
  useGetDatasources: vi.fn(),
}))

let mockSearch = ''

vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: () => ({
    pageIndex: 0,
    pageSize: 10,
    sorting: [],
    search: mockSearch,
    totalPages: 1,
    setTotalPages: mockSetTotalPages,
    setPaginationParams: mockSetPaginationParams,
    setSortingParams: mockSetSortingParams,
    setSearchParam: mockSetSearchParam,
    getApiRequestParamsByUrl: vi.fn(() => new URLSearchParams()),
  }),
}))

const mockResponse = (datasources: Datasource[], totalElements = datasources.length) => {
  vi.mocked(useGetDatasources).mockReturnValue({
    data: { data: datasources, totalElements, totalPages: 1, pageIndex: 0, pageSize: 10 },
    isLoading: false,
  } as unknown as ReturnType<typeof useGetDatasources>)
}

const renderComponent = (datapoolId = 'dp1', isCreateMode = false) =>
  render(<DatasourceTab datapoolId={datapoolId} isCreateMode={isCreateMode} />)

describe('DatasourceTab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSearch = ''
    mockResponse([])
  })

  describe('empty state', () => {
    it('shows NoDataPage when no datasources are returned and no search is active', () => {
      renderComponent()
      expect(screen.getByText('noDatasources')).toBeInTheDocument()
      expect(screen.getByText('noDatasourcesSubTitle')).toBeInTheDocument()
    })

    it('hides table and search when no datasources are returned and no search is active', () => {
      renderComponent()
      expect(screen.queryByTestId('datapoolDatasourcesTable')).not.toBeInTheDocument()
      expect(screen.queryByRole('search')).not.toBeInTheDocument()
    })

    it('shows the table instead of NoDataPage when no results but a search is active', () => {
      mockSearch = 'sensor'
      renderComponent()
      expect(screen.queryByText('noDatasources')).not.toBeInTheDocument()
      expect(screen.getByTestId('datapoolDatasourcesTable')).toBeInTheDocument()
    })

    it('shows NoDataPage in create mode', () => {
      renderComponent('dp1', true)
      expect(screen.getByText('noDatasources')).toBeInTheDocument()
    })
  })

  describe('with datasources', () => {
    it('renders the table when datasources are present', () => {
      mockResponse(mockDatasources)
      renderComponent()
      expect(screen.getByTestId('datapoolDatasourcesTable')).toBeInTheDocument()
    })

    it('renders the search field when datasources are present', () => {
      mockResponse(mockDatasources)
      renderComponent()
      expect(screen.getByRole('search')).toBeInTheDocument()
    })

    it('does not show NoDataPage while loading', () => {
      vi.mocked(useGetDatasources).mockReturnValue({
        data: undefined,
        isLoading: true,
      } as unknown as ReturnType<typeof useGetDatasources>)
      renderComponent()
      expect(screen.queryByText('noDatasources')).not.toBeInTheDocument()
    })

    it('renders datasource names in the table', () => {
      mockResponse(mockDatasources)
      renderComponent()
      expect(screen.getByText('MQTT Sensor Data')).toBeInTheDocument()
      expect(screen.getByText('SQL Database Import')).toBeInTheDocument()
    })

    it('renders datasource name as a link to the detail page', () => {
      mockResponse(mockDatasources)
      renderComponent()
      const link = screen.getByRole('link', { name: 'MQTT Sensor Data' })
      expect(link).toHaveAttribute('href', '/datasources/00000000-0000-0000-0000-000000000001')
    })
  })

  describe('API call', () => {
    it('passes datapoolId as filter parameter to useGetDatasources', () => {
      renderComponent('my-datapool-id')
      const calls = vi.mocked(useGetDatasources).mock.calls
      const params = calls[calls.length - 1][0]?.params as URLSearchParams
      expect(params?.get('datapoolId')).toBe('my-datapool-id')
    })

    it('updates totalPages when rowCount changes', async () => {
      mockResponse(mockDatasources, mockDatasources.length)
      renderComponent()
      await waitFor(() => {
        expect(mockSetTotalPages).toHaveBeenCalledWith(1)
      })
    })
  })

  describe('search', () => {
    it('calls setSearchParam when text is typed into the search field', async () => {
      const user = userEvent.setup()
      mockResponse(mockDatasources)
      renderComponent()
      await user.type(screen.getByRole('searchbox'), 'sensor')
      await waitFor(() => {
        expect(mockSetSearchParam).toHaveBeenCalledWith('sensor')
      })
    })
  })
})
