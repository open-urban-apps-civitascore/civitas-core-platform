import { render, screen, within } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it, vi } from 'vitest'

import messages from '@/messages/de.json'
import { DatastructureVersionsListData } from '@/types/datastructures'

import { VersionsTable } from './VersionsTable'

const versions: DatastructureVersionsListData[] = [
  {
    id: 'v-1',
    name: 'Version 1.0.0',
    description: 'First version',
    status: 'AVAILABLE',
    source: 'OWN',
    versionNumber: '1.0.0',
    inUseByReleased: true,
  },
  {
    id: 'v-2',
    name: 'Version 2.0.0',
    description: 'Second version',
    status: 'DRAFT',
    source: 'OWN',
    versionNumber: '2.0.0',
    inUseByReleased: false,
  },
]

const defaultProps = {
  datastructureId: 'ds-1',
  versions,
  rowCount: 2,
  pageIndex: 0,
  pageSize: 10,
  sorting: [{ desc: false, id: 'versionNumber' }] as { desc: boolean; id: string }[],
  totalPages: 1,
  onPaginationChange: vi.fn(),
  onSortingChange: vi.fn(),
}

const renderTable = (props = defaultProps) =>
  render(
    <NextIntlClientProvider locale="de" messages={messages}>
      <VersionsTable {...props} />
    </NextIntlClientProvider>,
  )

describe('VersionsTable', () => {
  it('renders the Usage column', () => {
    renderTable()
    expect(screen.getByRole('columnheader', { name: 'Verwendung' })).toBeDefined()
  })

  it('marks only the version a released entity references', () => {
    renderTable()
    const rows = screen.getAllByRole('row')

    expect(within(rows[1]).getByTestId('inUseIndicator')).toBeInTheDocument()
    expect(within(rows[2]).queryByTestId('inUseIndicator')).toBeNull()
  })
})
