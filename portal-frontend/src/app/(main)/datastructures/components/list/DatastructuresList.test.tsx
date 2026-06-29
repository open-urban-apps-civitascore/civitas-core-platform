import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'

import { DatastructuresList } from './DatastructuresList'

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

vi.mock('@/app/services/api/datastructures/clientRequests', () => ({
  useDeleteDatastructure: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: vi.fn(() => '/datastructures'),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: () => ({
    setSortingParams: vi.fn(),
    setPaginationParams: vi.fn(),
    setSearchParam: vi.fn(),
    setTotalPages: vi.fn(),
    pageIndex: 0,
    pageSize: 10,
    sorting: [],
    search: '',
    totalPages: 0,
  }),
}))

vi.mock('./DatastructuresTable', () => ({
  DatastructuresTable: () => <div data-testid="datastructures-table" />,
}))

const defaultProps = {
  datastructures: [],
  rowCount: 0,
}

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
  } as unknown as ReturnType<typeof useGetCurrentUser>)
}

const renderComponent = (props = {}) => render(<DatastructuresList {...defaultProps} {...props} />)

describe('DatastructuresList permission gating', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows create button when user has DATASTRUCTURE_CREATE permission', () => {
    mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_CREATE])
    renderComponent()
    expect(screen.getByTestId('addDatastructureButton')).toBeInTheDocument()
  })

  it('hides create button when user lacks DATASTRUCTURE_CREATE permission', () => {
    mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_READ])
    renderComponent()
    expect(screen.queryByTestId('addDatastructureButton')).not.toBeInTheDocument()
  })

  it('hides create button when user has DATASTRUCTURE_CREATE only via resource-scoped assignment', () => {
    mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_CREATE], 'DATASTRUCTURE', 'ds1')
    renderComponent()
    expect(screen.queryByTestId('addDatastructureButton')).not.toBeInTheDocument()
  })
})
