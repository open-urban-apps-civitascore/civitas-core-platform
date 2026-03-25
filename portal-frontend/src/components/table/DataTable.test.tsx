import {
  CellContext,
  createColumnHelper,
  getCoreRowModel,
  getExpandedRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
} from '@tanstack/react-table'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { useState } from 'react'

import { DataTable, DataTableProps } from './DataTable'
import { ExpanderCell } from './expander-cell/ExpanderCell'
import { SortableTableHeader } from './sortable-table-header/SortableTableHeader'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

const mockUseIsTruncated = vi.fn().mockReturnValue(false)

vi.mock('@/hooks/use-is-truncated', () => ({
  useIsTruncated: () => mockUseIsTruncated(),
}))

const onRowClickMock = vi.fn()

type Row = {
  readonly id: number
  readonly name: `item${number}`
  readonly description: `This is item${number}`
  readonly subrows?: Row[]
}

const mockTableData: Row[] = Array.from(
  { length: 50 },
  (_, i) =>
    ({
      id: i,
      name: `item${i}`,
      description: `This is item${i}`,
      subrows: Array.from({ length: 5 }, (_, i) => ({
        id: 50 + i,
        name: `item${50 + i}`,
        description: `This is item${50 + i}`,
      })),
    }) as const,
)

const columnHelper = createColumnHelper<Row>()

const mockColumnsWithoutSubrows = Object.keys(mockTableData[0]).flatMap(key =>
  key !== 'subrows'
    ? columnHelper.accessor(key as keyof Row, {
        header: ({ column }) => (key === 'id' ? <SortableTableHeader title={key} column={column} /> : key),
        cell: info => info.getValue(),
      })
    : [],
)

const mockColumnsWithTruncate = [
  columnHelper.accessor('id', {
    header: 'id',
    cell: info => info.getValue(),
  }),
  columnHelper.accessor('name', {
    header: 'name',
    cell: info => info.getValue(),
    meta: { truncate: true },
  }),
  columnHelper.accessor('description', {
    header: 'description',
    cell: info => info.getValue(),
  }),
]

const mockColumnsWithSubrows = [
  columnHelper.accessor('id', {
    header: 'id',
    cell: info => info.getValue(),
  }),
  columnHelper.accessor('name', {
    header: 'name',
    cell: ({ row }: CellContext<Row, unknown>) => (
      <ExpanderCell row={row} className="font-medium">
        {row.original.name}
      </ExpanderCell>
    ),
  }),
  columnHelper.accessor('description', {
    header: 'name',
    cell: info => info.getValue(),
  }),
]

interface TestWrapperProps extends Omit<DataTableProps<Row>, 'table' | 'pageSize' | 'pageIndex' | 'totalPages'> {
  pageIndex?: number
  hasEmptyRows?: boolean
  hasSubrows?: boolean
  hasTruncate?: boolean
}

const TestWrapper = (props: TestWrapperProps) => {
  const { hasEmptyRows = false, hasSubrows = false, hasTruncate = false, ...tableProps } = props
  const [pagination, setPagination] = useState({
    pageIndex: tableProps.pageIndex ?? 0,
    pageSize: 10,
  })

  const table = useReactTable({
    getRowId: row => String(row.id),
    data: hasEmptyRows ? [] : mockTableData,
    columns: hasSubrows ? mockColumnsWithSubrows : hasTruncate ? mockColumnsWithTruncate : mockColumnsWithoutSubrows,
    state: { pagination },
    onPaginationChange: setPagination,
    getCoreRowModel: getCoreRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getSubRows: hasSubrows ? row => row.subrows || [] : undefined,
    getExpandedRowModel: getExpandedRowModel(),
    paginateExpandedRows: false,
  })

  return (
    <DataTable
      table={table}
      pageIndex={pagination.pageIndex}
      pageSize={pagination.pageSize}
      totalPages={Math.ceil(mockTableData.length / pagination.pageSize)}
      testId="dataTable"
      {...tableProps}
    />
  )
}

