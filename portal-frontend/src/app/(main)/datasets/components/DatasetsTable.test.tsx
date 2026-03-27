import { render, screen, within } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it, vi } from 'vitest'

import { mappedDatasets } from '@/__mocks__/datasets/mappedDatasets.mock'
import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import messages from '@/messages/de.json'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'

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
    ;['Name', 'Erstellt von', 'Aktualisiert', 'Status', 'Aktion'].forEach(headerText => {
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
    expect(cells1[1]).toHaveTextContent(mappedDatasets[0].createdBy.name)
    expect(cells1[2]).toHaveTextContent('10.09.2023')
    expect(cells1[3]).toHaveTextContent('Entwurf')

    const dataRow2 = rows[2]
    const cells2 = within(dataRow2).getAllByRole('cell')

    expect(cells2[0]).toHaveTextContent(mappedDatasets[1].name)
    expect(cells2[1]).toHaveTextContent(mappedDatasets[1].createdBy.name)
    expect(cells2[2]).toHaveTextContent('01.01.2023')
    expect(cells2[3]).toHaveTextContent('Verfügbar')
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
})
