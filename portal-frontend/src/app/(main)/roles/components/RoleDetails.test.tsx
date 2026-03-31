import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { PERMISSION_NAMES } from '@/types/currentUser'

vi.mock('next-intl', () => ({ useTranslations: () => (k: string) => k }))
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: vi.fn(),
}))
vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: () => ({
    setSubTabValueParam: vi.fn(),
    subTabValue: 'basicInformation',
    tabValue: 'SYSTEM',
    totalPages: 0,
    setTotalPages: vi.fn(),
  }),
}))
vi.mock('@/hooks/use-permissions', () => ({ usePermissions: vi.fn() }))
vi.mock('@/app/services/api/roles/clientRequests', () => ({
  useGetRole: vi.fn(() => ({ data: null, isFetching: false })),
  useCreateRole: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useUpdateRole: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useDeleteRole: vi.fn(() => ({ mutate: vi.fn(), isPending: false, isSuccess: false })),
}))
vi.mock('@/app/services/api/assignments/clientRequests', () => ({
  useGetAssignments: vi.fn(() => ({ data: null, refetch: vi.fn() })),
  useCreateAssignment: vi.fn(() => ({ mutateAsync: vi.fn() })),
  useDeleteAssignment: vi.fn(() => ({ mutateAsync: vi.fn() })),
}))
vi.mock('../page', () => ({ DEFAULT_TAB: 'SYSTEM' }))
vi.mock('./baseinfo-tab/BaseInfoTab', () => ({
  BaseInfoTab: (props: { deleteRole?: unknown }) => (
    <div data-testid="base-info-tab" data-has-delete={!!props.deleteRole} />
  ),
}))
vi.mock('./permissions-tab/PermissionsTab', () => ({ PermissionsTab: () => null }))
vi.mock('./group-assignment-tab/GroupAssignmentTab', () => ({ GroupAssignmentTab: () => null }))

import { usePermissions } from '@/hooks/use-permissions'

import { RoleDetails } from './RoleDetails'

const mockHasPermission = (permissions: string[]) => {
  vi.mocked(usePermissions).mockReturnValue({
    hasPermission: (permission: string) => permissions.includes(permission),
    hasAnyPermission: (...perms: string[]) => perms.some(p => permissions.includes(p)),
    hasScopedPermission: () => false,
  })
}

describe('RoleDetails permission gating', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

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
    it('shows Group Assignment tab when user has GROUP_READ', () => {
      mockHasPermission([PERMISSION_NAMES.GROUP_READ])
      render(<RoleDetails roleId="role-1" />)
      expect(screen.getByText('roles.tabLabels.groupAssignment')).toBeInTheDocument()
    })

    it('hides Group Assignment tab when user lacks GROUP_READ', () => {
      mockHasPermission([PERMISSION_NAMES.ROLE_READ])
      render(<RoleDetails roleId="role-1" />)
      expect(screen.queryByText('roles.tabLabels.groupAssignment')).not.toBeInTheDocument()
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
    it('passes delete handler to BaseInfoTab when user has ROLE_DELETE (existing role)', () => {
      mockHasPermission([PERMISSION_NAMES.ROLE_DELETE])
      render(<RoleDetails roleId="role-1" />)
      expect(screen.getByTestId('base-info-tab')).toHaveAttribute('data-has-delete', 'true')
    })

    it('does not pass delete handler when user lacks ROLE_DELETE', () => {
      mockHasPermission([PERMISSION_NAMES.ROLE_READ])
      render(<RoleDetails roleId="role-1" />)
      expect(screen.getByTestId('base-info-tab')).toHaveAttribute('data-has-delete', 'false')
    })

    it('does not pass delete handler for new role (no roleId)', () => {
      mockHasPermission([PERMISSION_NAMES.ROLE_DELETE])
      render(<RoleDetails />)
      expect(screen.getByTestId('base-info-tab')).toHaveAttribute('data-has-delete', 'false')
    })
  })
})
