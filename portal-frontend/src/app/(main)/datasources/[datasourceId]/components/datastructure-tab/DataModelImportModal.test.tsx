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
      },
      {
        id: 'v-2',
        version: '2.0',
        description: 'Draft version',
        dataStructureVersionStatus: 'DRAFT' as const,
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
    mockUseGetDatastructures.mockReturnValue({
      data: { data: [mixedDatastructures[0]], totalElements: 1 },
      isFetching: false,
    })
    render(<DataModelImportModal {...defaultProps} />)

    fireEvent.click(screen.getByTestId('expanderCell').querySelector('button')!)
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select datastructure Version 1.0' }))
    fireEvent.click(screen.getByTestId('confirmButton'))

    expect(mockOnSelectVersion).toHaveBeenCalledWith(
      { 'ds-1/v-1': true },
      { datastructureId: 'ds-1', name: 'Available DS', version: '1.0' },
    )
  })

  it('calls onSelectVersion with empty selection when a saved version is removed', () => {
    mockUseGetDatastructures.mockReturnValue({
      data: { data: [mixedDatastructures[0]], totalElements: 1 },
      isFetching: false,
    })
    render(<DataModelImportModal {...defaultProps} selectedVersion="ds-1/v-1" />)

    fireEvent.click(screen.getByTestId('expanderCell').querySelector('button')!)
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select datastructure Version 1.0' }))
    fireEvent.click(screen.getByTestId('confirmButton'))

    const [selection, selectedVersion] = mockOnSelectVersion.mock.calls[0]
    expect(selection).toEqual({})
    expect(selectedVersion).toBeUndefined()
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

  it('disables confirm button when selection is unchanged from the saved version', () => {
    mockUseGetDatastructures.mockReturnValue({
      data: { data: [mixedDatastructures[0]], totalElements: 1 },
      isFetching: false,
    })
    render(<DataModelImportModal {...defaultProps} selectedVersion="ds-1/v-1" />)
    expect(screen.getByTestId('confirmButton')).toBeDisabled()
  })

  it('disables confirm button when nothing is selected and no version is saved', () => {
    render(<DataModelImportModal {...defaultProps} />)
    expect(screen.getByTestId('confirmButton')).toBeDisabled()
  })

  it('disables confirm button when selection is empty and canRemoveSelection is false', () => {
    mockUseGetDatastructures.mockReturnValue({
      data: { data: [mixedDatastructures[0]], totalElements: 1 },
      isFetching: false,
    })
    render(<DataModelImportModal {...defaultProps} selectedVersion="ds-1/v-1" canRemoveSelection={false} />)

    fireEvent.click(screen.getByTestId('expanderCell').querySelector('button')!)
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select datastructure Version 1.0' }))

    expect(screen.getByTestId('confirmButton')).toBeDisabled()
  })

  it('offers draft versions alongside available ones, so a draft consumer can reference one', () => {
    mockUseGetDatastructures.mockReturnValue({
      data: { data: [mixedDatastructures[0]], totalElements: 1 },
      isFetching: false,
    })
    render(<DataModelImportModal {...defaultProps} />)

    fireEvent.click(screen.getByTestId('expanderCell').querySelector('button')!)

    expect(screen.getByRole('checkbox', { name: 'Select datastructure Version 1.0' })).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Select datastructure Version 2.0' })).toBeInTheDocument()
  })

  it('does not narrow the request to available data structures', () => {
    render(<DataModelImportModal {...defaultProps} />)

    const calledParams = mockGetApiRequestParams.mock.results[0].value as URLSearchParams
    expect(calledParams.get('dataStructureStatus')).toBeNull()
  })

  it('hides a version that has no stored model, so nothing model-less can be imported', () => {
    const withModelLessVersion = {
      ...mixedDatastructures[0],
      dataStructureVersions: [
        ...mixedDatastructures[0].dataStructureVersions,
        {
          id: 'v-4',
          version: null,
          description: 'Draft without a stored model',
          dataStructureVersionStatus: 'DRAFT' as const,
          dataStructureVersionSource: null,
        },
      ],
    }
    mockUseGetDatastructures.mockReturnValue({
      data: { data: [withModelLessVersion], totalElements: 1 },
      isFetching: false,
    })
    render(<DataModelImportModal {...defaultProps} />)

    fireEvent.click(screen.getByTestId('expanderCell').querySelector('button')!)

    expect(screen.getByRole('checkbox', { name: 'Select datastructure Version 1.0' })).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Select datastructure Version 2.0' })).toBeInTheDocument()
    expect(screen.queryByRole('checkbox', { name: 'Select datastructure -' })).toBeNull()
  })

  it('offers a draft data structure', () => {
    mockUseGetDatastructures.mockReturnValue({
      data: { data: [mixedDatastructures[1]], totalElements: 1 },
      isFetching: false,
    })
    render(<DataModelImportModal {...defaultProps} />)

    expect(screen.getByText('Draft DS')).toBeInTheDocument()
  })
})
