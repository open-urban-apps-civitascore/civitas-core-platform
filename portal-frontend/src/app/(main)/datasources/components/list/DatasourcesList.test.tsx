import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'

import { DatasourcesList } from './DatasourcesList'

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

vi.mock('@/app/services/api/datasources/clientRequests', () => ({
  useDeleteDatasource: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: vi.fn(() => '/datasources'),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-table-search-params', () => ({
  useTableSearchParams: () => ({
    handleSortingChange: vi.fn(),
    handlePaginationChange: vi.fn(),
    handleSearchChange: vi.fn(),
  }),
}))

vi.mock('./DatasourcesTable', () => ({
  DatasourcesTable: () => <div data-testid="datasources-table" />,
}))

const defaultProps = {
  datasources: [],
  totalCount: 0,
  pageIndex: 0,
  pageSize: 10,
  sorting: [] as { desc: boolean; id: string }[],
  totalPages: 0,
  search: '',
}

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
  } as unknown as ReturnType<typeof useGetCurrentUser>)
}

const renderComponent = (props = {}) => render(<DatasourcesList {...defaultProps} {...props} />)

describe('DatasourcesList permission gating', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows create button when user has DATASOURCE_CREATE permission', () => {
    mockCurrentUser([PERMISSION_NAMES.DATASOURCE_CREATE])
    renderComponent()
    expect(screen.getByTestId('addDatasourceButton')).toBeInTheDocument()
  })

  it('hides create button when user lacks DATASOURCE_CREATE permission', () => {
    mockCurrentUser([PERMISSION_NAMES.DATASOURCE_READ])
    renderComponent()
    expect(screen.queryByTestId('addDatasourceButton')).not.toBeInTheDocument()
  })
})
