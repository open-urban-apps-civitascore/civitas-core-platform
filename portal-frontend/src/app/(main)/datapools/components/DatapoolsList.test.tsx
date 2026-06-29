import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { toast } from 'sonner'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useDeleteDatapool } from '@/app/services/api/datapools/clientRequests'
import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'
import { DatapoolSummary } from '@/types/datapools'

import DatapoolsList from './DatapoolsList'

const mockPush = vi.fn()
const mockRefresh = vi.fn()
const mockDeleteMutate = vi.fn()

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/app/services/api/datapools/clientRequests', () => ({
  useDeleteDatapool: vi.fn(),
}))

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, refresh: mockRefresh }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: vi.fn(() => '/datapools'),
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
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
    totalPages: 1,
  }),
}))

const mockCurrentUser = (
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
}

const datapoolWithDatasets: DatapoolSummary = {
  id: 'dp1',
  name: 'Datapool 1',
  description: 'Description 1',
  contactPerson: null,
  datasets: ['d1'],
  createdAt: '2024-01-01T00:00:00Z',
  modifiedAt: '2024-01-01T00:00:00Z',
}

const datapoolWithoutDatasets: DatapoolSummary = {
  id: 'dp2',
  name: 'Empty Datapool',
  description: 'Description 2',
  contactPerson: null,
  datasets: [],
  createdAt: '2024-01-01T00:00:00Z',
  modifiedAt: '2024-01-01T00:00:00Z',
}

const renderComponent = (datapools: DatapoolSummary[] = []) =>
  render(<DatapoolsList datapools={datapools} rowCount={datapools.length} />)

describe('DatapoolsList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useDeleteDatapool).mockReturnValue({
      mutate: mockDeleteMutate,
      isPending: false,
    } as unknown as ReturnType<typeof useDeleteDatapool>)
    mockCurrentUser([PERMISSION_NAMES.DATAPOOL_CREATE, PERMISSION_NAMES.DATAPOOL_DELETE])
  })

  describe('Basic Structure', () => {
    it('renders the page', () => {
      renderComponent()
      expect(screen.getByTestId('datapoolsPage')).toBeInTheDocument()
    })

    it('renders the page title', () => {
      renderComponent()
      expect(screen.getByText('title')).toBeInTheDocument()
    })

    it('renders the Create button when user has DATAPOOL_CREATE permission', () => {
      renderComponent()
      expect(screen.getByTestId('addDatapoolButton')).toBeInTheDocument()
    })

    it('does not render the Create button when user lacks DATAPOOL_CREATE permission', () => {
      mockCurrentUser([])
      renderComponent()
      expect(screen.queryByTestId('addDatapoolButton')).not.toBeInTheDocument()
    })

    it('does not render the Create button when user has DATAPOOL_CREATE only via resource-scoped assignment', () => {
      mockCurrentUser([PERMISSION_NAMES.DATAPOOL_CREATE], 'DATAPOOL', 'dp1')
      renderComponent()
      expect(screen.queryByTestId('addDatapoolButton')).not.toBeInTheDocument()
    })

    it('navigates to the Create page when clicking on Create', () => {
      renderComponent()
      fireEvent.click(screen.getByTestId('addDatapoolButton'))
      expect(mockPush).toHaveBeenCalledWith('datapools/create?')
    })
  })

  describe('Delete-Flow: Datapool with Datasets', () => {
    it('opens the InfoModal instead of the WarningModal when the datapool has datasets', async () => {
      const user = userEvent.setup()
      renderComponent([datapoolWithDatasets])
      await user.click(screen.getByRole('button', { name: 'Open menu' }))
      await user.click(await screen.findByText('actions.delete'))
      expect(screen.getByText('infoModal.title')).toBeInTheDocument()
      expect(screen.queryByText('deleteModal.title')).not.toBeInTheDocument()
    })

    it('does not call deleteDatapool', async () => {
      const user = userEvent.setup()
      renderComponent([datapoolWithDatasets])
      await user.click(screen.getByRole('button', { name: 'Open menu' }))
      await user.click(await screen.findByText('actions.delete'))
      expect(mockDeleteMutate).not.toHaveBeenCalled()
    })

    it('closes the InfoModal when clicking on Close', async () => {
      const user = userEvent.setup()
      renderComponent([datapoolWithDatasets])
      await user.click(screen.getByRole('button', { name: 'Open menu' }))
      await user.click(await screen.findByText('actions.delete'))
      expect(screen.queryByText('infoModal.title')).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: 'actions.close' }))
      expect(screen.queryByText('infoModal.title')).not.toBeInTheDocument()
    })
  })

  describe('Delete-Flow: Datapool without Datasets', () => {
    it('opens the WarningModal when the datapool has no datasets', async () => {
      const user = userEvent.setup()
      renderComponent([datapoolWithoutDatasets])
      await user.click(screen.getByRole('button', { name: 'Open menu' }))
      await user.click(await screen.findByText('actions.delete'))
      expect(screen.getByText('deleteModal.title')).toBeInTheDocument()
      expect(screen.queryByText('infoModal.title')).not.toBeInTheDocument()
    })

    it('closes the WarningModal when clicking on Cancel', async () => {
      const user = userEvent.setup()
      renderComponent([datapoolWithoutDatasets])
      await user.click(screen.getByRole('button', { name: 'Open menu' }))
      await user.click(await screen.findByText('actions.delete'))
      await user.click(screen.getByRole('button', { name: 'cancel' }))
      expect(screen.queryByText('deleteModal.title')).not.toBeInTheDocument()
    })

    const openWarningModal = async (user: ReturnType<typeof userEvent.setup>) => {
      await user.click(screen.getByRole('button', { name: 'Open menu' }))
      await user.click(await screen.findByText('actions.delete'))
      await screen.findByText('deleteModal.title')
    }

    it('calls deleteDatapool with the correct id', async () => {
      const user = userEvent.setup()
      renderComponent([datapoolWithoutDatasets])
      await openWarningModal(user)
      await user.click(screen.getByTestId('confirmButton'))
      expect(mockDeleteMutate).toHaveBeenCalledWith('dp2', expect.any(Object))
    })

    it('shows Success-Toast and refreshes after successful delete', async () => {
      const user = userEvent.setup()
      mockDeleteMutate.mockImplementation((_id: string, { onSuccess }: { onSuccess: () => void }) => onSuccess())

      renderComponent([datapoolWithoutDatasets])
      await openWarningModal(user)
      await user.click(screen.getByTestId('confirmButton'))

      await waitFor(() => {
        expect(toast.success).toHaveBeenCalledWith('messages.deleteSuccess')
        expect(mockRefresh).toHaveBeenCalled()
      })
    })

    it('closes the WarningModal after successful delete', async () => {
      const user = userEvent.setup()
      mockDeleteMutate.mockImplementation((_id: string, { onSuccess }: { onSuccess: () => void }) => onSuccess())

      renderComponent([datapoolWithoutDatasets])
      await openWarningModal(user)
      await user.click(screen.getByTestId('confirmButton'))

      await waitFor(() => {
        expect(screen.queryByText('deleteModal.title')).not.toBeInTheDocument()
      })
    })

    it('shows Error-Toast when delete fails', async () => {
      const user = userEvent.setup()
      mockDeleteMutate.mockImplementation((_id: string, { onError }: { onError: () => void }) => onError())

      renderComponent([datapoolWithoutDatasets])
      await openWarningModal(user)
      await user.click(screen.getByTestId('confirmButton'))

      await waitFor(() => {
        expect(toast.error).toHaveBeenCalledWith('errors.deleteError')
      })
    })
  })
})
