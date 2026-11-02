import { act, render, screen } from '@testing-library/react'
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

const {
  mockDeleteMutate,
  mockDeleteVersionMutate,
  mockDuplicateVersionMutate,
  mockDuplicateVersionState,
  mockRefresh,
} = vi.hoisted(() => ({
  mockDeleteMutate: vi.fn(),
  mockDeleteVersionMutate: vi.fn(),
  mockDuplicateVersionMutate: vi.fn(),
  mockDuplicateVersionState: { isPending: false },
  mockRefresh: vi.fn(),
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/app/services/api/datastructures/clientRequests', () => ({
  useDeleteDatastructure: vi.fn(() => ({ mutate: mockDeleteMutate, isPending: false })),
}))

vi.mock('@/app/services/api/datastructures/versions/clientRequests', () => ({
  useDeleteDatastructureVersion: vi.fn(() => ({ mutate: mockDeleteVersionMutate, isPending: false })),
  useDuplicateDatastructureVersion: vi.fn(() => ({
    mutate: mockDuplicateVersionMutate,
    isPending: mockDuplicateVersionState.isPending,
  })),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), refresh: mockRefresh }),
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
  DatastructuresTable: ({
    isDuplicating,
    onDeleteDatastructureClick,
    onDeleteVersionClick,
    onDuplicateVersionClick,
  }: {
    isDuplicating: boolean
    onDeleteDatastructureClick: (id: string) => void
    onDeleteVersionClick: (datastructureId: string, versionId: string) => void
    onDuplicateVersionClick: (datastructureId: string, versionId: string) => void
  }) => (
    <div>
      <button data-testid="datastructures-table" onClick={() => onDeleteDatastructureClick('ds-1')} />
      <button data-testid="delete-version-v1" onClick={() => onDeleteVersionClick('ds-1', 'v1')} />
      <button data-testid="delete-version-v2" onClick={() => onDeleteVersionClick('ds-1', 'v2')} />
      <button
        data-testid="duplicate-version-v1"
        disabled={isDuplicating}
        onClick={() => onDuplicateVersionClick('ds-1', 'v1')}
      />
    </div>
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
    source: null,
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

describe('DatastructuresList version deletion', () => {
  const versionRow = (id: string, inUse: boolean) => ({
    id,
    name: `Version ${id}`,
    description: '',
    status: 'DRAFT',
    source: 'OWN',
    versionNumber: null,
    versions: [],
    inUse,
  })
  const datastructureWithVersions = {
    id: 'ds-1',
    name: 'Struct',
    description: '',
    status: null,
    source: null,
    versionNumber: null,
    inUse: true,
    versions: [versionRow('v1', false), versionRow('v2', true)],
  } as unknown as DatastructuresListData

  beforeEach(() => {
    vi.clearAllMocks()
    mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_READ])
  })

  it('asks for confirmation before deleting a version that is not in use', async () => {
    renderComponent({ datastructures: [datastructureWithVersions] })
    await userEvent.click(screen.getByTestId('delete-version-v1'))

    expect(screen.getByText('deleteConfirm.title')).toBeInTheDocument()
    expect(screen.queryByTestId('infoModal')).not.toBeInTheDocument()
  })

  it('explains that a version in use cannot be deleted, without asking to confirm', async () => {
    renderComponent({ datastructures: [datastructureWithVersions] })
    await userEvent.click(screen.getByTestId('delete-version-v2'))

    expect(screen.getByTestId('infoModal')).toHaveTextContent('infoModal.title')
    expect(screen.queryByTestId('confirmButton')).not.toBeInTheDocument()
    expect(mockDeleteVersionMutate).not.toHaveBeenCalled()
  })

  it('deletes the version on confirm and refreshes the list', async () => {
    renderComponent({ datastructures: [datastructureWithVersions] })
    await userEvent.click(screen.getByTestId('delete-version-v1'))
    await userEvent.click(screen.getByTestId('confirmButton'))

    expect(mockDeleteVersionMutate).toHaveBeenCalledWith(
      { data: undefined, endpoint: '/datastructures/ds-1/versions/v1' },
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) }),
    )

    const [, { onSuccess }] = mockDeleteVersionMutate.mock.calls[0]
    onSuccess()

    expect(toast.success).toHaveBeenCalledWith('success.deletionSuccess')
    expect(mockRefresh).toHaveBeenCalled()
  })

  // The list flag can be stale; without this the user would see a generic error for an In Use version.
  it('explains that the version cannot be deleted when the delete answers that it is in use', async () => {
    renderComponent({ datastructures: [datastructureWithVersions] })
    await userEvent.click(screen.getByTestId('delete-version-v1'))
    await userEvent.click(screen.getByTestId('confirmButton'))

    const [, { onError }] = mockDeleteVersionMutate.mock.calls[0]
    act(() => onError(mockApiError(409, 'still referenced', 'urn:civitas:error:RESOURCE_IN_USE')))

    expect(screen.getByTestId('infoModal')).toHaveTextContent('infoModal.title')
    expect(toast.error).not.toHaveBeenCalled()
  })

  it('shows a generic error toast for any other delete failure', async () => {
    renderComponent({ datastructures: [datastructureWithVersions] })
    await userEvent.click(screen.getByTestId('delete-version-v1'))
    await userEvent.click(screen.getByTestId('confirmButton'))

    const [, { onError }] = mockDeleteVersionMutate.mock.calls[0]
    onError(new Error('network failure'))

    expect(toast.error).toHaveBeenCalledWith('errors.deletionError')
  })
})

describe('DatastructuresList version duplication', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockDuplicateVersionState.isPending = false
    mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_READ])
  })

  it('disables duplication in the table while a copy is being made', () => {
    mockDuplicateVersionState.isPending = true
    renderComponent()

    expect(screen.getByTestId('duplicate-version-v1')).toBeDisabled()
  })

  it('duplicates the version and shows a success toast', async () => {
    renderComponent()
    await userEvent.click(screen.getByTestId('duplicate-version-v1'))

    expect(mockDuplicateVersionMutate).toHaveBeenCalledWith(
      { data: undefined, endpoint: '/datastructures/ds-1/versions/v1/duplicate' },
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) }),
    )

    const [, { onSuccess }] = mockDuplicateVersionMutate.mock.calls[0]
    onSuccess()

    expect(toast.success).toHaveBeenCalledWith('messages.duplicateSuccess')
    expect(mockRefresh).toHaveBeenCalled()
  })

  it('shows an error toast when duplication fails', async () => {
    renderComponent()
    await userEvent.click(screen.getByTestId('duplicate-version-v1'))

    const [, { onError }] = mockDuplicateVersionMutate.mock.calls[0]
    onError(new Error('network failure'))

    expect(toast.error).toHaveBeenCalledWith('errors.duplicationError')
  })
})
