import { render, screen, within } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it, vi } from 'vitest'

import { mockDatasources } from '@/__mocks__/datasources/datasources.mock'
import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import messages from '@/messages/de.json'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'

import { DatasourcesTable } from './DatasourcesTable'

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

const defaultProps = {
  datasources: mockDatasources,
  rowCount: 3,
  pageIndex: 0,
  pageSize: 10,
  sorting: [{ desc: false, id: 'name' }] as { desc: boolean; id: string }[],
  totalPages: 1,
  onPaginationChange: vi.fn(),
  onSortingChange: vi.fn(),
  onDelete: vi.fn(),
}

const renderTable = (props = defaultProps) =>
  render(
    <NextIntlClientProvider locale="de" messages={messages}>
      <DatasourcesTable {...props} />
    </NextIntlClientProvider>,
  )

describe('DatasourcesTable', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockCurrentUser([PERMISSION_NAMES.DATASOURCE_READ, PERMISSION_NAMES.DATASOURCE_DELETE])
  })

  it('renders the table', () => {
    renderTable()
    expect(screen.getByRole('table')).toBeDefined()
  })

  it('renders the header columns correctly', () => {
    renderTable()
    ;['Name', 'Beschreibung', 'Konnektor', 'Aktivität vor', 'Verwendung', 'Status'].forEach(headerText => {
      expect(screen.getByRole('columnheader', { name: headerText })).toBeDefined()
    })
    expect(screen.queryByRole('columnheader', { name: 'id' })).toBeNull()
  })

  it('renders the rows and row content correctly', () => {
    renderTable()
    const rows = screen.getAllByRole('row')
    expect(rows).toHaveLength(4) // 1 header + 3 data rows

    const dataRow1 = rows[1]
    const cells1 = within(dataRow1).getAllByRole('cell')
    expect(cells1[0]).toHaveTextContent('MQTT Sensor Data')
    expect(cells1[1]).toHaveTextContent('Temperature sensor data via MQTT')
    expect(cells1[2]).toHaveTextContent('MQTT')
    expect(cells1[5]).toHaveTextContent('Entwurf')

    const dataRow2 = rows[2]
    const cells2 = within(dataRow2).getAllByRole('cell')
    expect(cells2[0]).toHaveTextContent('SQL Database Import')
    expect(cells2[1]).toHaveTextContent('Importing data from PostgreSQL')
    expect(cells2[2]).toHaveTextContent('SQL')
    expect(cells2[5]).toHaveTextContent('Verfügbar')
  })

  it('renders dash for null connectorType', () => {
    renderTable()
    const rows = screen.getAllByRole('row')
    const dataRow3 = rows[3]
    const cells = within(dataRow3).getAllByRole('cell')
    expect(cells[2]).toHaveTextContent('-')
  })

  it('shows the In Use indicator only for a data source a released entity references', () => {
    const [draftDatasource, ...rest] = mockDatasources
    renderTable({ ...defaultProps, datasources: [{ ...draftDatasource, inUseByReleased: true }, ...rest] })
    const rows = screen.getAllByRole('row')

    expect(within(rows[1]).getByTestId('inUseIndicator')).toBeInTheDocument()
    expect(within(rows[2]).queryByTestId('inUseIndicator')).toBeNull()
  })

  it('renders an action menu button for each row', () => {
    renderTable()
    const menuButtons = screen.getAllByRole('button', { name: 'Open menu' })
    expect(menuButtons).toHaveLength(3)
  })

  describe('Permission gating', () => {
    it('action menu is hidden when user lacks DELETE permission', () => {
      mockCurrentUser([])
      renderTable()
      expect(screen.queryByRole('button', { name: 'Open menu' })).toBeNull()
    })
  })
})
