import { Table } from '@tanstack/react-table'
import { render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it } from 'vitest'

import messages from '@/messages/de.json'

import { Dataset } from '../page'
import TablePagination from './TablePagination'

const dummyTable = {
  setPageSize: () => {},
  setPageIndex: () => {},
  previousPage: () => {},
  nextPage: () => {},
  getCanPreviousPage: () => true,
  getCanNextPage: () => true,
} as unknown as Table<Dataset>

describe('TablePagination', () => {
  beforeEach(() => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <TablePagination table={dummyTable} totalPages={4} pageSize={5} pageIndex={0} />
      </NextIntlClientProvider>,
    )
  })
  it('renders results-per-page select', () => {
    expect(screen.getByText('Ergebnisse pro Seite')).toBeInTheDocument()
    expect(screen.getByRole('combobox')).toBeInTheDocument()
  })

  it('renders current page number', () => {
    expect(screen.getByTestId('currentPage')).toHaveTextContent('Seite 1 von 4')
  })

  it('renders navigation buttons', () => {
    expect(screen.getByRole('button', { name: '«' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '‹' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '›' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '»' })).toBeInTheDocument()
  })
})
