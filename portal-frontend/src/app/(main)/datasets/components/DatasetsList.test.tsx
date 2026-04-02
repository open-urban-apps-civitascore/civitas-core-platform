import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'

import DatasetsList from './DatasetsList'

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

vi.mock('@/app/services/api/datasets/clientRequests', () => ({
  useDeleteDataset: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: vi.fn(() => '/datasets'),
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

vi.mock('./DatasetsTable', () => ({
  DatasetsTable: () => <div data-testid="datasets-table" />,
}))

const defaultProps = {
  datasets: [],
  rowCount: 0,
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

const renderComponent = (props = {}) => render(<DatasetsList {...defaultProps} {...props} />)

describe('DatasetsList permission gating', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows create button when user has DATASET_CREATE permission', () => {
    mockCurrentUser([PERMISSION_NAMES.DATASET_CREATE])
    renderComponent()
    expect(screen.getByTestId('addDatasetButton')).toBeInTheDocument()
  })

  it('hides create button when user lacks DATASET_CREATE permission', () => {
    mockCurrentUser([PERMISSION_NAMES.DATASET_READ])
    renderComponent()
    expect(screen.queryByTestId('addDatasetButton')).not.toBeInTheDocument()
  })
})
