import { render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import messages from '@/messages/de.json'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'
import { DatastructuresListData } from '@/types/datastructures'

import { DatastructuresTable } from './DatastructuresTable'

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

const mockCurrentUser = (permissions: PermissionName[]) => {
  vi.mocked(useGetCurrentUser).mockReturnValue({
    data: {
      username: 'test',
      email: 'test@test.com',
      title: 'MR' as const,
      firstName: 'Test',
      lastName: 'User',
      assignments: [{ scopeType: 'TENANT', scopeId: null, permissions }],
    },
  } as ReturnType<typeof useGetCurrentUser>)
}

const mockDatastructures: DatastructuresListData[] = [
  {
    id: '00000000-0000-0000-0000-000000000001',
    name: 'Customer Schema',
    description: 'Schema for customer data',
    status: 'DRAFT',
    source: 'OWN',
    versionNumber: '1.0.0',
    versions: [],
  },
  {
    id: '00000000-0000-0000-0000-000000000002',
    name: 'Order Schema',
    description: 'Schema for order data',
    status: 'AVAILABLE',
    source: null,
    versionNumber: null,
    versions: [],
  },
]

const defaultProps = {
  datastructures: mockDatastructures,
  rowCount: 2,
  pageIndex: 0,
  pageSize: 10,
  sorting: [{ desc: false, id: 'name' }] as { desc: boolean; id: string }[],
  totalPages: 1,
  onPaginationChange: vi.fn(),
  onSortingChange: vi.fn(),
  onDeleteDatastructureClick: vi.fn(),
}

const renderTable = (props = defaultProps) =>
  render(
    <NextIntlClientProvider locale="de" messages={messages}>
      <DatastructuresTable {...props} />
    </NextIntlClientProvider>,
  )

describe('DatastructuresTable', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_READ, PERMISSION_NAMES.DATASTRUCTURE_DELETE])
  })

  it('renders the table', () => {
    renderTable()
    expect(screen.getByRole('table')).toBeDefined()
  })

  it('renders rows correctly', () => {
    renderTable()
    const rows = screen.getAllByRole('row')
    expect(rows).toHaveLength(3) // 1 header + 2 data rows
  })

  describe('Permission gating', () => {
    it('action menu is hidden when user lacks DELETE permission', () => {
      mockCurrentUser([])
      renderTable()
      expect(screen.queryByRole('button', { name: 'Open menu' })).toBeNull()
    })

    it('action menu is shown when user has DELETE permission', () => {
      mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_DELETE])
      renderTable()
      const menuButtons = screen.getAllByRole('button', { name: 'Open menu' })
      expect(menuButtons).toHaveLength(2)
    })
  })
})
