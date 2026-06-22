import { render, screen, within } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it, vi } from 'vitest'

import { mappedDatasets } from '@/__mocks__/datasets/mappedDatasets.mock'
import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import messages from '@/messages/de.json'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'
import { DatasetTableData } from '@/types/datasets'

import { DatasetsTable } from './DatasetsTable'

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

describe('DatasetsTable', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockCurrentUser([PERMISSION_NAMES.DATASET_READ, PERMISSION_NAMES.DATASET_DELETE])
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <DatasetsTable
          datasets={mappedDatasets}
          rowCount={2}
          pageIndex={0}
          pageSize={5}
          setPageIndex={() => null}
          setPageSize={() => null}
          sorting={[{ desc: false, id: 'name' }]}
          setSorting={() => null}
          totalPages={4}
          onPaginationChange={() => null}
          onSortingChange={() => null}
          onDeleteClick={() => null}
        />
      </NextIntlClientProvider>,
    )
  })

  it('renders the table', () => {
    expect(screen.getByRole('table')).toBeDefined()
  })

  it('renders the header and header content correctly', async () => {
    ;['Name', 'Datenpool', 'Erstellt von', 'Aktualisiert', 'Status', 'Aktion'].forEach(headerText => {
      expect(screen.getByRole('columnheader', { name: headerText })).toBeDefined()
    })
    expect(screen.queryByRole('columnheader', { name: 'id' })).toBeNull()
  })

  it('renders the rows and row content correctly', async () => {
    const rows = screen.getAllByRole('row')
    expect(rows).toHaveLength(3)

    const dataRow1 = rows[1]
    const cells1 = within(dataRow1).getAllByRole('cell')

    expect(cells1[0]).toHaveTextContent(mappedDatasets[0].name)
    expect(cells1[2]).toHaveTextContent(mappedDatasets[0].createdBy.name)
    expect(cells1[3]).toHaveTextContent('10.09.2023')
    expect(cells1[4]).toHaveTextContent('Entwurf')

    const dataRow2 = rows[2]
    const cells2 = within(dataRow2).getAllByRole('cell')

    expect(cells2[0]).toHaveTextContent(mappedDatasets[1].name)
    expect(cells2[2]).toHaveTextContent(mappedDatasets[1].createdBy.name)
    expect(cells2[3]).toHaveTextContent('01.01.2023')
    expect(cells2[4]).toHaveTextContent('Verfügbar')
  })
})

describe('DatasetsTable — Permission gating', () => {
  const renderTable = () =>
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <DatasetsTable
          datasets={mappedDatasets}
          rowCount={2}
          pageIndex={0}
          pageSize={5}
          setPageIndex={() => null}
          setPageSize={() => null}
          sorting={[{ desc: false, id: 'name' }]}
          setSorting={() => null}
          totalPages={4}
          onPaginationChange={() => null}
          onSortingChange={() => null}
          onDeleteClick={() => null}
        />
      </NextIntlClientProvider>,
    )

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('action menu is hidden when user lacks DELETE permission', () => {
    mockCurrentUser([PERMISSION_NAMES.DATASET_READ])
    renderTable()
    expect(screen.queryByRole('button', { name: 'Open menu' })).toBeNull()
  })

  it('action menu is shown when user has DELETE permission', () => {
    mockCurrentUser([PERMISSION_NAMES.DATASET_READ, PERMISSION_NAMES.DATASET_DELETE])
    renderTable()
    expect(screen.getAllByRole('button', { name: 'Open menu' })).toHaveLength(2)
  })

  describe('Datapool column permission guards', () => {
    const datasetsWithDatapool: DatasetTableData[] = [
      {
        ...mappedDatasets[0],
        datapool: { id: 'dp1', name: 'Datapool 1' },
      },
    ]

    const renderTableWithDatapool = (
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

      return render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <DatasetsTable
            datasets={datasetsWithDatapool}
            rowCount={1}
            pageIndex={0}
            pageSize={5}
            setPageIndex={() => null}
            setPageSize={() => null}
            sorting={[]}
            setSorting={() => null}
            totalPages={1}
            onPaginationChange={() => null}
            onSortingChange={() => null}
          />
        </NextIntlClientProvider>,
      )
    }

    it('shows the datapool name as a link when user has TENANT-scoped DATAPOOL_READ', () => {
      renderTableWithDatapool([PERMISSION_NAMES.DATAPOOL_READ])
      expect(screen.getByText('Datapool 1')).toBeInTheDocument()
      expect(screen.queryByText(messages.datasets.anonymousDatapool)).not.toBeInTheDocument()
    })

    it('shows the datapool name as a link when user has scoped DATAPOOL_READ for this datapool', () => {
      renderTableWithDatapool([PERMISSION_NAMES.DATAPOOL_READ], 'DATAPOOL', 'dp1')
      expect(screen.getByText('Datapool 1')).toBeInTheDocument()
      expect(screen.queryByText(messages.datasets.anonymousDatapool)).not.toBeInTheDocument()
    })

    it('shows "Anonym" when user has no DATAPOOL_READ permission', () => {
      renderTableWithDatapool([PERMISSION_NAMES.DATASET_READ])
      expect(screen.getByText(messages.datasets.anonymousDatapool)).toBeInTheDocument()
      expect(screen.queryByText('Datapool 1')).not.toBeInTheDocument()
    })

    it('shows "Anonym" when user has DATAPOOL_READ only for a different datapool', () => {
      renderTableWithDatapool([PERMISSION_NAMES.DATAPOOL_READ], 'DATAPOOL', 'dp-other')
      expect(screen.getByText(messages.datasets.anonymousDatapool)).toBeInTheDocument()
      expect(screen.queryByText('Datapool 1')).not.toBeInTheDocument()
    })
  })
})
