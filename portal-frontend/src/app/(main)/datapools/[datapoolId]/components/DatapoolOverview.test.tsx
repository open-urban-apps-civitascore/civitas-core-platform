import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { toast } from 'sonner'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useCreateDatapool, usePatchDatapool } from '@/app/services/api/datapools/clientRequests'
import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'
import { Datapool } from '@/types/datapools'

import { DatapoolOverview } from './DatapoolOverview'

const mockRouterPush = vi.fn()
const mockRouterReplace = vi.fn()
const mockRouterRefresh = vi.fn()
const mockMutate = vi.fn()
const mockMutateAsync = vi.fn()
let mockSearchParams = new URLSearchParams()

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/app/services/api/datapools/clientRequests', () => ({
  useCreateDatapool: vi.fn(),
  usePatchDatapool: vi.fn(),
}))

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
  useGetUsers: vi.fn().mockReturnValue({ data: undefined, isLoading: false }),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockRouterPush, replace: mockRouterReplace, refresh: mockRouterRefresh }),
  useSearchParams: () => mockSearchParams,
  usePathname: () => '/datapools/dp1',
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/contexts/unsaved-changes/UnsavedChangesContext', () => ({
  useUnsavedChanges: () => ({
    setHasUnsavedChanges: vi.fn(),
    setSaveHandler: vi.fn(),
  }),
}))

vi.mock('@/hooks/use-mobile', () => ({
  useIsMobile: () => false,
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

const mockDatapool: Datapool = {
  id: 'dp1',
  name: 'Test Datapool',
  description: 'A description',
  contactPerson: null,
  createdAt: '2024-01-01T00:00:00Z',
  modifiedAt: '2024-01-01T00:00:00Z',
}

const renderComponent = (datapool: Datapool = mockDatapool) => render(<DatapoolOverview datapool={datapool} />)

const renderInEditMode = (datapool: Datapool = mockDatapool) => {
  mockCurrentUser([PERMISSION_NAMES.DATAPOOL_UPDATE])
  mockSearchParams = new URLSearchParams('mode=edit')
  return render(<DatapoolOverview datapool={datapool} />)
}

const editAndSave = async (newName: string) => {
  fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: newName } })
  await waitFor(() => expect(screen.getByTestId('confirmButton')).not.toBeDisabled())
  fireEvent.click(screen.getByTestId('confirmButton'))
}

