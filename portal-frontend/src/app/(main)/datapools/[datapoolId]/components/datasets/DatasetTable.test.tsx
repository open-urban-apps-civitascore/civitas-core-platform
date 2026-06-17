import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { BaseDatasetTableData } from '@/types/datasets'

import { DatasetTable } from './DatasetTable'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
  useLocale: () => 'en',
}))

const makeRow = (overrides: Partial<BaseDatasetTableData> = {}): BaseDatasetTableData => ({
  id: 'ds-1',
  name: 'Test Dataset',
  createdBy: { id: 'u1', name: 'Max Mustermann' },
  modifiedAt: '2024-06-15T00:00:00Z',
  dataSetStatus: 'DRAFT',
  ...overrides,
})

const defaultProps = {
  datasets: [makeRow()],
  rowCount: 1,
  pageIndex: 0,
  pageSize: 10,
  totalPages: 1,
  sorting: [],
  rowSelection: {},
  isLoading: false,
  onPaginationChange: vi.fn(),
  onSortingChange: vi.fn(),
}

const renderComponent = (props: Partial<typeof defaultProps> = {}) =>
  render(<DatasetTable {...defaultProps} {...props} />)

describe('DatasetTable', () => {
  describe('Rendering', () => {
    it('renders the table', () => {
      renderComponent()
      expect(screen.getByTestId('datasetsTable')).toBeInTheDocument()
    })

    it('renders the correct column headers', () => {
      renderComponent()
      expect(screen.getByText('tableHeaders.name')).toBeInTheDocument()
      expect(screen.getByText('tableHeaders.contact')).toBeInTheDocument()
      expect(screen.getByText('tableHeaders.modifiedAt')).toBeInTheDocument()
      expect(screen.getByText('tableHeaders.status')).toBeInTheDocument()
    })

    it('does not render the id column', () => {
      renderComponent()
      expect(screen.queryByRole('columnheader', { name: 'id' })).not.toBeInTheDocument()
    })
  })

  describe('Row content', () => {
    it('renders dataset name as a link to the dataset detail page', () => {
      renderComponent()
      const link = screen.getByRole('link', { name: 'Test Dataset' })
      expect(link).toBeInTheDocument()
      expect(link).toHaveAttribute('href', '/datasets/ds-1')
    })

    it('renders a dash when the dataset name is empty', () => {
      renderComponent({ datasets: [makeRow({ name: '' })] })
      const rows = screen.getAllByRole('row')
      const [, dataRow] = rows
      expect(within(dataRow).getByText('-')).toBeInTheDocument()
    })

    it('renders the creator name in the contact cell', () => {
      renderComponent()
      expect(screen.getByText('Max Mustermann')).toBeInTheDocument()
    })

    it('renders a fallback when createdBy is null', () => {
      renderComponent({ datasets: [makeRow({ createdBy: null as unknown as BaseDatasetTableData['createdBy'] })] })
      expect(screen.getByText('noContact')).toBeInTheDocument()
    })

    it('renders the formatted modification date', () => {
      renderComponent()
      expect(screen.getByText('15/06/2024')).toBeInTheDocument()
    })

    it('renders the dataset status', () => {
      renderComponent({ datasets: [makeRow({ dataSetStatus: 'AVAILABLE' })] })
      expect(screen.getByText('AVAILABLE')).toBeInTheDocument()
    })

    it('renders multiple rows', () => {
      renderComponent({
        datasets: [makeRow(), makeRow({ id: 'ds-2', name: 'Second Dataset' })],
        rowCount: 2,
      })
      expect(screen.getByText('Test Dataset')).toBeInTheDocument()
      expect(screen.getByText('Second Dataset')).toBeInTheDocument()
    })
  })

  describe('Loading state', () => {
    it('shows the loading skeleton when datasets are empty and isLoading is true', () => {
      renderComponent({ datasets: [], rowCount: 0, isLoading: true })
      expect(screen.getByTestId('loadingSkeleton')).toBeInTheDocument()
    })

    it('does not show the loading skeleton when isLoading is false', () => {
      renderComponent({ isLoading: false })
      expect(screen.queryByTestId('loadingSkeleton')).not.toBeInTheDocument()
    })
  })

  describe('Empty state', () => {
    it('shows no results message when datasets is empty and not loading', () => {
      renderComponent({ datasets: [], rowCount: 0, isLoading: false })
      expect(screen.getByText('noResults')).toBeInTheDocument()
    })
  })

  describe('Sorting', () => {
    it('calls onSortingChange when the name column header is clicked', () => {
      const onSortingChange = vi.fn()
      renderComponent({ onSortingChange })
      const sortButton = within(screen.getByTestId('sortableTableHeader')).getByRole('button')
      fireEvent.click(sortButton)
      expect(onSortingChange).toHaveBeenCalled()
    })
  })

  describe('Pagination', () => {
    it('calls onPaginationChange when the next page button is clicked', () => {
      const onPaginationChange = vi.fn()
      renderComponent({
        datasets: Array.from({ length: 10 }, (_, i) => makeRow({ id: `ds-${i}`, name: `Dataset ${i}` })),
        rowCount: 20,
        pageIndex: 0,
        totalPages: 2,
        onPaginationChange,
      })
      const nextPageButton = screen.getByTestId('nextPage')
      expect(nextPageButton).not.toBeDisabled()
      fireEvent.click(nextPageButton)
      expect(onPaginationChange).toHaveBeenCalled()
    })
  })
})
