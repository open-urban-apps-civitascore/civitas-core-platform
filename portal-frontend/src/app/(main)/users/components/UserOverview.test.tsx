import { fireEvent, render, screen, waitFor } from '@testing-library/react'

import {
  useCreateUser,
  useGetCurrentUser,
  useReplaceUserGroups,
  useUpdateUser,
} from '@/app/services/api/users/clientRequests'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'
import { User } from '@/types/users'

import { UserOverview } from './UserOverview'

const mockPush = vi.fn()
const mockRefresh = vi.fn()
const mockSetSubTabValueParam = vi.fn()
const mockCreateMutateAsync = vi.fn()
const mockUpdateMutateAsync = vi.fn()
const mockInvalidateQueries = vi.fn()

let mockSubTabValue = ''
let mockSearchParams = new URLSearchParams()

vi.mock('next/navigation', () => ({
  useRouter: vi.fn(() => ({ push: mockPush, refresh: mockRefresh })),
  useSearchParams: vi.fn(() => mockSearchParams),
  usePathname: vi.fn(() => '/users/1'),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: () => ({
    setSubTabValueParam: mockSetSubTabValueParam,
    subTabValue: mockSubTabValue,
    getApiRequestParams: vi.fn(() => new URLSearchParams()),
  }),
}))

const mockReplaceUserGroupsMutateAsync = vi.fn()

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useCreateUser: vi.fn(() => ({ mutateAsync: mockCreateMutateAsync, isPending: false })),
  useUpdateUser: vi.fn(() => ({ mutateAsync: mockUpdateMutateAsync, isPending: false })),
  useReplaceUserGroups: vi.fn(() => ({ mutateAsync: mockReplaceUserGroupsMutateAsync, isPending: false })),
  useGetCurrentUser: vi.fn(),
}))

vi.mock('@/app/services/api/groups/clientRequests', () => ({
  useGetGroups: vi.fn(() => ({ data: { data: [], totalElements: 0 }, isFetching: false, error: null })),
}))

vi.mock('@/app/services/api/request/apiRequest', () => ({
  apiRequest: vi.fn(() => Promise.resolve({ data: {} })),
}))

vi.mock('@/app/services/api/assignments/clientRequests', () => ({
  useGetAssignments: vi.fn(() => ({ data: { data: [], totalElements: 0 }, isFetching: false, error: null })),
}))

vi.mock('@tanstack/react-query', async () => {
  const actual = await vi.importActual('@tanstack/react-query')
  return {
    ...actual,
    useQueryClient: () => ({ invalidateQueries: mockInvalidateQueries }),
  }
})

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

let capturedOnAssignGroups: ((groupIds: string[]) => void) | null = null

vi.mock('./groups-tab/GroupsTab', () => ({
  GroupsTab: (props: { onAssignGroups: (groupIds: string[]) => void }) => {
    capturedOnAssignGroups = props.onAssignGroups
    return <div data-testid="groupsTab">GroupsTab</div>
  },
}))

vi.mock('./roles-tab/RolesTab', () => ({
  RolesTab: () => <div data-testid="rolesTab">RolesTab</div>,
}))

const mockUserData: User = {
  id: '1',
  email: 'test@test.com',
  title: 'MR',
  firstName: 'Test',
  lastName: 'User',
  phone: null,
  groups: [],
}

