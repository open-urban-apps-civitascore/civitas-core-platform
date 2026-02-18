import { fireEvent, render, screen } from '@testing-library/react'
import { vi } from 'vitest'

import groups from '@/__mocks__/groups/groupsResponse.json'

import { GroupTable } from './GroupTable'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

const defaultProps = {
  groups: groups,
  isLoading: false,
  sorting: [],
  pageIndex: 0,
  pageSize: 10,
  totalPages: 1,
  rowSelection: {},
  rowCount: 2,
  onPaginationChange: vi.fn(),
  onSortingChange: vi.fn(),
}

describe('GroupTable', () => {
  it('renders table headers correctly', () => {
    const renderedTable = render(<GroupTable {...defaultProps} />)

    expect(screen.getByText('tableHeaders.name')).toBeInTheDocument()
    expect(screen.getByText('tableHeaders.contact')).toBeInTheDocument()
    expect(screen.getByText('tableHeaders.usersCount')).toBeInTheDocument()
    expect(screen.getByText('tableHeaders.description')).toBeInTheDocument()

    renderedTable.unmount()
  })

  it('renders table content correctly', () => {
    const renderedTable = render(<GroupTable {...defaultProps} />)

    expect(screen.getByRole('cell', { name: 'Group 1' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '2' })).toBeInTheDocument() // User count for Group 1
    expect(screen.getByRole('cell', { name: 'Contact 1' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'description for Group 1' })).toBeInTheDocument()

    expect(screen.getByRole('cell', { name: 'Group 2' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '0' })).toBeInTheDocument() // User count for Group 2
    expect(screen.getByRole('cell', { name: 'Contact 2' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'description for Group 2' })).toBeInTheDocument()
    renderedTable.unmount()
  })
})
