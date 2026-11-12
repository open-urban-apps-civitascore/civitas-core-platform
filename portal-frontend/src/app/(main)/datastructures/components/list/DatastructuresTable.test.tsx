import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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

const mockCurrentUser = (
  permissions: PermissionName[],
  scopeType: 'TENANT' | 'DATASTRUCTURE' = 'TENANT',
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

const mockDatastructures: DatastructuresListData[] = [
  {
    id: '00000000-0000-0000-0000-000000000001',
    name: 'Customer Schema',
    description: 'Schema for customer data',
    status: null,
    source: 'OWN',
    versionNumber: '1.0.0',
    versions: [],
  },
  {
    id: '00000000-0000-0000-0000-000000000002',
    name: 'Order Schema',
    description: 'Schema for order data',
    status: null,
    source: null,
    versionNumber: null,
    versions: [],
  },
]

const defaultProps = {
  datastructures: mockDatastructures,
  isDuplicating: false,
  rowCount: 2,
  pageIndex: 0,
  pageSize: 10,
  sorting: [{ desc: false, id: 'name' }] as { desc: boolean; id: string }[],
  totalPages: 1,
  onPaginationChange: vi.fn(),
  onSortingChange: vi.fn(),
  onDeleteDatastructureClick: vi.fn(),
  onDeleteVersionClick: vi.fn(),
  onDuplicateVersionClick: vi.fn(),
}

const createVersion = (idSuffix: string, overrides?: Partial<DatastructuresListData>): DatastructuresListData => ({
  ...mockDatastructures[0],
  id: `00000000-0000-0000-0000-0000000000${idSuffix}`,
  name: 'Version 1.0.0',
  status: 'DRAFT',
  versions: [],
  ...overrides,
})

const expandRow = (row: HTMLElement) =>
  fireEvent.click(within(within(row).getByTestId('expanderCell')).getByRole('button'))

const renderTable = (props: typeof defaultProps = defaultProps) =>
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

  it('shows the Usage column and marks only the rows a released entity references', () => {
    const [first, second] = mockDatastructures
    renderTable({
      ...defaultProps,
      datastructures: [
        { ...first, versions: [createVersion('11')], inUseByReleasedVersionCount: 1 },
        { ...second, versions: [createVersion('12')], inUseByReleasedVersionCount: 0 },
      ],
    })
    const rows = screen.getAllByRole('row')

    expect(screen.getByRole('columnheader', { name: 'Verwendung' })).toBeDefined()
    expect(within(rows[1]).getByTestId('inUseIndicator')).toBeInTheDocument()
    expect(within(rows[2]).queryByTestId('inUseIndicator')).toBeNull()
  })

  it('shows the version count per status on a data structure row and the own status on a version row', () => {
    const [datastructure] = mockDatastructures
    renderTable({
      ...defaultProps,
      datastructures: [
        {
          ...datastructure,
          versions: [
            createVersion('11', { status: 'AVAILABLE' }),
            createVersion('12', { status: 'AVAILABLE' }),
            createVersion('13', { status: 'DRAFT' }),
          ],
          versionCountByStatus: { AVAILABLE: 2, DRAFT: 1 },
        },
      ],
      rowCount: 1,
    })

    const [topLevelRow] = screen.getAllByRole('row').slice(1)
    expect(within(topLevelRow).getByText('2 Verfügbar')).toBeInTheDocument()
    expect(within(topLevelRow).getByText('1 Entwurf')).toBeInTheDocument()

    expandRow(topLevelRow)

    const [, , subRow] = screen.getAllByRole('row')
    expect(within(subRow).getByTestId('statusBadge')).toHaveTextContent(/^Verfügbar$/)
  })

  it('leaves the status column of a data structure row without versions empty', () => {
    const [datastructure] = mockDatastructures
    renderTable({
      ...defaultProps,
      datastructures: [{ ...datastructure, versionCountByStatus: { AVAILABLE: 0, DRAFT: 0 } }],
      rowCount: 1,
    })

    const [topLevelRow] = screen.getAllByRole('row').slice(1)
    const statusColumnIndex = screen.getAllByRole('columnheader').findIndex(header => header.textContent === 'Status')
    const statusCell = within(topLevelRow).getAllByRole('cell')[statusColumnIndex]
    expect(statusCell).toBeEmptyDOMElement()
  })

  it('shows the draft status on a draft version row', () => {
    const [datastructure] = mockDatastructures
    renderTable({
      ...defaultProps,
      datastructures: [{ ...datastructure, versions: [createVersion('11', { status: 'DRAFT' })] }],
      rowCount: 1,
    })

    const [topLevelRow] = screen.getAllByRole('row').slice(1)
    expandRow(topLevelRow)

    const [, , subRow] = screen.getAllByRole('row')
    expect(within(subRow).getByTestId('statusBadge')).toHaveTextContent(/^Entwurf$/)
  })

  it('shows no draft badge on a data structure row without drafts', () => {
    const [datastructure] = mockDatastructures
    renderTable({
      ...defaultProps,
      datastructures: [{ ...datastructure, versionCountByStatus: { AVAILABLE: 3, DRAFT: 0 } }],
      rowCount: 1,
    })

    const [topLevelRow] = screen.getAllByRole('row').slice(1)
    expect(within(topLevelRow).getAllByTestId('statusBadge')).toHaveLength(1)
    expect(within(topLevelRow).getByText('3 Verfügbar')).toBeInTheDocument()
  })

  it('shows the plural for more than one draft on a data structure row', () => {
    const [datastructure] = mockDatastructures
    renderTable({
      ...defaultProps,
      datastructures: [{ ...datastructure, versionCountByStatus: { AVAILABLE: 0, DRAFT: 2 } }],
      rowCount: 1,
    })

    const [topLevelRow] = screen.getAllByRole('row').slice(1)
    expect(within(topLevelRow).getByText('2 Entwürfe')).toBeInTheDocument()
  })

  it('shows the in-use icon and the count of used versions on a data structure row', () => {
    const [datastructure] = mockDatastructures
    renderTable({
      ...defaultProps,
      datastructures: [
        {
          ...datastructure,
          versions: [createVersion('11'), createVersion('12'), createVersion('13')],
          inUseByReleasedVersionCount: 1,
        },
      ],
      rowCount: 1,
    })

    const [topLevelRow] = screen.getAllByRole('row').slice(1)
    expect(within(topLevelRow).getByTestId('inUseIndicator')).toBeInTheDocument()
    expect(within(topLevelRow).getByText('1/3')).toBeInTheDocument()
  })

  it('shows no usage on a data structure row without used versions', () => {
    const [datastructure] = mockDatastructures
    renderTable({
      ...defaultProps,
      datastructures: [{ ...datastructure, versions: [createVersion('11')], inUseByReleasedVersionCount: 0 }],
      rowCount: 1,
    })

    const [topLevelRow] = screen.getAllByRole('row').slice(1)
    expect(within(topLevelRow).queryByTestId('inUseIndicator')).toBeNull()
    expect(within(topLevelRow).queryByText('0/1')).toBeNull()
  })

  it('shows the in-use icon without a count on a used version row', () => {
    const [datastructure] = mockDatastructures
    renderTable({
      ...defaultProps,
      datastructures: [
        {
          ...datastructure,
          versions: [createVersion('11', { inUseByReleased: true })],
          inUseByReleasedVersionCount: 1,
        },
      ],
      rowCount: 1,
    })

    const [topLevelRow] = screen.getAllByRole('row').slice(1)
    expandRow(topLevelRow)

    const [, , subRow] = screen.getAllByRole('row')
    expect(within(subRow).getByTestId('inUseIndicator')).toBeInTheDocument()
    expect(within(subRow).queryByText('1/1')).toBeNull()
  })

  it('shows the number of versions next to the name of a data structure row', () => {
    const [datastructure] = mockDatastructures
    renderTable({
      ...defaultProps,
      datastructures: [{ ...datastructure, versions: [createVersion('11'), createVersion('12'), createVersion('13')] }],
      rowCount: 1,
    })

    const [topLevelRow] = screen.getAllByRole('row').slice(1)
    expect(within(topLevelRow).getByTestId('countBadge')).toHaveTextContent(/^3$/)
  })

  it('shows zero versions next to the name of a data structure row without versions', () => {
    const [datastructure] = mockDatastructures
    renderTable({ ...defaultProps, datastructures: [datastructure], rowCount: 1 })

    const [topLevelRow] = screen.getAllByRole('row').slice(1)
    expect(within(topLevelRow).getByTestId('countBadge')).toHaveTextContent(/^0$/)
  })

  it('shows no version count on a version row', () => {
    const [datastructure] = mockDatastructures
    renderTable({
      ...defaultProps,
      datastructures: [{ ...datastructure, versions: [createVersion('11')] }],
      rowCount: 1,
    })

    const [topLevelRow] = screen.getAllByRole('row').slice(1)
    expandRow(topLevelRow)

    const [, , subRow] = screen.getAllByRole('row')
    expect(within(subRow).queryByTestId('countBadge')).toBeNull()
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

    it('labels the delete of a data structure row as deleting the data structure', async () => {
      const user = userEvent.setup()
      mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_DELETE])
      renderTable({ ...defaultProps, datastructures: [mockDatastructures[0]], rowCount: 1 })

      await user.click(screen.getByRole('button', { name: 'Open menu' }))
      await user.click(await screen.findByText('Datenstruktur löschen'))

      expect(defaultProps.onDeleteDatastructureClick).toHaveBeenCalledWith(mockDatastructures[0].id)
    })
  })

  describe('Version sub-rows', () => {
    const DATASTRUCTURE_ID = mockDatastructures[0].id
    const VERSION_ID = '00000000-0000-0000-0000-000000000099'

    const renderWithVersion = (isDuplicating = false) => {
      renderTable({
        ...defaultProps,
        isDuplicating,
        datastructures: [{ ...mockDatastructures[0], versions: [createVersion('99')] }],
        rowCount: 1,
      })
      expandRow(screen.getAllByRole('row')[1])
    }

    it('offers duplication on a version row when the UPDATE permission is scoped to its parent data structure', () => {
      mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_UPDATE], 'DATASTRUCTURE', DATASTRUCTURE_ID)
      renderWithVersion()

      const [, topLevelRow, subRow] = screen.getAllByRole('row')
      expect(within(topLevelRow).queryByRole('button', { name: 'Open menu' })).toBeNull()
      expect(within(subRow).getByRole('button', { name: 'Open menu' })).toBeInTheDocument()
    })

    it('hides the action menu of a version row when the UPDATE permission is scoped to the version itself', () => {
      mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_UPDATE], 'DATASTRUCTURE', VERSION_ID)
      renderWithVersion()

      expect(screen.queryByRole('button', { name: 'Open menu' })).toBeNull()
    })

    it('offers deletion on a version row when the DELETE permission is scoped to its parent data structure', async () => {
      const user = userEvent.setup()
      mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_DELETE], 'DATASTRUCTURE', DATASTRUCTURE_ID)
      renderWithVersion()

      const [, , subRow] = screen.getAllByRole('row')
      await user.click(within(subRow).getByRole('button', { name: 'Open menu' }))

      expect(await screen.findByText('Version löschen')).toBeInTheDocument()
      expect(screen.queryByText('Version duplizieren')).toBeNull()
    })

    it('hides the action menu of a version row when the DELETE permission is scoped to the version itself', () => {
      mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_DELETE], 'DATASTRUCTURE', VERSION_ID)
      renderWithVersion()

      expect(screen.queryByRole('button', { name: 'Open menu' })).toBeNull()
    })

    it('routes deletion of a version row to onDeleteVersionClick with the parent data structure id', async () => {
      const user = userEvent.setup()
      mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_DELETE], 'DATASTRUCTURE', DATASTRUCTURE_ID)
      renderWithVersion()

      const [, , subRow] = screen.getAllByRole('row')
      await user.click(within(subRow).getByRole('button', { name: 'Open menu' }))
      await user.click(await screen.findByText('Version löschen'))

      expect(defaultProps.onDeleteVersionClick).toHaveBeenCalledWith(DATASTRUCTURE_ID, VERSION_ID)
      expect(defaultProps.onDeleteDatastructureClick).not.toHaveBeenCalled()
    })

    it('routes duplication of a version row to onDuplicateVersionClick with the parent data structure id', async () => {
      const user = userEvent.setup()
      mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_UPDATE], 'DATASTRUCTURE', DATASTRUCTURE_ID)
      renderWithVersion()

      await user.click(screen.getByRole('button', { name: 'Open menu' }))
      await user.click(await screen.findByText('Version duplizieren'))

      expect(defaultProps.onDuplicateVersionClick).toHaveBeenCalledWith(DATASTRUCTURE_ID, VERSION_ID)
    })

    it('disables duplication of a version row while a copy is being made', async () => {
      const user = userEvent.setup()
      mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_UPDATE], 'DATASTRUCTURE', DATASTRUCTURE_ID)
      renderWithVersion(true)

      await user.click(screen.getByRole('button', { name: 'Open menu' }))
      const duplicateItem = await screen.findByRole('menuitem', { name: 'Version duplizieren' })
      expect(duplicateItem).toHaveAttribute('aria-disabled', 'true')
      await user.click(duplicateItem)

      expect(defaultProps.onDuplicateVersionClick).not.toHaveBeenCalled()
    })
  })
})
