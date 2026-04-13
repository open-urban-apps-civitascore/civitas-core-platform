import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useCreateGroup, useReplaceGroupAssignments, useUpdateGroup } from '@/app/services/api/groups/clientRequests'
import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'
import { Group } from '@/types/groups'

import { GroupOverview } from './GroupOverview'

const mockPush = vi.fn()
const mockCreateMutateAsync = vi.fn()
const mockUpdateMutateAsync = vi.fn()
const mockReplaceAssignmentsMutateAsync = vi.fn()
const mockSetSubTabValueParam = vi.fn()

let mockSearchParams = new URLSearchParams()
let mockSubTabValue = 'info'

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
  useGetUsers: vi.fn(() => ({ data: null, isLoading: false })),
}))

vi.mock('@/app/services/api/groups/clientRequests', () => ({
  useCreateGroup: vi.fn(),
  useUpdateGroup: vi.fn(),
  useReplaceGroupAssignments: vi.fn(),
}))

vi.mock('next/navigation', () => ({
  useRouter: vi.fn(() => ({ push: mockPush, refresh: vi.fn() })),
  useSearchParams: vi.fn(() => mockSearchParams),
  usePathname: vi.fn(() => '/groups/1'),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-query-params', async () => {
  const React = await vi.importActual<typeof import('react')>('react')

  return {
    useQueryParams: () => {
      const [subTabValue, setSubTabValue] = React.useState(mockSubTabValue)

      return {
        subTabValue,
        setSubTabValueParam: (value: string) => {
          mockSubTabValue = value
          mockSetSubTabValueParam(value)
          setSubTabValue(value)
        },
      }
    },
  }
})

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('./roles-tab/RolesTab', () => ({
  RolesTab: () => <div data-testid="roles-tab" />,
}))

vi.mock('./users-tab/UsersTab', () => ({
  UsersTab: () => <div data-testid="users-tab" />,
}))

const mockGroupData: Group = {
  id: '1',
  name: 'Test',
  description: '',
  roles: null,
  contactUser: null,
  members: [],
  assignments: [],
  createdAt: '2024-01-01',
  modifiedAt: '2024-01-01',
}

const mockGroupDataWithAssignment: Group = {
  ...mockGroupData,
  assignments: [
    {
      id: 'a1',
      createdAt: '2024-01-01',
      modifiedAt: '2024-01-01',
      group: { id: '1', name: 'Test' },
      role: { id: 'r1', name: 'Admin', roleType: 'SYSTEM', description: '', readonly: false },
      scopeType: 'TENANT',
      scope: null,
    },
  ],
}

const defaultProps = {
  title: 'Test Group',
  groupData: mockGroupData,
  isCreateMode: false,
}

const mockCurrentUser = (permissions: PermissionName[]) => {
  vi.mocked(useGetCurrentUser).mockReturnValue({
    data: {
      username: 'current',
      email: 'current@test.com',
      title: 'MR' as const,
      firstName: 'Current',
      lastName: 'User',
      assignments: [{ scopeType: 'TENANT', scopeId: null, permissions }],
    },
  } as unknown as ReturnType<typeof useGetCurrentUser>)
}

const allPermissions = [PERMISSION_NAMES.USER_READ, PERMISSION_NAMES.GROUP_UPDATE]

const renderComponent = (props = {}) => render(<GroupOverview {...defaultProps} {...props} />)

describe('GroupOverview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSearchParams = new URLSearchParams()
    mockSubTabValue = 'info'
    vi.mocked(useCreateGroup).mockReturnValue({
      mutateAsync: mockCreateMutateAsync,
      isPending: false,
    } as unknown as ReturnType<typeof useCreateGroup>)
    vi.mocked(useUpdateGroup).mockReturnValue({
      mutateAsync: mockUpdateMutateAsync,
      isPending: false,
    } as unknown as ReturnType<typeof useUpdateGroup>)
    vi.mocked(useReplaceGroupAssignments).mockReturnValue({
      mutateAsync: mockReplaceAssignmentsMutateAsync,
      isPending: false,
    } as unknown as ReturnType<typeof useReplaceGroupAssignments>)
    mockCurrentUser(allPermissions)
  })

  describe('Tab visibility', () => {
    it('shows Users tab when user has USER_READ permission', () => {
      mockCurrentUser([PERMISSION_NAMES.USER_READ])
      renderComponent()
      expect(screen.getByTestId('tab-users')).toBeInTheDocument()
    })

    it('hides Users tab when user lacks USER_READ permission', () => {
      mockCurrentUser([])
      renderComponent()
      expect(screen.queryByTestId('tab-users')).not.toBeInTheDocument()
    })

    it('always shows info tab regardless of permissions', () => {
      mockCurrentUser([])
      renderComponent()
      expect(screen.getByTestId('tab-info')).toBeInTheDocument()
    })

    it('always shows roles tab regardless of permissions', () => {
      mockCurrentUser([])
      renderComponent()
      expect(screen.getByTestId('tab-roles')).toBeInTheDocument()
    })
  })

  describe('Edit button visibility', () => {
    it('shows Edit button when user has GROUP_UPDATE permission in read-only mode', () => {
      mockCurrentUser([PERMISSION_NAMES.GROUP_UPDATE])
      renderComponent()
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
    })

    it('hides Edit button when user lacks GROUP_UPDATE permission in read-only mode', () => {
      mockCurrentUser([])
      renderComponent()
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
    })
  })

  describe('Tab rendering', () => {
    it('renders base info form for info tab', () => {
      mockSubTabValue = 'info'
      renderComponent()
      expect(screen.getByTestId('nameTextField')).toBeInTheDocument()
    })

    it('renders base info form as default when subTabValue is empty', () => {
      mockSubTabValue = ''
      renderComponent()
      expect(screen.getByTestId('nameTextField')).toBeInTheDocument()
    })

    it('switches to RolesTab when Roles tab is clicked', async () => {
      renderComponent()
      fireEvent.click(screen.getByTestId('tab-roles'))

      await waitFor(() => {
        expect(mockSetSubTabValueParam).toHaveBeenCalledWith('roles')
        expect(screen.getByTestId('roles-tab')).toBeInTheDocument()
      })
    })

    it('switches to UsersTab when Users tab is clicked', async () => {
      renderComponent()
      fireEvent.click(screen.getByTestId('tab-users'))

      await waitFor(() => {
        expect(mockSetSubTabValueParam).toHaveBeenCalledWith('users')
        expect(screen.getByTestId('users-tab')).toBeInTheDocument()
      })
    })
  })

  describe('Mode initialization', () => {
    it('renders the title passed via props', () => {
      renderComponent({ title: 'My Custom Group' })
      expect(screen.getByText('My Custom Group')).toBeInTheDocument()
    })

    it('starts in read-only mode when isCreateMode=false and no ?mode=edit param', () => {
      renderComponent()
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
      expect(screen.queryByTestId('confirmButton')).not.toBeInTheDocument()
    })

    it('starts in edit mode when isCreateMode=true', () => {
      renderComponent({ isCreateMode: true })
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      expect(screen.getByTestId('confirmButton')).toBeInTheDocument()
      expect(screen.getByTestId('cancelButton')).toBeInTheDocument()
    })

    it('starts in edit mode when URL contains ?mode=edit', () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      expect(screen.getByTestId('confirmButton')).toBeInTheDocument()
    })

    it('shows Save/Exit buttons in create mode without GROUP_UPDATE permission', () => {
      mockCurrentUser([])
      renderComponent({ isCreateMode: true })
      expect(screen.getByTestId('confirmButton')).toBeInTheDocument()
      expect(screen.getByTestId('cancelButton')).toBeInTheDocument()
    })
  })

  describe('Loading state', () => {
    it('hides tab content and shows loading indicator when a mutation is pending', () => {
      vi.mocked(useUpdateGroup).mockReturnValue({
        mutateAsync: mockUpdateMutateAsync,
        isPending: true,
      } as unknown as ReturnType<typeof useUpdateGroup>)
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      expect(screen.queryByTestId('nameTextField')).not.toBeInTheDocument()
      expect(screen.getByText('loading')).toBeInTheDocument()
    })
  })

  describe('Edit/Save/Exit button states', () => {
    it('switches to Save/Exit buttons when Edit button is clicked', () => {
      renderComponent()
      fireEvent.click(screen.getByTestId('editButton'))
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      expect(screen.getByTestId('confirmButton')).toBeInTheDocument()
      expect(screen.getByTestId('cancelButton')).toBeInTheDocument()
    })

    it('Save button is disabled when form is not dirty', () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      expect(screen.getByTestId('confirmButton')).toBeDisabled()
    })

    it('Save button is enabled when form is dirty', async () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Changed Name' } })
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
    })

    it('Save and Exit buttons are disabled while a mutation is loading', () => {
      vi.mocked(useUpdateGroup).mockReturnValue({
        mutateAsync: mockUpdateMutateAsync,
        isPending: true,
      } as unknown as ReturnType<typeof useUpdateGroup>)
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      expect(screen.getByTestId('confirmButton')).toBeDisabled()
      expect(screen.getByTestId('cancelButton')).toBeDisabled()
    })
  })

  describe('ExitWarningModal and Exit and Save click', () => {
    it('ExitWarningModal is initially closed', () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
    })

    it('navigates to /groups and does not open ExitWarningModal when Exit is clicked in create mode', () => {
      renderComponent({ isCreateMode: true })
      fireEvent.click(screen.getByTestId('cancelButton'))
      expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
      expect(mockPush).toHaveBeenCalledWith('/groups')
    })

    it('resets to read-only view and does not open ExitWarningModal when Exit is clicked in edit mode with clean form', () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      fireEvent.click(screen.getByTestId('cancelButton'))
      expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
      expect(screen.queryByTestId('confirmButton')).not.toBeInTheDocument()
    })

    it('opens ExitWarningModal when Exit is clicked and form is dirty', async () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Changed Name' } })
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
      fireEvent.click(screen.getByTestId('cancelButton'))
      await waitFor(() => {
        expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument()
      })
    })

    it('clicking Discard resets changes and returns to read-only view', async () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Changed Name' } })
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
      fireEvent.click(screen.getByTestId('cancelButton'))
      await waitFor(() => {
        expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument()
      })
      fireEvent.click(screen.getByTestId('discardButton'))
      await waitFor(() => {
        expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
        expect(screen.getByTestId('editButton')).toBeInTheDocument()
      })
    })

    it('clicking Save in ExitWarningModal triggers save and closes ExitWarningModal on success', async () => {
      mockUpdateMutateAsync.mockResolvedValue({ data: { ...mockGroupData } })
      mockReplaceAssignmentsMutateAsync.mockResolvedValue({ data: { ...mockGroupData } })
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Changed Name' } })
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
      fireEvent.click(screen.getByTestId('cancelButton'))
      await waitFor(() => {
        expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument()
      })
      fireEvent.click(screen.getByTestId('saveButton'))
      await waitFor(() => {
        expect(mockUpdateMutateAsync).toHaveBeenCalled()
        expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
      })
    })
  })

  describe('Create Group', () => {
    it('calls createGroup.mutateAsync with correct data on save', async () => {
      mockCreateMutateAsync.mockResolvedValue({ data: { id: '2' } })
      renderComponent({ isCreateMode: true })
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'New Group' } })
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(mockCreateMutateAsync).toHaveBeenCalledWith(expect.objectContaining({ name: 'New Group' }))
      })
    })

    it('shows success toast after successful create', async () => {
      const { toast } = await import('sonner')
      mockCreateMutateAsync.mockResolvedValue({ data: { id: '2' } })
      renderComponent({ isCreateMode: true })
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'New Group' } })
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(toast.success).toHaveBeenCalledWith('messages.createSuccess')
      })
    })

    it('navigates to /groups/{id}?mode=edit after successful create', async () => {
      mockCreateMutateAsync.mockResolvedValue({ data: { id: '42' } })
      renderComponent({ isCreateMode: true })
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'New Group' } })
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(mockPush).toHaveBeenCalledWith('/groups/42?mode=edit')
      })
    })

    it('shows error toast on failed create', async () => {
      const { toast } = await import('sonner')
      mockCreateMutateAsync.mockRejectedValue(new Error('create failed'))
      renderComponent({ isCreateMode: true })
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'New Group' } })
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(toast.error).toHaveBeenCalledWith('errors.createError')
      })
    })

    it('shows name conflict toast and form field error on name conflict during create', async () => {
      const { toast } = await import('sonner')
      const nameConflictError = {
        isAxiosError: true,
        status: 409,
        response: {
          data: { detail: 'Group with name "New Group" already exists' },
        },
      }
      mockCreateMutateAsync.mockRejectedValue(nameConflictError)
      renderComponent({ isCreateMode: true })
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'New Group' } })
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(toast.error).toHaveBeenCalledWith('errors.nameExistsToast')
        expect(screen.getByTestId('nameFormMessage')).toBeInTheDocument()
      })
    })

    it('calls replaceAssignments after create when group has assignments', async () => {
      mockCreateMutateAsync.mockResolvedValue({ data: { id: '42' } })
      mockReplaceAssignmentsMutateAsync.mockResolvedValue({ data: mockGroupDataWithAssignment })
      renderComponent({ isCreateMode: true, groupData: mockGroupDataWithAssignment })
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'New Group' } })
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(mockReplaceAssignmentsMutateAsync).toHaveBeenCalledWith(expect.objectContaining({ groupId: '42' }))
      })
    })

    it('shows assignment error toast but still navigates when assignment save fails during create', async () => {
      const { toast } = await import('sonner')
      mockCreateMutateAsync.mockResolvedValue({ data: { id: '42' } })
      mockReplaceAssignmentsMutateAsync.mockRejectedValue(new Error('assignment error'))
      renderComponent({ isCreateMode: true, groupData: mockGroupDataWithAssignment })
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'New Group' } })
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(toast.error).toHaveBeenCalledWith('errors.assignmentSaveError')
        expect(mockPush).toHaveBeenCalledWith('/groups/42?mode=edit')
      })
    })
  })

  describe('Update Group', () => {
    it('calls updateGroup.mutateAsync and replaceAssignments on save', async () => {
      mockUpdateMutateAsync.mockResolvedValue({ data: { ...mockGroupData } })
      mockReplaceAssignmentsMutateAsync.mockResolvedValue({ data: { ...mockGroupData } })
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Updated Name' } })
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(mockUpdateMutateAsync).toHaveBeenCalled()
        expect(mockReplaceAssignmentsMutateAsync).toHaveBeenCalled()
      })
    })

    it('shows success toast after successful update', async () => {
      const { toast } = await import('sonner')
      mockUpdateMutateAsync.mockResolvedValue({ data: { ...mockGroupData } })
      mockReplaceAssignmentsMutateAsync.mockResolvedValue({ data: { ...mockGroupData } })
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Updated Name' } })
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(toast.success).toHaveBeenCalledWith('messages.updateSuccess')
      })
    })

    it('shows error toast on failed update', async () => {
      const { toast } = await import('sonner')
      mockUpdateMutateAsync.mockRejectedValue(new Error('update failed'))
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Updated Name' } })
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(toast.error).toHaveBeenCalledWith('errors.updateError')
      })
    })

    it('shows name conflict toast and form field error on name conflict during update', async () => {
      const { toast } = await import('sonner')
      const nameConflictError = {
        isAxiosError: true,
        status: 409,
        response: {
          data: { detail: 'Group with name "Updated Name" already exists' },
        },
      }
      mockUpdateMutateAsync.mockRejectedValue(nameConflictError)
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Updated Name' } })
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(toast.error).toHaveBeenCalledWith('errors.nameExistsToast')
        expect(screen.getByTestId('nameFormMessage')).toBeInTheDocument()
      })
    })
  })
})