describe('DataTable', () => {
  it('renders the table with its content', () => {
    render(<TestWrapper />)
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
})

describe('DataTable pagination', () => {
  it('renders the next page content when paginating with next page button', async () => {
    render(<TestWrapper />)
    fireEvent.click(screen.getByTestId('nextPage'))
    const rows = screen.getAllByRole('row')
    expect(rows).toHaveLength(11)
    const [_headerRow, ...bodyRows] = rows
    for (let i = 1; i < 10; i++) {
      expect(bodyRows[i]).toHaveTextContent(`item${i + 10}`)
    }
  })

  it('renders the previous page content when paginating with previous page button', async () => {
    render(<TestWrapper pageIndex={1} />)
    fireEvent.click(screen.getByTestId('prevPage'))
    const rows = screen.getAllByRole('row')
    expect(rows).toHaveLength(11)
    const [_headerRow, ...bodyRows] = rows
    for (let i = 1; i < 10; i++) {
      expect(bodyRows[i]).toHaveTextContent(`item${i}`)
    }
  })

  it('renders the correct content when paginating with page selection', async () => {
    render(<TestWrapper />)
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
    render(<TestWrapper />)
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
    render(<TestWrapper />)
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

describe('DataTable sub rows', () => {
  it('toggles sub rows on expander cell button click', async () => {
    render(<TestWrapper hasSubrows />)
    expect(screen.getAllByRole('row')).toHaveLength(11)
    const expanderCells = screen.getAllByTestId('expanderCell')
    expect(expanderCells).toHaveLength(10)
    fireEvent.click(await within(expanderCells[0]).findByRole('button'))
    await waitFor(() => expect(screen.getAllByRole('row')).toHaveLength(16))
  })
})

describe('DataTable with no data rows', () => {
  it('renders the loading skeleton when no row data and loading', () => {
    render(<TestWrapper isLoading hasEmptyRows />)
    expect(screen.getByTestId('loadingSkeleton')).toBeInTheDocument()
  })
  it('renders the no results found feedback no row data and not loading', () => {
    render(<TestWrapper hasEmptyRows />)
    const rows = screen.getAllByRole('row')
    expect(rows).toHaveLength(2)
    const [_headerRow, ...bodyRows] = rows
    expect(bodyRows[0]).toHaveTextContent('noResults')
  })
})

describe('DataTable sorting', () => {
  it('sorts rows ascending on one sort button click', async () => {
    render(<TestWrapper />)
    const nameColumn = screen.getByRole('columnheader', { name: 'id' })
    const sortButton = await within(screen.getByTestId('sortableTableHeader')).findByRole('button')
    expect(nameColumn).toHaveAttribute('aria-sort', 'none')

    // check ascending sorting
    fireEvent.click(sortButton)
    expect(nameColumn).toHaveAttribute('aria-sort', 'ascending')
    // verify sorted rows
    const [_headerRow1, ...bodyRows] = screen.getAllByRole('row')
    const rowIds = bodyRows.map(row => Number(within(row).getAllByRole('cell')[0]))
    const isAscending = () => {
      for (let i = 1; i < rowIds.length; i++) {
        if (rowIds[i] < rowIds[i - 1]) return false
      }
      return true
    }
    expect(isAscending()).toBeTruthy()
  })

  it('sorts rows decending on two sort button clicks', async () => {
    render(<TestWrapper />)
    const nameColumn = screen.getByRole('columnheader', { name: 'id' })
    const sortButton = await within(screen.getByTestId('sortableTableHeader')).findByRole('button')
    expect(nameColumn).toHaveAttribute('aria-sort', 'none')

    // check descending sorting
    fireEvent.click(sortButton)
    fireEvent.click(sortButton)
    await waitFor(() => expect(nameColumn).toHaveAttribute('aria-sort', 'descending'))
    // verify sorted rows
    const [_headerRow1, ...bodyRows] = screen.getAllByRole('row')
    const rowIds = bodyRows.map(row => Number(within(row).getAllByRole('cell')[0]))
    const isDescending = () => {
      for (let i = 1; i < rowIds.length; i++) {
        if (rowIds[i] > rowIds[i - 1]) return false
      }
      return true
    }
    expect(isDescending()).toBeTruthy()
  })
})

describe('DataTable row click', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })
  it('rows click is disabled when isRowClickable is false', () => {
    render(<TestWrapper isRowClickable={() => false} onRowClick={onRowClickMock} />)
    const rows = screen.getAllByRole('row')
    expect(rows[1]).not.toHaveClass('cursor-pointer')
    fireEvent.click(rows[1])
    expect(onRowClickMock).not.toHaveBeenCalled()
  })
  it('rows click is enabled when isRowClickable is true', () => {
    render(<TestWrapper isRowClickable={() => true} onRowClick={onRowClickMock} />)
    const rows = screen.getAllByRole('row')
    expect(rows[1]).toHaveClass('cursor-pointer')
    fireEvent.click(rows[1])
    expect(onRowClickMock).toHaveBeenCalledOnce()
  })
})

describe('DataTable layout', () => {
  it('renders the data table with card styles when hasCard is true', () => {
    render(<TestWrapper />)
    expect(screen.getByTestId('dataTableScrollArea')).toHaveClass('rounded-md border-1')
  })
  it('renders the data table without card styles when hasCard is false', () => {
    render(<TestWrapper hasCard={false} />)
    expect(screen.getByTestId('dataTableScrollArea')).not.toHaveClass('rounded-md border-1')
  })
})

describe('DataTable truncated cell tooltip', () => {
  beforeEach(() => {
    mockUseIsTruncated.mockReturnValue(false)
  })

  it('does not show tooltip when truncated cell text is not overflowing', async () => {
    vi.useFakeTimers()
    render(<TestWrapper hasTruncate />)
    const truncatedCells = screen.getAllByText(/^item\d+$/)
    fireEvent.focus(truncatedCells[0])
    await vi.advanceTimersByTimeAsync(500)
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
    vi.useRealTimers()
  })

  it('shows tooltip when truncated cell text is overflowing', async () => {
    vi.useFakeTimers()
    mockUseIsTruncated.mockReturnValue(true)
    render(<TestWrapper hasTruncate />)
    const truncatedCells = screen.getAllByText(/^item\d+$/)
    fireEvent.focus(truncatedCells[0])
    await vi.advanceTimersByTimeAsync(500)
    expect(screen.getByRole('tooltip')).toHaveTextContent('item0')
    vi.useRealTimers()
  })
})