const defaultProps = {
  title: 'Test User',
  userData: mockUserData,
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

const allPermissions = [PERMISSION_NAMES.USER_UPDATE, PERMISSION_NAMES.GROUP_READ, PERMISSION_NAMES.ASSIGNMENT_READ]

const renderComponent = (props = {}) => render(<UserOverview {...defaultProps} {...props} />)

describe('UserOverview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSubTabValue = ''
    mockSearchParams = new URLSearchParams()
    vi.mocked(useCreateUser).mockReturnValue({
      mutateAsync: mockCreateMutateAsync,
      isPending: false,
    } as unknown as ReturnType<typeof useCreateUser>)
    vi.mocked(useUpdateUser).mockReturnValue({
      mutateAsync: mockUpdateMutateAsync,
      isPending: false,
    } as unknown as ReturnType<typeof useUpdateUser>)
    vi.mocked(useReplaceUserGroups).mockReturnValue({
      mutateAsync: mockReplaceUserGroupsMutateAsync,
      isPending: false,
    } as unknown as ReturnType<typeof useReplaceUserGroups>)
    mockCurrentUser(allPermissions)
  })

  describe('Rendering', () => {
    it('renders the page', () => {
      renderComponent({ testId: 'user-overview-container' })
      expect(screen.getByTestId('user-overview-container')).toBeInTheDocument()
    })

    it('shows UserData tab as default', () => {
      renderComponent()
      expect(screen.getByTestId('tab-userData')).toBeInTheDocument()
      expect(screen.getByTestId('userDetailsForm')).toBeInTheDocument()
    })

    it('starts in read-only mode when isCreateMode=false and no ?mode=edit', () => {
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
  })

  describe('Tab Navigation', () => {
    it('renders UserBasicInfoTab when subTabValue is userData', () => {
      mockSubTabValue = 'userData'
      renderComponent()
      expect(screen.getByTestId('userDetailsForm')).toBeInTheDocument()
    })

    it('renders UserBasicInfoTab when subTabValue is empty (default)', () => {
      mockSubTabValue = ''
      renderComponent()
      expect(screen.getByTestId('userDetailsForm')).toBeInTheDocument()
    })

    it('renders GroupsTab when subTabValue is groups', () => {
      mockSubTabValue = 'groups'
      renderComponent()
      expect(screen.getByTestId('groupsTab')).toBeInTheDocument()
    })

    it('renders RolesTab when subTabValue is roles', () => {
      mockSubTabValue = 'roles'
      renderComponent()
      expect(screen.getByTestId('rolesTab')).toBeInTheDocument()
    })

    it('calls setSubTabValueParam on tab change', () => {
      renderComponent()
      const groupsTab = screen.getByTestId('tab-groups')
      fireEvent.click(groupsTab)
      expect(mockSetSubTabValueParam).toHaveBeenCalledWith('groups')
    })
  })

  describe('View based on user permissions', () => {
    it('shows Groups tab only with GROUP_READ permission', () => {
      mockCurrentUser([PERMISSION_NAMES.GROUP_READ])
      renderComponent()
      expect(screen.getByTestId('tab-groups')).toBeInTheDocument()
    })

    it('hides Groups tab without GROUP_READ permission', () => {
      mockCurrentUser([])
      renderComponent()
      expect(screen.queryByTestId('tab-groups')).not.toBeInTheDocument()
    })

    it('shows Roles tab only with ASSIGNMENT_READ permission', () => {
      mockCurrentUser([PERMISSION_NAMES.ASSIGNMENT_READ])
      renderComponent()
      expect(screen.getByTestId('tab-roles')).toBeInTheDocument()
    })

    it('disables the Roles tab in create mode', () => {
      mockCurrentUser(allPermissions)
      renderComponent({ isCreateMode: true })
      expect(screen.getByTestId('tab-roles')).toBeDisabled()
    })

    it('hides Roles tab without ASSIGNMENT_READ permission', () => {
      mockCurrentUser([])
      renderComponent()
      expect(screen.queryByTestId('tab-roles')).not.toBeInTheDocument()
    })

    it('shows Edit button with USER_UPDATE permission in read-only mode', () => {
      mockCurrentUser([PERMISSION_NAMES.USER_UPDATE])
      renderComponent()
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
    })

    it('hides Edit button without USER_UPDATE permission in read-only mode', () => {
      mockCurrentUser([])
      renderComponent()
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
    })

    it('shows Save/Exit buttons in create mode without USER_UPDATE permission', () => {
      mockCurrentUser([])
      renderComponent({ isCreateMode: true })
      expect(screen.getByTestId('confirmButton')).toBeInTheDocument()
      expect(screen.getByTestId('cancelButton')).toBeInTheDocument()
    })
  })

  describe('Toggle Read only mode', () => {
    it('shows Save/Exit buttons in edit mode', () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      expect(screen.getByTestId('confirmButton')).toBeInTheDocument()
      expect(screen.getByTestId('cancelButton')).toBeInTheDocument()
    })

    it('switches to Save/Exit buttons when Edit button is clicked', () => {
      renderComponent()
      fireEvent.click(screen.getByTestId('editButton'))
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      expect(screen.getByTestId('confirmButton')).toBeInTheDocument()
      expect(screen.getByTestId('cancelButton')).toBeInTheDocument()
    })
  })

  describe('Button States', () => {
    it('Save button is disabled when form is not dirty', () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      expect(screen.getByTestId('confirmButton')).toBeDisabled()
    })

    it('Save button is enabled when form is dirty', async () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      const firstNameInput = screen.getByTestId('firstNameTextField')
      fireEvent.change(firstNameInput, { target: { value: 'Changed' } })
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
    })

    it('Save and Cancel/Exit buttons are disabled while isLoading', () => {
      vi.mocked(useUpdateUser).mockReturnValue({
        mutateAsync: mockUpdateMutateAsync,
        isPending: true,
      } as unknown as ReturnType<typeof useUpdateUser>)
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      expect(screen.getByTestId('confirmButton')).toBeDisabled()
      expect(screen.getByTestId('cancelButton')).toBeDisabled()
    })
  })

  describe('Dirty State Logik', () => {
    it('ignores whitespace differences in phone number for dirty check', async () => {
      const userWithPhone: User = { ...mockUserData, phone: '+49 123 456' }
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent({ userData: userWithPhone })
      fireEvent.change(screen.getByTestId('phoneTextField'), { target: { value: '+49123456' } })
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).toBeDisabled()
      })
    })

    it('form is dirty after group change', async () => {
      mockSubTabValue = 'groups'
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()

      capturedOnAssignGroups?.(['new-group-id'])

      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
    })
  })

  describe('Form Validation', () => {
    it('shows error when firstName is less than 2 characters', async () => {
      renderComponent({ isCreateMode: true })
      fireEvent.change(screen.getByTestId('firstNameTextField'), { target: { value: 'A' } })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(screen.getByTestId('firstNameFormMessage')).toBeInTheDocument()
      })
    })

    it('shows error when lastName is less than 2 characters', async () => {
      renderComponent({ isCreateMode: true })
      fireEvent.change(screen.getByTestId('lastNameTextField'), { target: { value: 'B' } })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(screen.getByTestId('lastNameFormMessage')).toBeInTheDocument()
      })
    })

    it('shows error for invalid email', async () => {
      renderComponent({ isCreateMode: true })
      fireEvent.change(screen.getByTestId('emailTextField'), { target: { value: 'not-an-email' } })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(screen.getByTestId('emailFormMessage')).toBeInTheDocument()
      })
    })

    it('shows error for invalid phone', async () => {
      renderComponent({ isCreateMode: true })
      fireEvent.change(screen.getByTestId('phoneTextField'), { target: { value: 'not-a-phone-number' } })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(screen.getByTestId('phoneFormMessage')).toBeInTheDocument()
      })
    })
  })

  describe('Create User', () => {
    const validUserData: User = {
      ...mockUserData,
      id: '',
      firstName: 'Test',
      lastName: 'User2',
      email: 'test.user2@test.com',
      phone: null,
    }

    it('calls createUser.mutate with correct data', async () => {
      renderComponent({ isCreateMode: true, userData: validUserData })
      fireEvent.change(screen.getByTestId('firstNameTextField'), { target: { value: 'NewFirst' } })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(mockCreateMutateAsync).toHaveBeenCalledWith(
          expect.objectContaining({
            firstName: 'NewFirst',
            email: 'test.user2@test.com',
          }),
        )
      })
    })

    it('sends phone: null when phone is empty', async () => {
      renderComponent({ isCreateMode: true, userData: validUserData })
      fireEvent.change(screen.getByTestId('firstNameTextField'), { target: { value: 'NewFirst' } })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(mockCreateMutateAsync).toHaveBeenCalledWith(expect.objectContaining({ phone: null }))
      })
    })

    it('shows success toast after create', async () => {
      const { toast } = await import('sonner')
      mockCreateMutateAsync.mockResolvedValue({ data: { id: '42' } })
      renderComponent({ isCreateMode: true, userData: validUserData })
      fireEvent.change(screen.getByTestId('firstNameTextField'), { target: { value: 'NewFirst' } })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(toast.success).toHaveBeenCalledWith('messages.createSuccess')
      })
    })

    it('navigates to /users/{id}?mode=edit after create', async () => {
      mockCreateMutateAsync.mockResolvedValue({ data: { id: '42' } })
      renderComponent({ isCreateMode: true, userData: validUserData })
      fireEvent.change(screen.getByTestId('firstNameTextField'), { target: { value: 'NewFirst' } })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(mockPush).toHaveBeenCalledWith('/users/42?mode=edit')
      })
    })

    it('shows error toast on failed create', async () => {
      const { toast } = await import('sonner')
      mockCreateMutateAsync.mockRejectedValue({ status: 500, response: { data: { message: 'error' } } })
      renderComponent({ isCreateMode: true, userData: validUserData })
      fireEvent.change(screen.getByTestId('firstNameTextField'), { target: { value: 'NewFirst' } })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(toast.error).toHaveBeenCalledWith('errors.creationError')
      })
    })
  })

  describe('Update User', () => {
    it('calls updateUser.mutateAsync with only dirty fields', async () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      fireEvent.change(screen.getByTestId('firstNameTextField'), { target: { value: 'Updated' } })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        const calledData = mockUpdateMutateAsync.mock.calls[0][0]
        expect(calledData).toEqual({ id: '1', firstName: 'Updated' })
      })
    })

    it('sends phone: null when phone is cleared and dirty', async () => {
      const userWithPhone: User = { ...mockUserData, phone: '+491234567890' }
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent({ userData: userWithPhone })
      fireEvent.change(screen.getByTestId('phoneTextField'), { target: { value: '' } })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(mockUpdateMutateAsync).toHaveBeenCalledWith(expect.objectContaining({ phone: null }))
      })
    })

    it('shows success toast after update', async () => {
      const { toast } = await import('sonner')
      mockUpdateMutateAsync.mockResolvedValue({ data: { ...mockUserData, firstName: 'Updated' } })
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      fireEvent.change(screen.getByTestId('firstNameTextField'), { target: { value: 'Updated' } })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(toast.success).toHaveBeenCalledWith('messages.updateSuccess')
      })
    })

    it('shows error toast on failed update', async () => {
      const { toast } = await import('sonner')
      mockUpdateMutateAsync.mockRejectedValue({ status: 500, response: { data: { message: 'error' } } })
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      fireEvent.change(screen.getByTestId('firstNameTextField'), { target: { value: 'Updated' } })
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(toast.error).toHaveBeenCalledWith('errors.updateError')
      })
    })
  })

  describe('Exit Warning Modal', () => {
    it('opens modal when Exit is clicked and form is dirty', async () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      fireEvent.change(screen.getByTestId('firstNameTextField'), { target: { value: 'Changed' } })
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
      fireEvent.click(screen.getByTestId('cancelButton'))
      await waitFor(() => {
        expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument()
      })
    })

    it('does not open modal when Exit is clicked and form is clean — directly resets to read-only', () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      fireEvent.click(screen.getByTestId('cancelButton'))
      expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
    })

    it('clicking Discard discards unsaved changes and returns to read-only view', async () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      fireEvent.change(screen.getByTestId('firstNameTextField'), { target: { value: 'Changed' } })
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
        expect(screen.getByTestId('firstNameTextField')).toHaveValue('Test')
      })
    })

    it('clicking Save in modal triggers update and closes modal on success', async () => {
      mockUpdateMutateAsync.mockResolvedValue({ data: { ...mockUserData, firstName: 'Changed' } })
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()
      fireEvent.change(screen.getByTestId('firstNameTextField'), { target: { value: 'Changed' } })
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
})
