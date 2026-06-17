import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'
import { DatapoolSummary } from '@/types/datapools'

import { DatapoolsTable } from './DatapoolsTable'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

const mockCurrentUser = (
  permissions: PermissionName[],
  scopeType: 'TENANT' | 'DATAPOOL' = 'TENANT',
  scopeId: string | null = null,
) => {
  vi.mocked(useGetCurrentUser).mockReturnValue({
    data: {
      username: 'test',
      email: 'test@test.com',
      title: 'MR' as const,
      firstName: 'Test',
      lastName: 'User',
      assignments: [{ scopeType, scopeId, permissions }],
    },
  } as ReturnType<typeof useGetCurrentUser>)
}

const mockDatapools: DatapoolSummary[] = [
  {
    id: 'dp1',
    name: 'Datapool 1',
    description: 'Description 1',
    contactPerson: { id: 'u1', name: 'Anna Müller' },
    datasets: ['d1', 'd2'],
    createdAt: '2024-01-01T00:00:00Z',
    modifiedAt: '2024-06-01T00:00:00Z',
  },
  {
    id: 'dp2',
    name: 'Default Datapool',
    description: 'Description 2',
    contactPerson: null,
    datasets: [],
    createdAt: '2024-01-02T00:00:00Z',
    modifiedAt: '2024-06-02T00:00:00Z',
  },
]

const defaultTableProps = {
  datapools: mockDatapools,
  rowCount: 2,
  pageIndex: 0,
  pageSize: 10,
  totalPages: 1,
  sorting: [],
  rowSelection: {},
  onPaginationChange: vi.fn(),
  onSortingChange: vi.fn(),
}

const renderTable = (props = {}) => render(<DatapoolsTable {...defaultTableProps} {...props} />)

describe('DatapoolsTable', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockCurrentUser([PERMISSION_NAMES.DATAPOOL_DELETE])
  })

  describe('Column Header', () => {
    it('renders all expected column headers', () => {
      renderTable()
      const headers = screen.getAllByRole('columnheader')
      expect(headers).toHaveLength(4)
      expect(headers[0]).toHaveTextContent('tableHeaders.name')
      expect(headers[1]).toHaveTextContent('tableHeaders.datasets')
      expect(headers[2]).toHaveTextContent('tableHeaders.contact')
      expect(headers[3]).toHaveTextContent('tableHeaders.description')
    })

    it('hides the id column', () => {
      renderTable()
      expect(screen.queryByRole('columnheader', { name: 'id' })).not.toBeInTheDocument()
    })
  })

  describe('Column Content', () => {
    it('renders name, description and dataset count correctly', () => {
      renderTable()
      const rows = screen.getAllByRole('row')
      // rows[0] = Header, rows[1] = dp1, rows[2] = dp2
      const cellsDp1 = within(rows[1]).getAllByRole('cell')
      expect(cellsDp1[0]).toHaveTextContent('Datapool 1')
      expect(cellsDp1[1]).toHaveTextContent('2')
      expect(cellsDp1[3]).toHaveTextContent('Description 1')

      const cellsDp2 = within(rows[2]).getAllByRole('cell')
      expect(cellsDp2[0]).toHaveTextContent('Default Datapool')
      expect(cellsDp2[1]).toHaveTextContent('0')
      expect(cellsDp2[3]).toHaveTextContent('Description 2')
    })

    it('shows the contact name when a contactPerson is present', () => {
      renderTable()
      const rows = screen.getAllByRole('row')
      const cellsDp1 = within(rows[1]).getAllByRole('cell')
      expect(cellsDp1[2]).toHaveTextContent('Anna Müller')
    })

    it('shows "None" when no contactPerson is present', () => {
      renderTable()
      const rows = screen.getAllByRole('row')
      const cellsDp2 = within(rows[2]).getAllByRole('cell')
      expect(cellsDp2[2]).toHaveTextContent('noContact')
    })
  })

  describe('Delete-Button', () => {
    it('shows the Delete button for each datapool when user has TENANT-scoped DATAPOOL_DELETE permission', () => {
      renderTable({ onDeleteClick: vi.fn() })
      const buttons = screen.getAllByRole('button', { name: 'Open menu' })
      expect(buttons).toHaveLength(2)
    })

    it('does not show the Delete button when onDeleteClick is not provided', () => {
      renderTable()
      expect(screen.queryByRole('button', { name: 'Open menu' })).not.toBeInTheDocument()
    })

    it('does not show the Delete button when user lacks DATAPOOL_DELETE permission', () => {
      mockCurrentUser([])
      renderTable({ onDeleteClick: vi.fn() })
      expect(screen.queryByRole('button', { name: 'Open menu' })).not.toBeInTheDocument()
    })

    it('shows Delete button only for the datapool the user has scoped DATAPOOL_DELETE permission on', () => {
      mockCurrentUser([PERMISSION_NAMES.DATAPOOL_DELETE], 'DATAPOOL', 'dp1')
      renderTable({ onDeleteClick: vi.fn() })
      const buttons = screen.getAllByRole('button', { name: 'Open menu' })
      expect(buttons).toHaveLength(1)
    })

    it('calls onDeleteClick with the correct datapool', async () => {
      const user = userEvent.setup()
      const onDeleteClick = vi.fn()
      renderTable({ onDeleteClick })
      const userMenu = screen.getAllByRole('button', { name: 'Open menu' })
      await user.click(userMenu[0])
      await user.click(await screen.findByText('actions.delete'))
      expect(onDeleteClick).toHaveBeenCalledWith(mockDatapools[0])
    })
  })
})
