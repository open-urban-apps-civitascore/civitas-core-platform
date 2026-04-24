import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { AxiosError, AxiosHeaders, InternalAxiosRequestConfig } from 'axios'
import { toast } from 'sonner'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetAssignments } from '@/app/services/api/assignments/clientRequests'
import { usePermissions } from '@/hooks/use-permissions'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'
import { Role } from '@/types/roles'

import { RoleDetails } from './RoleDetails'

const { mockT } = vi.hoisted(() => {
  const mockT = Object.assign((k: string) => k, { has: () => false })
  return { mockT }
})

const mockPush = vi.fn()
let mockSubTabValue = 'basicInformation'
let mockTabValue = 'SYSTEM'
const mockSetSubTabValueParam = vi.fn()
const mockCreateMutateAsync = vi.fn()
const mockUpdateMutateAsync = vi.fn()
const mockDeleteMutate = vi.fn()
let mockRoleResponse: { data: { data: Role } | null; isFetching: boolean; refetch: () => void; error: unknown } = {
  data: null,
  isFetching: false,
  refetch: vi.fn(),
  error: null,
}

let capturedOnGroupAssignmentUpdate: ((groupIds: string[]) => void) | null = null

vi.mock('next-intl', () => ({ useTranslations: () => mockT }))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: vi.fn(),
}))

vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: () => ({
    setSubTabValueParam: mockSetSubTabValueParam,
    subTabValue: mockSubTabValue,
    tabValue: mockTabValue,
    totalPages: 0,
    setTotalPages: vi.fn(),
    getApiRequestParams: vi.fn(() => new URLSearchParams()),
  }),
}))

vi.mock('@/hooks/use-permissions', () => ({ usePermissions: vi.fn() }))

vi.mock('@/hooks/use-register-unsaved-changes', () => ({
  useRegisterUnsavedChanges: vi.fn(),
}))

vi.mock('@/app/services/api/roles/clientRequests', () => ({
  useGetRole: vi.fn(() => mockRoleResponse),
  useGetRoles: vi.fn(() => ({ data: null, isFetching: false, error: null })),
  useCreateRole: vi.fn(() => ({ mutateAsync: mockCreateMutateAsync, isPending: false })),
  useUpdateRole: vi.fn(() => ({ mutateAsync: mockUpdateMutateAsync, isPending: false })),
  useDeleteRole: vi.fn(() => ({ mutate: mockDeleteMutate, isPending: false, isSuccess: false })),
}))

const mockPermissionsResponseData = {
  data: [{ id: 'perm-1', name: 'READ', permissionType: 'SYSTEM', category: 'ADMIN' }],
}

vi.mock('@/app/services/api/permissions/clientRequests', () => ({
  useGetPermissions: vi.fn(() => ({
    data: mockPermissionsResponseData,
    isFetching: false,
    error: null,
  })),
}))