describe('DatapoolOverview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSearchParams = new URLSearchParams()
    mockCurrentUser([])
    vi.mocked(useCreateDatapool).mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: false,
    } as unknown as ReturnType<typeof useCreateDatapool>)
    vi.mocked(usePatchDatapool).mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as unknown as ReturnType<typeof usePatchDatapool>)
  })

  describe('Rendering', () => {
    it('renders the overview page', () => {
      renderComponent()
      expect(screen.getByTestId('datapoolOverviewPage')).toBeInTheDocument()
    })

    it('shows the datapool name as the page title', () => {
      renderComponent()
      expect(screen.getByText('Test Datapool')).toBeInTheDocument()
    })

    it('shows the Edit button in read-only mode when user has TENANT-scoped DATAPOOL_UPDATE permission', () => {
      mockCurrentUser([PERMISSION_NAMES.DATAPOOL_UPDATE])
      renderComponent()
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
    })

    it('shows the Edit button when user has scoped DATAPOOL_UPDATE permission for this datapool', () => {
      mockCurrentUser([PERMISSION_NAMES.DATAPOOL_UPDATE], 'DATAPOOL', 'dp1')
      renderComponent()
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
    })

    it('does not show the Edit button when user lacks DATAPOOL_UPDATE permission', () => {
      mockCurrentUser([])
      renderComponent()
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
    })

    it('does not show Save/Exit buttons in read-only mode', () => {
      renderComponent()
      expect(screen.queryByTestId('confirmButton')).not.toBeInTheDocument()
      expect(screen.queryByTestId('cancelButton')).not.toBeInTheDocument()
    })
  })

  describe('Edit mode', () => {
    it('switches to edit mode and updates URL when Edit button is clicked', () => {
      mockCurrentUser([PERMISSION_NAMES.DATAPOOL_UPDATE])
      renderComponent()
      fireEvent.click(screen.getByTestId('editButton'))
      expect(mockRouterReplace).toHaveBeenCalledWith('/datapools/dp1?mode=edit', { scroll: false })
    })

    it('shows Save/Exit and hides Edit button when mode=edit param is present', () => {
      renderInEditMode()
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      expect(screen.getByTestId('confirmButton')).toBeInTheDocument()
      expect(screen.getByTestId('cancelButton')).toBeInTheDocument()
    })

    it('stays read-only when mode=edit param is present but the user lacks DATAPOOL_UPDATE', () => {
      mockCurrentUser([])
      mockSearchParams = new URLSearchParams('mode=edit')
      render(<DatapoolOverview datapool={mockDatapool} />)

      expect(screen.queryByTestId('confirmButton')).not.toBeInTheDocument()
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      expect(screen.getByTestId('nameTextField')).toBeDisabled()
    })

    it('enables form fields when mode=edit param is present', () => {
      renderInEditMode()
      expect(screen.getByTestId('nameTextField')).not.toBeDisabled()
      expect(screen.getByTestId('descriptionTextArea')).not.toBeDisabled()
    })

    it('disables the Save button when no changes have been made', () => {
      renderInEditMode()
      expect(screen.getByTestId('confirmButton')).toBeDisabled()
    })

    it('enables the Save button when a field has been changed', async () => {
      renderInEditMode()
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Updated Name' } })
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
    })
  })

  describe('Cancel without changes', () => {
    it('removes mode param from URL when Cancel is clicked without changes', () => {
      renderInEditMode()
      fireEvent.click(screen.getByTestId('cancelButton'))
      expect(mockRouterReplace).toHaveBeenCalledWith('/datapools/dp1', { scroll: false })
    })
  })

  describe('Cancel with unsaved changes', () => {
    const enterEditAndChange = async () => {
      renderInEditMode()
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Changed Name' } })
      await waitFor(() => expect(screen.getByTestId('confirmButton')).not.toBeDisabled())
    }

    it('opens the exit warning modal when Cancel is clicked with unsaved changes', async () => {
      await enterEditAndChange()
      fireEvent.click(screen.getByTestId('cancelButton'))
      await waitFor(() => {
        expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument()
      })
    })

    it('removes mode param from URL and closes modal when Discard is clicked', async () => {
      await enterEditAndChange()
      fireEvent.click(screen.getByTestId('cancelButton'))
      await screen.findByTestId('exitWarningModal')
      fireEvent.click(screen.getByTestId('discardButton'))
      expect(mockRouterReplace).toHaveBeenCalledWith('/datapools/dp1', { scroll: false })
      expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
    })
  })

  describe('Successful Save', () => {
    beforeEach(() => {
      mockMutate.mockImplementation((_data, { onSuccess }) => {
        onSuccess({ data: { ...mockDatapool, name: 'Updated Name' } })
      })
    })

    it('calls patchDatapool with the changed fields', async () => {
      renderInEditMode()
      await editAndSave('Updated Name')
      await waitFor(() => {
        expect(mockMutate).toHaveBeenCalledWith(
          expect.objectContaining({ id: 'dp1', name: 'Updated Name' }),
          expect.any(Object),
        )
      })
    })

    it('only sends dirty fields in the patch payload', async () => {
      renderInEditMode()
      await editAndSave('Updated Name')
      await waitFor(() => {
        expect(mockMutate).toHaveBeenCalledWith({ id: 'dp1', name: 'Updated Name' }, expect.any(Object))
      })
    })

    it('shows a success toast after saving', async () => {
      renderInEditMode()
      await editAndSave('Updated Name')
      await waitFor(() => {
        expect(toast.success).toHaveBeenCalledWith('messages.updateSuccess')
      })
    })

    it('stays in edit mode after a successful save', async () => {
      renderInEditMode()
      await editAndSave('Updated Name')
      await waitFor(() => {
        expect(toast.success).toHaveBeenCalledWith('messages.updateSuccess')
      })
      expect(mockRouterReplace).not.toHaveBeenCalledWith('/datapools/dp1', { scroll: false })
    })
  })

  describe('Failed Save', () => {
    beforeEach(() => {
      mockMutate.mockImplementation((_data, { onError }) => {
        onError(new Error('Server error'))
      })
    })

    it('shows an error toast when the save fails', async () => {
      renderInEditMode()
      await editAndSave('Updated Name')
      await waitFor(() => {
        expect(toast.error).toHaveBeenCalledWith('errors.updateError')
      })
    })

    it('stays in edit mode when the save fails', async () => {
      renderInEditMode()
      await editAndSave('Updated Name')
      await waitFor(() => {
        expect(toast.error).toHaveBeenCalled()
      })
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      expect(screen.getByTestId('confirmButton')).toBeInTheDocument()
    })
  })

  describe('Create mode', () => {
    const defaultDatapool: Datapool = {
      id: '',
      name: '',
      description: '',
      contactPerson: null,
      createdAt: '',
      modifiedAt: '',
    }
    const createdDatapool = {
      id: 'new-id',
      name: 'New Datapool',
      description: 'A description',
      contactPerson: null,
      createdAt: '',
      modifiedAt: '',
    }

    const renderCreateComponent = () => render(<DatapoolOverview datapool={defaultDatapool} isCreateMode={true} />)

    const fillAndSubmit = async () => {
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: createdDatapool.name } })
      fireEvent.change(screen.getByTestId('descriptionTextArea'), { target: { value: createdDatapool.description } })
      await waitFor(() => expect(screen.getByTestId('confirmButton')).not.toBeDisabled())
      fireEvent.click(screen.getByTestId('confirmButton'))
    }

    it('calls createDatapool with the user-entered form data', async () => {
      mockMutateAsync.mockResolvedValue({ data: createdDatapool })
      renderCreateComponent()
      await fillAndSubmit()
      await waitFor(() => {
        expect(mockMutateAsync).toHaveBeenCalledWith({
          name: 'New Datapool',
          description: 'A description',
        })
      })
    })

    it('shows a success toast after creation', async () => {
      mockMutateAsync.mockResolvedValue({ data: createdDatapool })
      renderCreateComponent()
      await fillAndSubmit()
      await waitFor(() => {
        expect(toast.success).toHaveBeenCalledWith('messages.createSuccess')
      })
    })

    it('redirects to the new datapool edit page after creation', async () => {
      mockMutateAsync.mockResolvedValue({ data: createdDatapool })
      renderCreateComponent()
      await fillAndSubmit()
      await waitFor(() => {
        expect(mockRouterPush).toHaveBeenCalledWith('/datapools/new-id?mode=edit')
      })
    })

    it('shows an error toast when creation fails', async () => {
      mockMutateAsync.mockRejectedValue(new Error('Server error'))
      renderCreateComponent()
      await fillAndSubmit()
      await waitFor(() => {
        expect(toast.error).toHaveBeenCalledWith('errors.createError')
      })
    })
  })

  describe('Save from exit warning modal', () => {
    beforeEach(() => {
      mockMutate.mockImplementation((_data, { onSuccess }) => {
        onSuccess({ data: { ...mockDatapool, name: 'Changed Name' } })
      })
    })

    it('saves, exits edit mode and closes the modal when Save is clicked in the exit warning modal', async () => {
      renderInEditMode()
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Changed Name' } })
      await waitFor(() => expect(screen.getByTestId('nameTextField')).toHaveValue('Changed Name'))
      fireEvent.click(screen.getByTestId('cancelButton'))
      await screen.findByTestId('exitWarningModal')
      fireEvent.click(screen.getByTestId('saveButton'))
      await waitFor(() => {
        expect(mockMutate).toHaveBeenCalledWith(
          expect.objectContaining({ id: 'dp1', name: 'Changed Name' }),
          expect.any(Object),
        )
        expect(toast.success).toHaveBeenCalledWith('messages.updateSuccess')
        expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
        expect(mockRouterReplace).toHaveBeenCalledWith('/datapools/dp1', { scroll: false })
      })
    })
  })
})
