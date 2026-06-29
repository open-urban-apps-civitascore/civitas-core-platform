import { render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { mockDatasources } from '@/__mocks__/datasources/datasources.mock'

import { DatasourceTable, DatasourceTableProps } from './DatasourceTable'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
  useLocale: () => 'en',
}))

const defaultProps: DatasourceTableProps = {
  datasources: mockDatasources,
  rowCount: mockDatasources.length,
  pageIndex: 0,
  pageSize: 10,
  totalPages: 1,
  sorting: [],
  onPaginationChange: vi.fn(),
  onSortingChange: vi.fn(),
}

const renderTable = (props = defaultProps) => render(<DatasourceTable {...props} />)

describe('DatasourceTable', () => {
  it('renders the table', () => {
    renderTable()
    expect(screen.getByRole('table')).toBeDefined()
  })

  it('renders all column headers', () => {
    renderTable()
    ;[
      'tableHeaders.name',
      'tableHeaders.description',
      'tableHeaders.connector',
      'tableHeaders.lastActive',
      'tableHeaders.status',
    ].forEach(header => {
      expect(screen.getByRole('columnheader', { name: header })).toBeDefined()
    })
    expect(screen.queryByRole('columnheader', { name: 'id' })).toBeNull()
  })

  it('renders a row for each datasource', () => {
    renderTable()
    const rows = screen.getAllByRole('row')
    expect(rows).toHaveLength(mockDatasources.length + 1) // +1 for header
  })

  it('renders datasource name as a link to the detail page', () => {
    renderTable()
    const link = screen.getByRole('link', { name: 'MQTT Sensor Data' })
    expect(link).toHaveAttribute('href', '/datasources/00000000-0000-0000-0000-000000000001')
  })

  it('renders description and connectorType per row', () => {
    renderTable()
    const rows = screen.getAllByRole('row')
    const cells = within(rows[1]).getAllByRole('cell')
    expect(cells[1]).toHaveTextContent('Temperature sensor data via MQTT')
    expect(cells[2]).toHaveTextContent('MQTT')
  })

  it('renders dash for missing connectorType', () => {
    renderTable()
    const rows = screen.getAllByRole('row')
    const cells = within(rows[3]).getAllByRole('cell')
    expect(cells[2]).toHaveTextContent('-')
  })

  it('renders DRAFT status with translated key', () => {
    renderTable()
    const rows = screen.getAllByRole('row')
    const cells = within(rows[1]).getAllByRole('cell')
    expect(cells[4]).toHaveTextContent('status.DRAFT')
  })

  it('renders AVAILABLE status with translated key', () => {
    renderTable()
    const rows = screen.getAllByRole('row')
    const cells = within(rows[2]).getAllByRole('cell')
    expect(cells[4]).toHaveTextContent('status.AVAILABLE')
  })

  it('shows loading skeleton when isLoading is true', () => {
    renderTable({ ...defaultProps, datasources: [], isLoading: true })
    expect(screen.getByTestId('loadingSkeleton')).toBeInTheDocument()
  })
})
