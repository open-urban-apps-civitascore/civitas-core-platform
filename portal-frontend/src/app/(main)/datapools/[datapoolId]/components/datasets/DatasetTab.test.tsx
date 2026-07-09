import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetDatasets } from '@/app/services/api/datasets/clientRequests'
import { usePermissions } from '@/hooks/use-permissions'
import { Dataset } from '@/types/datasets'

import { DatasetTab } from './DatasetTab'

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

vi.mock('@/app/services/api/datasets/clientRequests', () => ({
  useGetDatasets: vi.fn(),
}))

vi.mock('@/hooks/use-permissions', () => ({
  usePermissions: vi.fn(),
}))

const mockCanCreateDataset = (canCreate: boolean) => {
  vi.mocked(usePermissions).mockReturnValue({
    hasPermission: vi.fn(),
    hasPermissionInScope: vi.fn(),
    hasAnyPermission: vi.fn(),
    hasScopedPermission: vi.fn().mockReturnValue(canCreate),
  })
}

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

const makeDataset = (overrides: Partial<Dataset> = {}): Dataset => ({
  id: 'ds-1',
  name: 'Test Dataset',
  description: 'A description',
  dataSetStatus: 'DRAFT',
  openDataAccess: false,
  pipelines: [],
  createdAt: '2024-01-01T00:00:00Z',
  modifiedAt: '2024-06-01T00:00:00Z',
  createdBy: { id: 'u1', name: 'Max Mustermann' },
  datapool: { id: 'dp1', name: 'Datapool 1' },
  ...overrides,
})

const mockDatasets = (datasets: Dataset[], totalElements = datasets.length) => {
  vi.mocked(useGetDatasets).mockReturnValue({
    data: { data: datasets, totalElements, totalPages: 1, pageIndex: 0, pageSize: 10 },
    isLoading: false,
  } as unknown as ReturnType<typeof useGetDatasets>)
}

const renderComponent = (datapoolId = 'dp1', isReadOnly = false) =>
  render(<DatasetTab datapoolId={datapoolId} isReadOnly={isReadOnly} />)

