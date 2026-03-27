import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'

import RolesPage from './page'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: vi.fn(),
}))

import { useQueryParams } from '@/hooks/use-query-params'

vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: vi.fn(),
}))

const mockQueryParams = (tabValue = 'SYSTEM') => {
  vi.mocked(useQueryParams).mockReturnValue({
    setSortingParams: vi.fn(),
    setPaginationParams: vi.fn(),
    setSearchParam: vi.fn(),
    getApiRequestParamsByUrl: vi.fn(() => new URLSearchParams()),
    setTabValueParam: vi.fn(),
    pageIndex: 0,
    pageSize: 10,
    sorting: [],
    search: '',
    tabValue,
    subTabValue: '',
  } as unknown as ReturnType<typeof useQueryParams>)
}

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

vi.mock('@/app/services/api/roles/clientRequests', () => ({
  useGetRoles: () => ({
    data: { data: [], totalElements: 0 },
    isFetching: false,
  }),
}))

vi.mock('./components/RolesTable', () => ({
  RolesTable: () => <div data-testid="rolesTable" />,
}))

const renderPage = () => render(<RolesPage />)

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

describe('RolesPage permission gating', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockQueryParams('SYSTEM')
  })

  it('shows create button when user has ROLE_CREATE', () => {
    mockCurrentUser([PERMISSION_NAMES.ROLE_CREATE])
    renderPage()
    expect(screen.getByText('newSystemRole')).toBeInTheDocument()
  })

  it('hides create button when user lacks ROLE_CREATE', () => {
    mockCurrentUser([PERMISSION_NAMES.ROLE_READ])
    renderPage()
    expect(screen.queryByText('newSystemRole')).not.toBeInTheDocument()
  })
})

describe('RolesPage create button label', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows "newSystemRole" on System Roles tab', () => {
    mockQueryParams('SYSTEM')
    mockCurrentUser([PERMISSION_NAMES.ROLE_CREATE])
    renderPage()
    expect(screen.getByText('newSystemRole')).toBeInTheDocument()
  })

  it('shows "newDataRole" on Data Roles tab', () => {
    mockQueryParams('DATA')
    mockCurrentUser([PERMISSION_NAMES.ROLE_CREATE])
    renderPage()
    expect(screen.getByText('newDataRole')).toBeInTheDocument()
  })
})
