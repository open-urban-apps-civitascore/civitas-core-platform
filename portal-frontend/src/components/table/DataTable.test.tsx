import { createColumnHelper, getCoreRowModel, getPaginationRowModel, useReactTable } from '@tanstack/react-table'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { useState } from 'react'

import messages from '@/messages/de.json'

import { DataTable } from './DataTable'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

type Row = {
  readonly id: number
  readonly name: `item${number}`
  readonly description: `This is item${number}`
}

const mockTableData: Row[] = Array.from(
  { length: 50 },
  (_, i) =>
    ({
      id: i,
      name: `item${i}`,
      description: `This is item${i}`,
    }) as const,
)

const columnHelper = createColumnHelper<Row>()

const mockColumns = Object.keys(mockTableData[0]).map(key =>
  columnHelper.accessor(key as keyof Row, {
    header: key,
    cell: info => String(info.getValue()),
  }),
)

interface TestWrapperProps {
  hasEmptyRows?: boolean
  isLoading?: boolean
}
const TestWrapper = (props: TestWrapperProps) => {
  const { hasEmptyRows = false, isLoading = false } = props
  const [pagination, setPagination] = useState({
    pageIndex: 0,
    pageSize: 10,
  })

  const table = useReactTable({
    data: hasEmptyRows ? [] : mockTableData,
    columns: mockColumns,
    state: { pagination },
    onPaginationChange: setPagination,
    getCoreRowModel: getCoreRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
  })

  return (
      <DataTable
        table={table}
        pageIndex={pagination.pageIndex}
        pageSize={pagination.pageSize}
        totalPages={Math.ceil(mockTableData.length / pagination.pageSize)}
        isLoading={isLoading}
      />
  )
}

describe('DataTable with data rows', () => {
  beforeEach(() => {
    render(<TestWrapper />)
  })
  it('renders the table with its content', () => {
    expect(screen.getByTestId('dataTable')).toBeInTheDocument()
    expect(screen.getAllByRole('columnheader')).toHaveLength(3)
    const rows = screen.getAllByRole('row')
    expect(rows).toHaveLength(11)
    const [headerRow, ...bodyRows] = rows
    expect(headerRow).toHaveTextContent('id')
    expect(headerRow).toHaveTextContent('name')
    expect(headerRow).toHaveTextContent('description')
    for (let i = 1; i < 10; i++) {
      expect(bodyRows[i]).toHaveTextContent(`item${i}`)
    }
  })

  it('renders the next page content when paginating with next button', async () => {
    fireEvent.click(screen.getByTestId('nextPage'))
    const rows = screen.getAllByRole('row')
    expect(rows).toHaveLength(11)
    const [_headerRow, ...bodyRows] = rows
    for (let i = 1; i < 10; i++) {
      expect(bodyRows[i]).toHaveTextContent(`item${i + 10}`)
    }
    fireEvent.click(screen.getByTestId('pageSelectTrigger'))
    const paginationSelection = screen.getByTestId('pageSelectContent')
    await waitFor(() => {
      const options = within(paginationSelection).getAllByRole('option')
      expect(options).toHaveLength(5)
      fireEvent.click(options[2])
      const updatedRows = screen.getAllByRole('row')
      expect(updatedRows).toHaveLength(11)
      const [_headerRow, ...bodyRows] = updatedRows
      for (let i = 1; i < 10; i++) {
        expect(bodyRows[i]).toHaveTextContent(`item${i + 20}`)
      }
    })
  })

  it('renders the correct content when paginating with page selection', async () => {
    fireEvent.click(screen.getByTestId('pageSelectTrigger'))
    const paginationSelection = screen.getByTestId('pageSelectContent')
    const options = within(paginationSelection).getAllByRole('option')
    expect(options).toHaveLength(5)
    fireEvent.click(options[2])
    await waitFor(() => {
      const rows = screen.getAllByRole('row')
      expect(rows).toHaveLength(11)
      const [_headerRow, ...bodyRows] = rows
      for (let i = 1; i < 10; i++) {
        expect(bodyRows[i]).toHaveTextContent(`item${i + 20}`)
      }
    })
  })

  it('renders 5 rows when changing the page size to 5', async () => {
    fireEvent.click(screen.getByTestId('pageSizeTrigger'))
    const pageSizeSelection = screen.getByTestId('pageSizeContent')
    const options = within(pageSizeSelection).getAllByRole('option')
    fireEvent.click(options[0])
    await waitFor(() => {
      const rows = screen.getAllByRole('row')
      expect(rows).toHaveLength(6)
      const [_headerRow, ...bodyRows] = rows
      for (let i = 1; i < 5; i++) {
        expect(bodyRows[i]).toHaveTextContent(`item${i}`)
      }
    })
  })

  it('renders 20 rows when changing the page size to 20', async () => {
    fireEvent.click(screen.getByTestId('pageSizeTrigger'))
    const pageSizeSelection = screen.getByTestId('pageSizeContent')
    const options = within(pageSizeSelection).getAllByRole('option')
    fireEvent.click(options[2])
    await waitFor(() => {
      const rows = screen.getAllByRole('row')
      expect(rows).toHaveLength(21)
      const [_headerRow, ...bodyRows] = rows
      for (let i = 1; i < 20; i++) {
        expect(bodyRows[i]).toHaveTextContent(`item${i}`)
      }
    })
  })
})

describe('DataTable with no data rows', () => {
  it('renders the loading skeleton when no row data and loading', () => {
    render(<TestWrapper isLoading hasEmptyRows />)
    expect(screen.getByTestId('dataTableSkeleton')).toBeInTheDocument()
  })
  it('renders the no results found feedback no row data and not loading', () => {
    render(<TestWrapper hasEmptyRows />)
    const rows = screen.getAllByRole('row')
    expect(rows).toHaveLength(2)
    const [_headerRow, ...bodyRows] = rows
    expect(bodyRows[0]).toHaveTextContent('noResults')
  })
})
