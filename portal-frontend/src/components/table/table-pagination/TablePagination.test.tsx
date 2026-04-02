import { Table } from '@tanstack/react-table'
import { fireEvent, render, screen, within } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it } from 'vitest'

import messages from '@/messages/de.json'
import { DatasetTableData } from '@/types/datasets'

import TablePagination from './TablePagination'

const dummyTable = {
  setPageSize: vi.fn(),
  setPageIndex: vi.fn(),
  previousPage: vi.fn(),
  nextPage: vi.fn(),
  getCanPreviousPage: vi.fn().mockReturnValue(false),
  getCanNextPage: vi.fn().mockReturnValue(true),
} as unknown as Table<DatasetTableData>

describe('TablePagination', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <TablePagination table={dummyTable} totalPages={4} pageSize={5} pageIndex={0} />
      </NextIntlClientProvider>,
    )
  })
  it('renders results-per-page select', () => {
    expect(screen.getByText('Ergebnisse pro Seite')).toBeInTheDocument()
    fireEvent.click(screen.getByTestId('pageSizeTrigger'))
    const pageSizeSelection = screen.getByTestId('pageSizeContent')
    const options = within(pageSizeSelection).getAllByRole('option')
    expect(options).toHaveLength(4)
  })

  it('renders current page number', () => {
    expect(screen.getByTestId('currentPage')).toHaveTextContent('Seite 1 von 4')
  })

  it('renders navigation buttons with correct disabled state', () => {
    const prevButton = screen.getByRole('button', { name: 'Zur vorherigen Seite wechseln' })
    const nextButton = screen.getByRole('button', { name: 'Zur nächsten Seite wechseln' })
    expect(prevButton).toBeInTheDocument()
    expect(nextButton).toBeInTheDocument()
    expect(prevButton).toBeDisabled()
    expect(nextButton).toBeEnabled()
  })

  it('triggers pagination handlers when clicking enabled navigation buttons', () => {
    fireEvent.click(screen.getByRole('button', { name: 'Zur vorherigen Seite wechseln' }))
    expect(dummyTable.previousPage).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Zur nächsten Seite wechseln' }))
    expect(dummyTable.nextPage).toHaveBeenCalledOnce()
    fireEvent.click(screen.getByRole('button', { name: 'Zur nächsten Seite wechseln' }))
    expect(dummyTable.nextPage).toHaveBeenCalledTimes(2)
  })

  it('triggers set page size handler when changing the page size', () => {
    fireEvent.click(screen.getByTestId('pageSizeTrigger'))
    const pageSizeSelection = screen.getByTestId('pageSizeContent')
    const options = within(pageSizeSelection).getAllByRole('option')
    fireEvent.click(options[1])
    expect(dummyTable.setPageSize).toHaveBeenCalledOnce()
  })
})
