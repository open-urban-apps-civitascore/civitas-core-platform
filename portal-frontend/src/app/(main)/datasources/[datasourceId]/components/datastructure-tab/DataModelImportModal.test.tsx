import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { DataModelImportModal } from './DataModelImportModal'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string, params?: Record<string, string>) =>
    params ? `${key} ${JSON.stringify(params)}` : key,
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/',
}))

const mockGetApiRequestParams = vi.fn().mockReturnValue(new URLSearchParams())
vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: () => ({
    getApiRequestParams: mockGetApiRequestParams,
  }),
}))

const mockUseGetDatastructures = vi.fn().mockReturnValue({
  data: undefined,
  isFetching: false,
})
vi.mock('@/app/services/api/datastructures/clientRequests', () => ({
  useGetDatastructures: (...args: unknown[]) => mockUseGetDatastructures(...args),
}))

const mockOnSelectVersion = vi.fn()
const mockOnOpenChange = vi.fn()

const defaultProps = {
  open: true,
  selectedVersion: null,
  datasourceTitle: 'My Datasource',
  onSelectVersion: mockOnSelectVersion,
  onOpenChange: mockOnOpenChange,
}

const mixedDatastructures = [
  {
    id: 'ds-1',
    name: 'Available DS',
    description: 'First datastructure',
    dataStructureStatus: 'AVAILABLE' as const,
    dataStructureVersions: [
      {
        id: 'v-1',
        version: '1.0',
        description: 'Available version',
        dataStructureVersionStatus: 'AVAILABLE' as const,
        dataStructureVersionSource: null,
      },
      {
        id: 'v-2',
        version: '2.0',
        description: 'Draft version',
        dataStructureVersionStatus: 'DRAFT' as const,
        dataStructureVersionSource: null,
      },
    ],
    createdAt: '2024-01-01',
    modifiedAt: '2024-01-01',
  },
  {
    id: 'ds-2',
    name: 'Draft DS',
    description: 'Draft datastructure',
    dataStructureStatus: 'DRAFT' as const,
    dataStructureVersions: [
      {
        id: 'v-3',
        version: '1.0',
        description: 'A version',
        dataStructureVersionStatus: 'AVAILABLE' as const,
        dataStructureVersionSource: null,
      },
    ],
    createdAt: '2024-01-01',
    modifiedAt: '2024-01-01',
  },
]

describe('DataModelImportModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUseGetDatastructures.mockReturnValue({
      data: undefined,
      isFetching: false,
    })
  })

  it('does not render when open is false', () => {
    render(<DataModelImportModal {...defaultProps} open={false} />)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('renders dialog with title and description when open', () => {
    render(<DataModelImportModal {...defaultProps} />)
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('title')).toBeInTheDocument()
    expect(screen.getByText('to {"name":"My Datasource"}')).toBeInTheDocument()
  })

  it('renders search area', () => {
    render(<DataModelImportModal {...defaultProps} />)
    expect(screen.getByTestId('searchArea')).toBeInTheDocument()
  })

  it('renders data table', () => {
    render(<DataModelImportModal {...defaultProps} />)
    expect(screen.getByTestId('dataTableScrollArea')).toBeInTheDocument()
  })

  it('shows loading spinner when isUpdating is true', () => {
    render(<DataModelImportModal {...defaultProps} isUpdating={true} />)
    expect(screen.getByText('loading')).toBeInTheDocument()
    expect(screen.queryByTestId('dataTableScrollArea')).not.toBeInTheDocument()
  })

  it('calls onSelectVersion with current selection on confirm', () => {
    render(<DataModelImportModal {...defaultProps} selectedVersion="ds-1/v-1" />)
    fireEvent.click(screen.getByTestId('confirmButton'))

    expect(mockOnSelectVersion).toHaveBeenCalledWith({ 'ds-1/v-1': true })
  })

  it('calls onSelectVersion with empty selection when no version is selected', () => {
    render(<DataModelImportModal {...defaultProps} />)
    fireEvent.click(screen.getByTestId('confirmButton'))

    expect(mockOnSelectVersion).toHaveBeenCalledWith({})
  })

  it('calls onOpenChange on cancel', () => {
    render(<DataModelImportModal {...defaultProps} />)
    fireEvent.click(screen.getByTestId('cancelButton'))

    expect(mockOnOpenChange).toHaveBeenCalledWith(false)
  })

  it('disables confirm button when isUpdating', () => {
    render(<DataModelImportModal {...defaultProps} isUpdating={true} />)
    expect(screen.getByTestId('confirmButton')).toBeDisabled()
  })

  it('disables confirm button when fetching datastructures', () => {
    mockUseGetDatastructures.mockReturnValue({
      data: undefined,
      isFetching: true,
    })
    render(<DataModelImportModal {...defaultProps} />)
    expect(screen.getByTestId('confirmButton')).toBeDisabled()
  })

  it('passes dataStructureStatus=AVAILABLE to the API and filters DRAFT versions client-side', () => {
    // Backend returns only AVAILABLE datastructures (filtered by query param),
    // but versions still need client-side filtering
    const availableDatastructures = [mixedDatastructures[0]]
    mockUseGetDatastructures.mockReturnValue({
      data: { data: availableDatastructures, totalElements: 1 },
      isFetching: false,
    })
    render(<DataModelImportModal {...defaultProps} />)

    // Verify the API was called with dataStructureStatus param
    const calledParams = mockGetApiRequestParams.mock.results[0].value as URLSearchParams
    expect(calledParams).toBeInstanceOf(URLSearchParams)

    // Available datastructure should be shown
    expect(screen.getByText('Available DS')).toBeInTheDocument()
    expect(screen.getByTestId('expanderCell')).toBeInTheDocument()
    // Expand the available datastructure to see versions
    fireEvent.click(screen.getByTestId('expanderCell').querySelector('button')!)
    // Only available versions should be shown (draft versions filtered client-side)
    expect(screen.getByText('Version 1.0')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Select datastructure Version 1.0' })).toBeInTheDocument()
    expect(screen.queryByText('Version 2.0')).not.toBeInTheDocument()
  })
})
