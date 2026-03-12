import { render, screen, within } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it, vi } from 'vitest'

import { AssignmentSummary } from '@/app/services/api/assignments/clientRequests'
import messages from '@/messages/de.json'

import { RolesAssignmentTable } from './RolesAssignmentTable'

const mockAssignments: AssignmentSummary[] = [
  {
    id: 'a1',
    group: { id: 'g1', name: 'Group 1' },
    role: { id: 'r1', name: 'Admin Role', roleType: 'SYSTEM', description: 'Admin description', readonly: true },
    scopeType: 'TENANT',
    scope: null,
  },
  {
    id: 'a2',
    group: { id: 'g2', name: 'Group 2' },
    role: { id: 'r2', name: 'Data Editor', roleType: 'DATA', description: '', readonly: false },
    scopeType: 'DATASET',
    scope: { id: 's1', name: 'Test Dataset' },
  },
]

const defaultProps = {
  assignments: mockAssignments,
  rowCount: 2,
  pageIndex: 0,
  pageSize: 10,
  totalPages: 1,
  sorting: [{ id: 'role.name', desc: false }],
  isLoading: false,
  isPlatformWide: true,
  onPaginationChange: vi.fn(),
  onSortingChange: vi.fn(),
}

const renderTable = (props = {}) =>
  render(
    <NextIntlClientProvider locale="de" messages={messages}>
      <RolesAssignmentTable {...defaultProps} {...props} />
    </NextIntlClientProvider>,
  )

describe('RolesAssignmentTable', () => {
  it('renders the table', () => {
    renderTable()
    expect(screen.getByRole('table')).toBeDefined()
  })

  it('renders all column headers', () => {
    renderTable()
    ;['Name', 'Beschreibung', 'Rolle', 'Typ', 'Geltungsbereich'].forEach(headerText => {
      expect(screen.getByRole('columnheader', { name: headerText })).toBeDefined()
    })
  })

  it('renders assignment rows with correct data', () => {
    renderTable()
    const rows = screen.getAllByRole('row')
    expect(rows).toHaveLength(3) // 1 header + 2 data rows

    const row1 = within(rows[1]).getAllByRole('cell')
    expect(row1[0]).toHaveTextContent('Admin Role')
    expect(row1[1]).toHaveTextContent('Admin description')
    expect(row1[2]).toHaveTextContent('Systemrolle')
    expect(row1[3]).toHaveTextContent('Standard')

    const row2 = within(rows[2]).getAllByRole('cell')
    expect(row2[0]).toHaveTextContent('Data Editor')
    expect(row2[1]).toHaveTextContent('-')
    expect(row2[2]).toHaveTextContent('Datenrolle')
    expect(row2[3]).toHaveTextContent('Eigene')
  })

  it('renders role name as a link to role detail page', () => {
    renderTable()
    const link = screen.getByRole('link', { name: 'Admin Role' })
    expect(link).toHaveAttribute('href', '/roles/r1')
  })

  it('shows platform scope label when isPlatformWide is true', () => {
    renderTable({ isPlatformWide: true })
    const rows = screen.getAllByRole('row')
    const scopeCell = within(rows[1]).getAllByRole('cell')[4]
    expect(scopeCell).toHaveTextContent('Plattform')
  })

  it('shows scope name when isPlatformWide is false', () => {
    renderTable({ isPlatformWide: false })
    const rows = screen.getAllByRole('row')
    const scopeCell = within(rows[2]).getAllByRole('cell')[4]
    expect(scopeCell).toHaveTextContent('Test Dataset')
  })

  it('shows dash for missing scope when isPlatformWide is false', () => {
    renderTable({ isPlatformWide: false })
    const rows = screen.getAllByRole('row')
    const scopeCell = within(rows[1]).getAllByRole('cell')[4]
    expect(scopeCell).toHaveTextContent('-')
  })
})