vi.mock('@/app/services/api/assignments/clientRequests', () => ({
  useGetAssignments: vi.fn(() => ({ data: null, refetch: vi.fn(), isFetching: false, error: null })),
  useCreateAssignment: vi.fn(() => ({ mutateAsync: vi.fn(), isPending: false })),
  useDeleteAssignment: vi.fn(() => ({ mutateAsync: vi.fn(), isPending: false })),
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('../page', () => ({ DEFAULT_TAB: 'SYSTEM' }))

vi.mock('@/hooks/use-mobile', () => ({ useIsMobile: vi.fn(() => false) }))

vi.mock('./group-assignment-tab/GroupAssignmentTab', () => ({
  GroupAssignmentTab: (props: { onGroupAssignmentUpdate: (groupIds: string[]) => void }) => {
    capturedOnGroupAssignmentUpdate = props.onGroupAssignmentUpdate
    return <div data-testid="groupAssignmentTab" />
  },
}))

const mockHasPermission = (permissions: PermissionName[]) => {
  vi.mocked(usePermissions).mockReturnValue({
    hasPermission: (permission: string) => permissions.includes(permission as PermissionName),
    hasAnyPermission: (...perms: string[]) => perms.some(p => permissions.includes(p as PermissionName)),
    hasScopedPermission: () => false,
  })
}

const makeAxiosError = (status: number, detail?: string) =>
  new AxiosError(
    'request failed',
    undefined,
    { headers: new AxiosHeaders(), method: 'POST', url: '/roles' } as InternalAxiosRequestConfig,
    undefined,
    {
      status,
      statusText: '',
      headers: new AxiosHeaders(),
      config: { headers: new AxiosHeaders(), method: 'POST', url: '/roles' } as InternalAxiosRequestConfig,
      data: { detail },
    },
  )

const mockRole: Role = {
  id: 'role-1',
  name: 'Test Role',
  description: 'Test description',
  roleType: 'SYSTEM',
  permissions: [],
  readonly: false,
  modifiedBy: null,
  modifiedAt: null,
  createdAt: new Date().toISOString(),
  groupCount: 0,
  userCount: 0,
}

const allPermissions: PermissionName[] = [
  PERMISSION_NAMES.ROLE_UPDATE,
  PERMISSION_NAMES.ROLE_DELETE,
  PERMISSION_NAMES.PERMISSION_READ,
  PERMISSION_NAMES.ASSIGNMENT_READ,
  PERMISSION_NAMES.GROUP_READ,
]

describe('RoleDetails', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    capturedOnGroupAssignmentUpdate = null
    mockSubTabValue = 'basicInformation'
    mockTabValue = 'SYSTEM'
    mockRoleResponse = { data: null, isFetching: false, refetch: vi.fn(), error: null }
    mockCreateMutateAsync.mockResolvedValue({ data: { id: 'new-role-id' } })
    mockUpdateMutateAsync.mockResolvedValue({ data: mockRole })
    mockHasPermission(allPermissions)
  })

  describe('Tab rendering based on subTabValue', () => {
    it('renders BaseInfoTab when subTabValue is basicInformation', () => {
      mockSubTabValue = 'basicInformation'
      render(<RoleDetails roleId="role-1" />)
      expect(screen.getByTestId('baseInfoForm')).toBeInTheDocument()
      expect(screen.queryByTestId('permissionsTab')).not.toBeInTheDocument()
      expect(screen.queryByTestId('groupAssignmentTab')).not.toBeInTheDocument()
    })

    it('redirects to default tab when subTabValue is empty', () => {
      mockSubTabValue = ''
      render(<RoleDetails roleId="role-1" />)
      expect(mockSetSubTabValueParam).toHaveBeenCalledWith('basicInformation')
    })

    it('renders PermissionsTab when subTabValue is permissions', () => {
      mockSubTabValue = 'permissions'
      render(<RoleDetails roleId="role-1" />)
      expect(screen.getByTestId('permissionsTab')).toBeInTheDocument()
      expect(screen.queryByTestId('nameTextField')).not.toBeInTheDocument()
    })

    it('renders GroupAssignmentTab when subTabValue is groupAssignment', () => {
      mockSubTabValue = 'groupAssignment'
      render(<RoleDetails roleId="role-1" />)
      expect(screen.getByTestId('groupAssignmentTab')).toBeInTheDocument()
      expect(screen.queryByTestId('nameTextField')).not.toBeInTheDocument()
    })

    it('calls setSubTabValueParam when tab is changed', () => {
      render(<RoleDetails roleId="role-1" />)
      const permissionsTab = screen.getByTestId('tab-permissions')
      fireEvent.click(permissionsTab)
      expect(mockSetSubTabValueParam).toHaveBeenCalledWith('permissions')
    })

    it('disables groupAssignment tab for new roles (no roleId)', () => {
      render(<RoleDetails />)
      expect(screen.getByTestId('tab-groupAssignment')).toBeDisabled()
    })
  })

  describe('Edit / read-only toggle', () => {
    it('starts in read-only for updating an existing role (existing roleId)', () => {
      render(<RoleDetails roleId="role-1" />)
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
      expect(screen.queryByTestId('confirmButton')).not.toBeInTheDocument()
    })

    it('starts in edit mode for creating a new role (no roleId)', () => {
      render(<RoleDetails />)
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      expect(screen.getByTestId('confirmButton')).toBeInTheDocument()
      expect(screen.getByTestId('cancelButton')).toBeInTheDocument()
    })

    it('switches to edit mode when Edit button is clicked', () => {
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      expect(screen.getByTestId('confirmButton')).toBeInTheDocument()
      expect(screen.getByTestId('cancelButton')).toBeInTheDocument()
    })

    it('returns to read-only mode when Exit is clicked without changes', () => {
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      fireEvent.click(screen.getByTestId('cancelButton'))
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
      expect(screen.queryByTestId('confirmButton')).not.toBeInTheDocument()
    })

    it('navigates to /roles when cancel is clicked when creating a new role', () => {
      render(<RoleDetails />)
      fireEvent.click(screen.getByTestId('cancelButton'))
      expect(mockPush).toHaveBeenCalledWith('/roles')
    })
  })

  describe('isAnyDirty detection', () => {
    it('Save button is disabled when nothing is dirty', () => {
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      expect(screen.getByTestId('confirmButton')).toBeDisabled()
    })

    it('enables Save when the name field is changed', async () => {
      mockSubTabValue = 'basicInformation'
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Changed Name' } })
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
    })

    it('enables Save when pending permissions change', async () => {
      mockSubTabValue = 'permissions'
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      expect(screen.getByTestId('confirmButton')).toBeDisabled()
      fireEvent.click(screen.getAllByRole('checkbox')[1])
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
    })

    it('enables Save when group assignments change', async () => {
      mockSubTabValue = 'groupAssignment'
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      expect(screen.getByTestId('confirmButton')).toBeDisabled()
      act(() => capturedOnGroupAssignmentUpdate?.(['new-group-id']))
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
    })

    it('Save button becomes disabled again when group assignments are reset to initial', async () => {
      mockSubTabValue = 'groupAssignment'
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))

      act(() => capturedOnGroupAssignmentUpdate?.(['new-group-id']))
      await waitFor(() => expect(screen.getByTestId('confirmButton')).not.toBeDisabled())

      act(() => capturedOnGroupAssignmentUpdate?.([]))
      await waitFor(() => expect(screen.getByTestId('confirmButton')).toBeDisabled())
    })

    it('Save button becomes disabled again when permissions are reset to initial', async () => {
      const roleWithPermissions = {
        ...mockRole,
        permissions: [{ id: 'perm-1', name: 'READ', permissionType: 'SYSTEM' }],
      }
      mockRoleResponse = { data: { data: roleWithPermissions }, isFetching: false, refetch: vi.fn(), error: null }
      mockSubTabValue = 'permissions'
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))

      fireEvent.click(screen.getAllByRole('checkbox')[1])
      await waitFor(() => expect(screen.getByTestId('confirmButton')).not.toBeDisabled())

      fireEvent.click(screen.getAllByRole('checkbox')[1])
      await waitFor(() => expect(screen.getByTestId('confirmButton')).toBeDisabled())
    })
  })

  describe('Save: create mutation', () => {
    it('calls createRole.mutateAsync in create mode', async () => {
      render(<RoleDetails />)
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'New Role' } })
      await waitFor(() => expect(screen.getByTestId('confirmButton')).not.toBeDisabled())
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(mockCreateMutateAsync).toHaveBeenCalledWith(
          expect.objectContaining({ name: 'New Role', roleType: 'SYSTEM' }),
        )
      })
    })

    it('shows success toast and navigates after successful create', async () => {
      mockCreateMutateAsync.mockResolvedValue({ data: { id: 'new-role-id' } })
      render(<RoleDetails />)
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'New Role' } })
      await waitFor(() => expect(screen.getByTestId('confirmButton')).not.toBeDisabled())
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(toast.success).toHaveBeenCalledWith('success.creationSuccess')
        expect(mockPush).toHaveBeenCalledWith(expect.stringContaining('/roles/new-role-id'))
      })
    })

    it('shows error toast on failed create', async () => {
      mockCreateMutateAsync.mockRejectedValue(makeAxiosError(500))
      render(<RoleDetails />)
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'New Role' } })
      await waitFor(() => expect(screen.getByTestId('confirmButton')).not.toBeDisabled())
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(toast.error).toHaveBeenCalledWith('errors.createError')
      })
    })

    it('shows name conflict error toast and form field error on failed create due to existing role name', async () => {
      mockCreateMutateAsync.mockRejectedValue(makeAxiosError(409, 'A role with name New Role already exists'))
      render(<RoleDetails />)
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'New Role' } })
      await waitFor(() => expect(screen.getByTestId('confirmButton')).not.toBeDisabled())
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(toast.error).toHaveBeenCalledWith('errors.nameExistsToast')
        expect(screen.getByTestId('nameFormMessage')).toHaveTextContent('common.errors.nameExists')
      })
    })

    it('shows insufficient permissions toast on failed create due to missing permissions', async () => {
      mockCreateMutateAsync.mockRejectedValue(makeAxiosError(403))
      render(<RoleDetails />)
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'New Role' } })
      await waitFor(() => expect(screen.getByTestId('confirmButton')).not.toBeDisabled())
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(toast.error).toHaveBeenCalledWith('errors.insufficientPermissions')
      })
    })
  })

  describe('Save: update mutation', () => {
    beforeEach(() => {
      mockRoleResponse = { data: { data: mockRole }, isFetching: false, refetch: vi.fn(), error: null }
      mockSubTabValue = 'basicInformation'
    })

    it('calls updateRole.mutateAsync for an existing role', async () => {
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Changed Name' } })
      await waitFor(() => expect(screen.getByTestId('confirmButton')).not.toBeDisabled())
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(mockUpdateMutateAsync).toHaveBeenCalledWith(
          expect.objectContaining({ id: 'role-1', name: 'Changed Name' }),
        )
      })
    })

    it('shows success toast after successful update', async () => {
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Changed Name' } })
      await waitFor(() => expect(screen.getByTestId('confirmButton')).not.toBeDisabled())
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(toast.success).toHaveBeenCalledWith('messages.updateSuccess')
      })
    })

    it('shows error toast on failed update', async () => {
      mockUpdateMutateAsync.mockRejectedValue(makeAxiosError(500, 'error'))
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Changed Name' } })
      await waitFor(() => expect(screen.getByTestId('confirmButton')).not.toBeDisabled())
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(toast.error).toHaveBeenCalledWith('errors.updateError')
      })
    })

    it('shows name conflict error toast and form field error on failed update due to existing role name', async () => {
      mockUpdateMutateAsync.mockRejectedValue(makeAxiosError(409, 'A role with name New Role already exists'))
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'New Role' } })
      await waitFor(() => expect(screen.getByTestId('confirmButton')).not.toBeDisabled())
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(toast.error).toHaveBeenCalledWith('errors.nameExistsToast')
        expect(screen.getByTestId('nameFormMessage')).toHaveTextContent('common.errors.nameExists')
      })
    })

    it('shows insufficient permissions toast on failed update due to missing permissions', async () => {
      mockUpdateMutateAsync.mockRejectedValue(makeAxiosError(403))
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Changed Name' } })
      await waitFor(() => expect(screen.getByTestId('confirmButton')).not.toBeDisabled())
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(toast.error).toHaveBeenCalledWith('errors.insufficientPermissions')
      })
    })
  })

  describe('Delete flow', () => {
    beforeEach(() => {
      mockRoleResponse = { data: { data: mockRole }, isFetching: false, refetch: vi.fn(), error: null }
      mockSubTabValue = 'basicInformation'
    })

    it('shows delete button in edit mode when user has ROLE_DELETE and ROLE_UPDATE', () => {
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      expect(screen.getByRole('button', { name: 'securityArea.deleteButton' })).toBeInTheDocument()
    })

    it('opens delete confirmation modal when delete button is clicked', async () => {
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      fireEvent.click(screen.getByRole('button', { name: 'securityArea.deleteButton' }))
      await waitFor(() => expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument())
    })

    it('cancels delete when Cancel is clicked in the confirmation modal', async () => {
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      fireEvent.click(screen.getByRole('button', { name: 'securityArea.deleteButton' }))
      await waitFor(() => expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument())
      fireEvent.click(screen.getByTestId('discardButton'))
      await waitFor(() => {
        expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
        expect(mockDeleteMutate).not.toHaveBeenCalled()
      })
    })

    it('calls deleteRole.mutate when deletion is confirmed', async () => {
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      fireEvent.click(screen.getByRole('button', { name: 'securityArea.deleteButton' }))
      await waitFor(() => expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument())
      fireEvent.click(screen.getByRole('button', { name: 'deleteConfirmModal.confirm' }))
      await waitFor(() => {
        expect(mockDeleteMutate).toHaveBeenCalledWith(
          'role-1',
          expect.objectContaining({ onSuccess: expect.any(Function) }),
        )
      })
    })

    it('shows success toast and navigates to /roles after successful delete', async () => {
      mockDeleteMutate.mockImplementation((_id: string, { onSuccess }: { onSuccess: () => void }) => onSuccess())
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      fireEvent.click(screen.getByRole('button', { name: 'securityArea.deleteButton' }))
      await waitFor(() => expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument())
      fireEvent.click(screen.getByRole('button', { name: 'deleteConfirmModal.confirm' }))
      await waitFor(() => {
        expect(toast.success).toHaveBeenCalledWith('success.deletionSuccess')
        expect(mockPush).toHaveBeenCalledWith('/roles')
      })
    })

    it('shows insufficient permissions toast when delete fails due to missing permissions', async () => {
      mockDeleteMutate.mockImplementation((_id: string, { onError }: { onError: (e: unknown) => void }) =>
        onError(makeAxiosError(403)),
      )
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      fireEvent.click(screen.getByRole('button', { name: 'securityArea.deleteButton' }))
      await waitFor(() => expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument())
      fireEvent.click(screen.getByRole('button', { name: 'deleteConfirmModal.confirm' }))
      await waitFor(() => {
        expect(toast.error).toHaveBeenCalledWith('errors.insufficientPermissions')
      })
    })

    it('opens delete error dialog when delete fails with a generic error', async () => {
      mockDeleteMutate.mockImplementation((_id: string, { onError }: { onError: (e: unknown) => void }) =>
        onError(makeAxiosError(500)),
      )
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      fireEvent.click(screen.getByRole('button', { name: 'securityArea.deleteButton' }))
      await waitFor(() => expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument())
      fireEvent.click(screen.getByRole('button', { name: 'deleteConfirmModal.confirm' }))
      await waitFor(() => {
        expect(screen.getByText('deleteErrorModal.title')).toBeInTheDocument()
      })
    })
  })

  describe('Default roles', () => {
    const defaultRole: Role = { ...mockRole, readonly: true }

    beforeEach(() => {
      mockRoleResponse = { data: { data: defaultRole }, isFetching: false, refetch: vi.fn(), error: null }
      mockSubTabValue = 'basicInformation'
    })

    it('hides all action buttons on basicInformation tab', () => {
      render(<RoleDetails roleId="role-1" />)
      expect(screen.getByTestId('baseInfoForm')).toBeInTheDocument()
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      expect(screen.queryByTestId('confirmButton')).not.toBeInTheDocument()
    })

    it('hides all action buttons on permissions tab', () => {
      mockSubTabValue = 'permissions'
      render(<RoleDetails roleId="role-1" />)
      expect(screen.getByTestId('permissionsTab')).toBeInTheDocument()
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      expect(screen.queryByTestId('confirmButton')).not.toBeInTheDocument()
    })

    it('shows Edit button on groupAssignment tab (assignment can be changed)', () => {
      mockSubTabValue = 'groupAssignment'
      render(<RoleDetails roleId="role-1" />)
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
    })

    it('allows editing group assignments for a default role', async () => {
      mockSubTabValue = 'groupAssignment'
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      act(() => capturedOnGroupAssignmentUpdate?.(['group-42']))
      await waitFor(() => {
        expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
      })
    })
  })

  describe('ExitWarningModal behavior', () => {
    beforeEach(() => {
      mockRoleResponse = { data: { data: mockRole }, isFetching: false, refetch: vi.fn(), error: null }
    })

    it('opens ExitWarningModal when Exit is clicked with unsaved changes', async () => {
      mockSubTabValue = 'basicInformation'
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Changed Name' } })
      await waitFor(() => expect(screen.getByTestId('confirmButton')).not.toBeDisabled())
      fireEvent.click(screen.getByTestId('cancelButton'))
      await waitFor(() => {
        expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument()
      })
    })

    it('does not open ExitWarningModal when Exit is clicked with no changes', () => {
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      fireEvent.click(screen.getByTestId('cancelButton'))
      expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
    })

    it('discards changes and returns to read-only when Discard is clicked', async () => {
      mockSubTabValue = 'basicInformation'
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Changed Name' } })
      await waitFor(() => expect(screen.getByTestId('confirmButton')).not.toBeDisabled())
      fireEvent.click(screen.getByTestId('cancelButton'))
      await waitFor(() => expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument())
      fireEvent.click(screen.getByTestId('discardButton'))
      await waitFor(() => {
        expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
        expect(screen.getByTestId('editButton')).toBeInTheDocument()
        expect(screen.getByTestId('nameTextField')).toHaveValue(mockRole.name)
      })
    })

    it('triggers save and closes modal when Save is clicked in ExitWarningModal', async () => {
      mockSubTabValue = 'basicInformation'
      render(<RoleDetails roleId="role-1" />)
      fireEvent.click(screen.getByTestId('editButton'))
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Changed Name' } })
      await waitFor(() => expect(screen.getByTestId('confirmButton')).not.toBeDisabled())
      fireEvent.click(screen.getByTestId('cancelButton'))
      await waitFor(() => expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument())
      fireEvent.click(screen.getByTestId('saveButton'))
      await waitFor(() => {
        expect(mockUpdateMutateAsync).toHaveBeenCalled()
        expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
      })
    })
  })

  describe('Permission gating', () => {
    describe('Permissions tab visibility', () => {
      it('shows Permissions tab when user has PERMISSION_READ', () => {
        mockHasPermission([PERMISSION_NAMES.PERMISSION_READ])
        render(<RoleDetails roleId="role-1" />)
        expect(screen.getByText('roles.tabLabels.permissions')).toBeInTheDocument()
      })

      it('hides Permissions tab when user lacks PERMISSION_READ', () => {
        mockHasPermission([PERMISSION_NAMES.ROLE_READ])
        render(<RoleDetails roleId="role-1" />)
        expect(screen.queryByText('roles.tabLabels.permissions')).not.toBeInTheDocument()
      })
    })

    describe('Group Assignment tab visibility', () => {
      it('shows Group Assignment tab when user has GROUP_READ and ASSIGNMENT_READ', () => {
        mockHasPermission([PERMISSION_NAMES.GROUP_READ, PERMISSION_NAMES.ASSIGNMENT_READ])
        render(<RoleDetails roleId="role-1" />)
        expect(screen.getByText('roles.tabLabels.groupAssignment')).toBeInTheDocument()
      })

      it('hides Group Assignment tab when user lacks GROUP_READ', () => {
        mockHasPermission([PERMISSION_NAMES.ROLE_READ])
        render(<RoleDetails roleId="role-1" />)
        expect(screen.queryByText('roles.tabLabels.groupAssignment')).not.toBeInTheDocument()
      })
    })

    describe('Assignments fetch gating', () => {
      it('enables assignments fetch when user has ASSIGNMENT_READ', () => {
        mockHasPermission([PERMISSION_NAMES.ASSIGNMENT_READ])
        render(<RoleDetails roleId="role-1" />)
        const call = vi.mocked(useGetAssignments).mock.calls[0][0]
        expect(call?.isEnabled).toBe(true)
      })

      it('disables assignments fetch when user lacks ASSIGNMENT_READ', () => {
        mockHasPermission([PERMISSION_NAMES.ROLE_READ])
        render(<RoleDetails roleId="role-1" />)
        const call = vi.mocked(useGetAssignments).mock.calls[0][0]
        expect(call?.isEnabled).toBe(false)
      })
    })

    describe('Edit button visibility', () => {
      it('shows Edit button when user has ROLE_UPDATE (existing role, read-only mode)', () => {
        mockHasPermission([PERMISSION_NAMES.ROLE_UPDATE])
        render(<RoleDetails roleId="role-1" />)
        expect(screen.getByTestId('editButton')).toBeInTheDocument()
      })

      it('hides Edit button when user lacks ROLE_UPDATE', () => {
        mockHasPermission([PERMISSION_NAMES.ROLE_READ])
        render(<RoleDetails roleId="role-1" />)
        expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      })
    })

    describe('Delete button visibility', () => {
      it('shows delete button in edit mode when user has ROLE_UPDATE and ROLE_DELETE', () => {
        mockHasPermission([PERMISSION_NAMES.ROLE_UPDATE, PERMISSION_NAMES.ROLE_DELETE])
        render(<RoleDetails roleId="role-1" />)
        fireEvent.click(screen.getByTestId('editButton'))
        expect(screen.getByRole('button', { name: 'securityArea.deleteButton' })).toBeInTheDocument()
      })

      it('does not show delete button in edit mode when user lacks ROLE_DELETE', () => {
        mockHasPermission([PERMISSION_NAMES.ROLE_UPDATE])
        render(<RoleDetails roleId="role-1" />)
        fireEvent.click(screen.getByTestId('editButton'))
        expect(screen.queryByRole('button', { name: 'securityArea.deleteButton' })).not.toBeInTheDocument()
      })

      it('does not show delete button for new role (no roleId)', () => {
        mockHasPermission([PERMISSION_NAMES.ROLE_DELETE])
        render(<RoleDetails />)
        expect(screen.queryByRole('button', { name: 'securityArea.deleteButton' })).not.toBeInTheDocument()
      })
    })
  })
})
