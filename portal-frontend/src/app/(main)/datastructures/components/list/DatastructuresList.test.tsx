import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { toast } from 'sonner'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { mockApiError } from '@/__mocks__/errors/apiError.mock'
import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'
import { DatastructuresListData } from '@/types/datastructures'

import { DatastructuresList } from './DatastructuresList'

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

const { mockDeleteMutate } = vi.hoisted(() => ({ mockDeleteMutate: vi.fn() }))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/app/services/api/datastructures/clientRequests', () => ({
  useDeleteDatastructure: vi.fn(() => ({ mutate: mockDeleteMutate, isPending: false })),
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
  DatastructuresTable: ({ onDeleteDatastructureClick }: { onDeleteDatastructureClick: (id: string) => void }) => (
    <button data-testid="datastructures-table" onClick={() => onDeleteDatastructureClick('ds-1')} />
  ),
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

describe('DatastructuresList deletion', () => {
  const deletableDatastructure = {
    id: 'ds-1',
    name: 'Struct',
    description: '',
    status: 'DRAFT',
    versionNumber: null,
    versions: [],
    inUse: false,
  } as unknown as DatastructuresListData

  beforeEach(() => {
    vi.clearAllMocks()
    mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_READ])
  })

  it('names the reason when a data source or data storage still uses the data structure', async () => {
    renderComponent({ datastructures: [deletableDatastructure] })
    await userEvent.click(screen.getByTestId('datastructures-table'))
    await userEvent.click(screen.getByTestId('confirmButton'))

    const [, { onError }] = mockDeleteMutate.mock.calls[0]
    onError(
      mockApiError(
        409,
        'Cannot modify DataStructure because one or more of its versions is referenced by a DataSource or DataSink.',
        'urn:civitas:error:RESOURCE_IN_USE',
      ),
    )

    expect(vi.mocked(toast.error)).toHaveBeenCalledWith('errors.inUseError')
  })

  it('falls back to the generic message for an unrecognised failure', async () => {
    renderComponent({ datastructures: [deletableDatastructure] })
    await userEvent.click(screen.getByTestId('datastructures-table'))
    await userEvent.click(screen.getByTestId('confirmButton'))

    const [, { onError }] = mockDeleteMutate.mock.calls[0]
    onError(new Error('network failure'))

    expect(vi.mocked(toast.error)).toHaveBeenCalledWith('errors.deletionError')
  })
})
