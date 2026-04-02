import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'

import GroupsList from './GroupList'

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

vi.mock('@/app/services/api/groups/clientRequests', () => ({
  useDeleteGroup: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: vi.fn(() => '/groups'),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: () => ({
    setSortingParams: vi.fn(),
    setPaginationParams: vi.fn(),
    setSearchParam: vi.fn(),
    getApiRequestParamsByUrl: vi.fn(() => new URLSearchParams()),
    setTotalPages: vi.fn(),
    pageIndex: 0,
    pageSize: 10,
    sorting: [],
    search: '',
    totalPages: 0,
  }),
}))

vi.mock('./GroupsTable', () => ({
  GroupsTable: (props: { onDeleteGroupClick?: unknown }) => (
    <div data-testid="groups-table" data-has-delete={!!props.onDeleteGroupClick} />
  ),
}))

const defaultProps = {
  groupsData: [],
  totalCount: 0,
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

const renderComponent = (props = {}) => render(<GroupsList {...defaultProps} {...props} />)

describe('GroupsList permission gating', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows create button when user has GROUP_CREATE permission', () => {
    mockCurrentUser([PERMISSION_NAMES.GROUP_CREATE])
    renderComponent()
    expect(screen.getByText('newGroup')).toBeInTheDocument()
  })

  it('hides create button when user lacks GROUP_CREATE permission', () => {
    mockCurrentUser([])
    renderComponent()
    expect(screen.queryByText('newGroup')).not.toBeInTheDocument()
  })

  it('passes delete handler to GroupsTable when user has GROUP_DELETE permission', () => {
    mockCurrentUser([PERMISSION_NAMES.GROUP_DELETE])
    renderComponent()
    expect(screen.getByTestId('groups-table')).toHaveAttribute('data-has-delete', 'true')
  })

  it('does not pass delete handler when user lacks GROUP_DELETE permission', () => {
    mockCurrentUser([])
    renderComponent()
    expect(screen.getByTestId('groups-table')).toHaveAttribute('data-has-delete', 'false')
  })
})