describe('DatasetTab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSearch = ''
    mockDatasets([])
    mockCanCreateDataset(true)
  })

  describe('Rendering', () => {
    it('renders the section title', () => {
      renderComponent()
      expect(screen.getByText('title')).toBeInTheDocument()
    })

    it('renders the search field when datasets are present', () => {
      mockDatasets([makeDataset()])
      renderComponent()
      expect(screen.getByRole('search')).toBeInTheDocument()
    })

    it('renders the Add Dataset button when datasets are present', () => {
      mockDatasets([makeDataset()])
      renderComponent()
      expect(screen.getByRole('button', { name: 'addDataset' })).toBeInTheDocument()
    })

    it('renders the dataset table when datasets are present', () => {
      mockDatasets([makeDataset()])
      renderComponent()
      expect(screen.getByTestId('datasetsTable')).toBeInTheDocument()
    })
  })

  describe('Loading state', () => {
    it('shows loading skeleton while datasets are loading', () => {
      vi.mocked(useGetDatasets).mockReturnValue({
        data: undefined,
        isLoading: true,
      } as unknown as ReturnType<typeof useGetDatasets>)

      renderComponent()
      expect(screen.getByTestId('loadingSkeleton')).toBeInTheDocument()
    })
  })

  describe('Dataset data', () => {
    it('renders dataset rows when data is available', () => {
      mockDatasets([makeDataset(), makeDataset({ id: 'ds-2', name: 'Second Dataset' })])
      renderComponent()
      expect(screen.getByText('Test Dataset')).toBeInTheDocument()
      expect(screen.getByText('Second Dataset')).toBeInTheDocument()
    })

    it('renders a link to the dataset detail page for each dataset name', () => {
      mockDatasets([makeDataset()])
      renderComponent()
      const link = screen.getByRole('link', { name: 'Test Dataset' })
      expect(link).toHaveAttribute('href', '/datasets/ds-1')
    })

    it('renders the creator name in the contact cell', () => {
      mockDatasets([makeDataset()])
      renderComponent()
      expect(screen.getByText('Max Mustermann')).toBeInTheDocument()
    })

    it('renders the dataset status', () => {
      mockDatasets([makeDataset({ dataSetStatus: 'AVAILABLE' })])
      renderComponent()
      expect(screen.getByText('AVAILABLE')).toBeInTheDocument()
    })

    it('shows the NoDataPage when no datasets are returned and no search is active', () => {
      mockDatasets([])
      renderComponent()
      expect(screen.getByText('noDatasets')).toBeInTheDocument()
      expect(screen.getByText('noDatasetsSubTitle')).toBeInTheDocument()
    })

    it('hides the table and search when no datasets are returned and no search is active', () => {
      mockDatasets([])
      renderComponent()
      expect(screen.queryByTestId('datasetsTable')).not.toBeInTheDocument()
      expect(screen.queryByRole('search')).not.toBeInTheDocument()
    })

    it('shows the table instead of NoDataPage when no results but a search is active', () => {
      mockSearch = 'something'
      mockDatasets([])
      renderComponent()
      expect(screen.queryByText('noDatasets')).not.toBeInTheDocument()
      expect(screen.getByTestId('datasetsTable')).toBeInTheDocument()
    })

    it('does not show the NoDataPage while loading', () => {
      vi.mocked(useGetDatasets).mockReturnValue({
        data: undefined,
        isLoading: true,
      } as unknown as ReturnType<typeof useGetDatasets>)

      renderComponent()
      expect(screen.queryByText('noDatasets')).not.toBeInTheDocument()
    })
  })

  describe('API call', () => {
    it('passes the datapoolId as a filter parameter to useGetDatasets', () => {
      renderComponent('my-datapool-id')
      const calls = vi.mocked(useGetDatasets).mock.calls
      const params: URLSearchParams = calls[calls.length - 1][0]?.params as URLSearchParams
      expect(params?.get('datapoolIds')).toBe('my-datapool-id')
    })

    it('updates totalPages when rowCount changes', async () => {
      mockDatasets([makeDataset(), makeDataset({ id: 'ds-2', name: 'Second Dataset' })], 2)
      renderComponent()
      await waitFor(() => {
        expect(mockSetTotalPages).toHaveBeenCalledWith(1)
      })
    })
  })

  describe('isReadOnly prop', () => {
    it('does not render the Add Dataset button in the toolbar when isReadOnly is true', () => {
      mockDatasets([makeDataset()])
      renderComponent('dp1', true)
      expect(screen.queryByRole('button', { name: 'addDataset' })).not.toBeInTheDocument()
    })

    it('renders and enables the Add Dataset button in the toolbar when isReadOnly is false', () => {
      mockDatasets([makeDataset()])
      renderComponent('dp1', false)
      expect(screen.getByRole('button', { name: 'addDataset' })).not.toBeDisabled()
    })

    it('does not render the Add Dataset button on the NoDataPage when isReadOnly is true', () => {
      mockDatasets([])
      renderComponent('dp1', true)
      expect(screen.queryByRole('button', { name: 'addDataset' })).not.toBeInTheDocument()
    })

    it('renders and enables the Add Dataset button on the NoDataPage when isReadOnly is false', () => {
      mockDatasets([])
      renderComponent('dp1', false)
      expect(screen.getByRole('button', { name: 'addDataset' })).not.toBeDisabled()
    })

    it('does not render the Add Dataset button when user lacks DATASET_CREATE permission on the datapool', () => {
      mockCanCreateDataset(false)
      mockDatasets([makeDataset()])
      renderComponent('dp1', false)
      expect(screen.queryByRole('button', { name: 'addDataset' })).not.toBeInTheDocument()
    })
  })

  describe('Add Dataset navigation', () => {
    it('renders a GuardedLink with the correct href in the toolbar', () => {
      mockDatasets([makeDataset()])
      renderComponent('dp1')
      const link = screen.getByRole('link', { name: 'addDataset' })
      expect(link).toHaveAttribute('href', '/datasets/create?datapoolId=dp1&source=datapools')
    })

    it('renders a GuardedLink with the correct href on the NoDataPage', () => {
      mockDatasets([])
      renderComponent('dp1')
      const link = screen.getByRole('link', { name: 'addDataset' })
      expect(link).toHaveAttribute('href', '/datasets/create?datapoolId=dp1&source=datapools')
    })
  })

  describe('Search', () => {
    it('calls setSearchParam when text is typed into the search field', async () => {
      const user = userEvent.setup()
      mockDatasets([makeDataset()])
      renderComponent()
      const searchInput = screen.getByRole('searchbox')
      await user.type(searchInput, 'my dataset')
      await waitFor(() => {
        expect(mockSetSearchParam).toHaveBeenCalledWith('my dataset')
      })
    })
  })
})
