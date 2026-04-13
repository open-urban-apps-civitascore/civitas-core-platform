import { act, fireEvent, render, screen } from '@testing-library/react'

import { useGetPermissions } from '@/app/services/api/permissions/clientRequests'
import { useGetRoles } from '@/app/services/api/roles/clientRequests'
import { Permission } from '@/types/permissions'
import { Role, ROLE_TYPES, RoleType } from '@/types/roles'

import { PermissionsTab } from './PermissionsTab'

vi.mock('next-intl', () => ({ useTranslations: () => (k: string) => k }))
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: vi.fn(),
}))

vi.mock('@/app/services/api/permissions/clientRequests', () => ({
  useGetPermissions: vi.fn(),
}))
vi.mock('@/app/services/api/roles/clientRequests', () => ({
  useGetRoles: vi.fn(),
}))
vi.mock('./RoleTemplateSelect', () => ({
  RoleTemplateSelect: ({ setRoleTemplate }: { setRoleTemplate: (id: string) => void }) => (
    <button data-testid="role-template-select" onClick={() => setRoleTemplate('template-1')}>
      Select Template
    </button>
  ),
}))

const mockPermissions = (permissions: Permission[]) => {
  vi.mocked(useGetPermissions).mockReturnValue({
    data: { data: permissions, totalElements: permissions.length },
    isFetching: false,
    error: null,
  } as unknown as ReturnType<typeof useGetPermissions>)
}

const mockRoles = (roles: Partial<Role>[]) => {
  vi.mocked(useGetRoles).mockReturnValue({
    data: { data: roles },
    error: null,
  } as unknown as ReturnType<typeof useGetRoles>)
}

const defaultProps = {
  pendingPermissionIds: [],
  onPendingPermissionIdsChange: vi.fn(),
  isReadOnly: false,
  currentRoleId: 'role-1',
}

const renderTab = (roleType: RoleType) => render(<PermissionsTab {...defaultProps} roleType={roleType} />)

describe('PermissionsTab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRoles([])
  })

  describe('permissionType filtering', () => {
    it('passes permissionType=SYSTEM for SYSTEM roles', () => {
      mockPermissions([])
      renderTab(ROLE_TYPES.SYSTEM)

      const call = vi.mocked(useGetPermissions).mock.calls[0][0]
      expect(call?.params?.get('permissionType')).toBe('SYSTEM')
    })

    it('passes permissionType=DATA for DATA roles', () => {
      mockPermissions([])
      renderTab(ROLE_TYPES.DATA)

      const call = vi.mocked(useGetPermissions).mock.calls[0][0]
      expect(call?.params?.get('permissionType')).toBe('DATA')
    })

    it('does not pass page/size params', () => {
      mockPermissions([])
      renderTab(ROLE_TYPES.SYSTEM)

      const call = vi.mocked(useGetPermissions).mock.calls[0][0]
      expect(call?.params?.has('page')).toBe(false)
      expect(call?.params?.has('size')).toBe(false)
    })
  })

  describe('search visibility', () => {
    it('shows search area for SYSTEM roles', () => {
      mockPermissions([{ id: '1', name: 'USER_READ', category: 'TENANT_ADMINISTRATION', permissionType: 'SYSTEM' }])
      renderTab(ROLE_TYPES.SYSTEM)

      expect(screen.getByTestId('searchArea')).toBeInTheDocument()
    })

    it('hides search area for DATA roles', () => {
      mockPermissions([{ id: '1', name: 'DATASET_READ', category: 'DATA_MANAGEMENT', permissionType: 'DATA' }])
      renderTab(ROLE_TYPES.DATA)

      expect(screen.queryByTestId('searchArea')).not.toBeInTheDocument()
    })
  })

  describe('role template visibility', () => {
    it('shows role template select for SYSTEM roles when not read-only', () => {
      mockPermissions([{ id: '1', name: 'USER_READ', category: 'TENANT_ADMINISTRATION', permissionType: 'SYSTEM' }])
      render(<PermissionsTab {...defaultProps} roleType={ROLE_TYPES.SYSTEM} isReadOnly={false} />)

      expect(screen.getByTestId('role-template-select')).toBeInTheDocument()
    })

    it('hides role template select for SYSTEM roles when read-only', () => {
      mockPermissions([{ id: '1', name: 'USER_READ', category: 'TENANT_ADMINISTRATION', permissionType: 'SYSTEM' }])
      render(<PermissionsTab {...defaultProps} roleType={ROLE_TYPES.SYSTEM} isReadOnly={true} />)

      expect(screen.queryByTestId('role-template-select')).not.toBeInTheDocument()
    })

    it('shows role template select for DATA roles when not read-only', () => {
      mockPermissions([{ id: '1', name: 'DATASET_READ', category: 'DATA_MANAGEMENT', permissionType: 'DATA' }])
      render(<PermissionsTab {...defaultProps} roleType={ROLE_TYPES.DATA} isReadOnly={false} />)

      expect(screen.getByTestId('role-template-select')).toBeInTheDocument()
    })

    it('hides role template select for DATA roles when read-only', () => {
      mockPermissions([{ id: '1', name: 'DATASET_READ', category: 'DATA_MANAGEMENT', permissionType: 'DATA' }])
      render(<PermissionsTab {...defaultProps} roleType={ROLE_TYPES.DATA} isReadOnly={true} />)

      expect(screen.queryByTestId('role-template-select')).not.toBeInTheDocument()
    })
  })

  describe('grid vs category list rendering', () => {
    it('renders DataPermissionsGrid for DATA roles', () => {
      mockPermissions([{ id: '1', name: 'DATASET_READ', category: 'DATA_MANAGEMENT', permissionType: 'DATA' }])
      renderTab(ROLE_TYPES.DATA)

      expect(screen.getByText('dataPermissions.dataset')).toBeInTheDocument()
      expect(screen.getAllByRole('checkbox').length).toBeGreaterThan(0)
    })

    it('renders CategoryList for SYSTEM roles', () => {
      mockPermissions([{ id: '1', name: 'USER_READ', category: 'TENANT_ADMINISTRATION', permissionType: 'SYSTEM' }])
      renderTab(ROLE_TYPES.SYSTEM)

      expect(screen.getByText('permissionsTab.categories.TENANT_ADMINISTRATION')).toBeInTheDocument()
    })
  })

  describe('permissions mapping', () => {
    it('formats SNAKE_CASE permission names to Title Case', () => {
      mockPermissions([
        { id: '1', name: 'USER_READ_ACCESS', category: 'TENANT_ADMINISTRATION', permissionType: 'SYSTEM' },
      ])
      renderTab(ROLE_TYPES.SYSTEM)

      expect(screen.getByText('User Read Access')).toBeInTheDocument()
    })

    it('maps permission category correctly', () => {
      mockPermissions([
        { id: '1', name: 'USER_READ', category: 'CATEGORY_A', permissionType: 'SYSTEM' },
        { id: '2', name: 'USER_CREATE', category: 'CATEGORY_B', permissionType: 'SYSTEM' },
      ])
      renderTab(ROLE_TYPES.SYSTEM)

      expect(screen.getByText('permissionsTab.categories.CATEGORY_A')).toBeInTheDocument()
      expect(screen.getByText('permissionsTab.categories.CATEGORY_B')).toBeInTheDocument()
    })
  })

  describe('role template selection', () => {
    it('applies permissions from the selected role template', () => {
      const onPendingPermissionIdsChange = vi.fn()
      mockPermissions([
        { id: 'perm-1', name: 'USER_READ', category: 'TENANT', permissionType: 'SYSTEM' },
        { id: 'perm-2', name: 'USER_CREATE', category: 'TENANT', permissionType: 'SYSTEM' },
      ])
      mockRoles([
        {
          id: 'template-1',
          name: 'Admin',
          permissions: [{ id: 'perm-1', name: 'USER_READ', permissionType: 'SYSTEM' }],
        },
      ])
      render(
        <PermissionsTab
          {...defaultProps}
          onPendingPermissionIdsChange={onPendingPermissionIdsChange}
          roleType={ROLE_TYPES.SYSTEM}
        />,
      )

      fireEvent.click(screen.getByTestId('role-template-select'))

      expect(onPendingPermissionIdsChange).toHaveBeenCalledWith(['perm-1'])
    })

    it('applies all matching permissions when the template has multiple', () => {
      const onPendingPermissionIdsChange = vi.fn()
      mockPermissions([
        { id: 'perm-1', name: 'USER_READ', category: 'TENANT', permissionType: 'SYSTEM' },
        { id: 'perm-2', name: 'USER_CREATE', category: 'TENANT', permissionType: 'SYSTEM' },
        { id: 'perm-3', name: 'USER_DELETE', category: 'TENANT', permissionType: 'SYSTEM' },
      ])
      mockRoles([
        {
          id: 'template-1',
          name: 'Admin',
          permissions: [
            { id: 'perm-1', name: 'USER_READ', permissionType: 'SYSTEM' },
            { id: 'perm-3', name: 'USER_DELETE', permissionType: 'SYSTEM' },
          ],
        },
      ])
      render(
        <PermissionsTab
          {...defaultProps}
          onPendingPermissionIdsChange={onPendingPermissionIdsChange}
          roleType={ROLE_TYPES.SYSTEM}
        />,
      )

      fireEvent.click(screen.getByTestId('role-template-select'))

      expect(onPendingPermissionIdsChange).toHaveBeenCalledWith(['perm-1', 'perm-3'])
    })
  })

  describe('search', () => {
    beforeEach(() => vi.useFakeTimers())
    afterEach(() => vi.useRealTimers())

    it('passes the search query as q param to the permissions API after debounce', () => {
      mockPermissions([])
      renderTab(ROLE_TYPES.SYSTEM)

      fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'user' } })
      act(() => vi.advanceTimersByTime(300))

      const lastCall = vi.mocked(useGetPermissions).mock.calls.at(-1)![0]
      expect(lastCall?.params?.get('q')).toBe('user')
    })

    it('does not pass q param when search input is empty', () => {
      mockPermissions([])
      renderTab(ROLE_TYPES.SYSTEM)

      const call = vi.mocked(useGetPermissions).mock.calls[0][0]
      expect(call?.params?.has('q')).toBe(false)
    })

    it('trims whitespace before passing q param', () => {
      mockPermissions([])
      renderTab(ROLE_TYPES.SYSTEM)

      fireEvent.change(screen.getByRole('searchbox'), { target: { value: '  read  ' } })
      act(() => vi.advanceTimersByTime(300))

      const lastCall = vi.mocked(useGetPermissions).mock.calls.at(-1)![0]
      expect(lastCall?.params?.get('q')).toBe('read')
    })

    it('does not pass q param when search contains only whitespace', () => {
      mockPermissions([])
      renderTab(ROLE_TYPES.SYSTEM)

      fireEvent.change(screen.getByRole('searchbox'), { target: { value: '   ' } })
      act(() => vi.advanceTimersByTime(300))

      const lastCall = vi.mocked(useGetPermissions).mock.calls.at(-1)![0]
      expect(lastCall?.params?.has('q')).toBe(false)
    })
  })

  describe('handleCheckedItemsChange', () => {
    it('calls onPendingPermissionIdsChange with the checked item IDs', () => {
      const onPendingPermissionIdsChange = vi.fn()
      mockPermissions([{ id: 'perm-1', name: 'USER_READ', category: 'TENANT', permissionType: 'SYSTEM' }])
      render(
        <PermissionsTab
          {...defaultProps}
          onPendingPermissionIdsChange={onPendingPermissionIdsChange}
          roleType={ROLE_TYPES.SYSTEM}
        />,
      )

      fireEvent.click(screen.getAllByRole('checkbox')[1])

      expect(onPendingPermissionIdsChange).toHaveBeenCalledWith(['perm-1'])
    })

    it('removes a previously selected visible SYSTEM permission when it is unchecked', () => {
      const onPendingPermissionIdsChange = vi.fn()
      mockPermissions([{ id: 'perm-1', name: 'USER_READ', category: 'TENANT', permissionType: 'SYSTEM' }])
      render(
        <PermissionsTab
          {...defaultProps}
          pendingPermissionIds={['perm-1']}
          onPendingPermissionIdsChange={onPendingPermissionIdsChange}
          roleType={ROLE_TYPES.SYSTEM}
        />,
      )

      fireEvent.click(screen.getAllByRole('checkbox')[1])

      expect(onPendingPermissionIdsChange).toHaveBeenCalledWith([])
    })

    it('preserves hidden (filtered-out) selected IDs when updating visible selections', () => {
      const onPendingPermissionIdsChange = vi.fn()
      // 'perm-hidden' is selected but not in the currently visible permission list
      mockPermissions([{ id: 'perm-visible', name: 'USER_READ', category: 'TENANT', permissionType: 'SYSTEM' }])
      render(
        <PermissionsTab
          {...defaultProps}
          pendingPermissionIds={['perm-hidden']}
          onPendingPermissionIdsChange={onPendingPermissionIdsChange}
          roleType={ROLE_TYPES.SYSTEM}
        />,
      )

      fireEvent.click(screen.getAllByRole('checkbox')[1])

      expect(onPendingPermissionIdsChange).toHaveBeenCalledWith(['perm-visible', 'perm-hidden'])
    })

    it('keeps already-visible selected IDs without duplicating them', () => {
      const onPendingPermissionIdsChange = vi.fn()
      mockPermissions([
        { id: 'perm-1', name: 'USER_READ', category: 'TENANT', permissionType: 'SYSTEM' },
        { id: 'perm-2', name: 'USER_CREATE', category: 'TENANT', permissionType: 'SYSTEM' },
      ])
      render(
        <PermissionsTab
          {...defaultProps}
          pendingPermissionIds={['perm-1']}
          onPendingPermissionIdsChange={onPendingPermissionIdsChange}
          roleType={ROLE_TYPES.SYSTEM}
        />,
      )

      fireEvent.click(screen.getAllByRole('checkbox')[2])

      expect(onPendingPermissionIdsChange).toHaveBeenCalledWith(['perm-1', 'perm-2'])
    })
  })

  describe('data permissions grid selection', () => {
    it('selects a DATA permission and reports its id', () => {
      const onPendingPermissionIdsChange = vi.fn()
      mockPermissions([
        { id: 'perm-data-1', name: 'DATASET_READ', category: 'DATA_MANAGEMENT', permissionType: 'DATA' },
      ])
      render(
        <PermissionsTab
          {...defaultProps}
          onPendingPermissionIdsChange={onPendingPermissionIdsChange}
          roleType={ROLE_TYPES.DATA}
        />,
      )

      fireEvent.click(screen.getAllByRole('checkbox')[0])

      expect(onPendingPermissionIdsChange).toHaveBeenCalledWith(['perm-data-1'])
    })

    it('removes a previously selected visible DATA permission when it is unchecked', () => {
      const onPendingPermissionIdsChange = vi.fn()
      mockPermissions([
        { id: 'perm-data-1', name: 'DATASET_READ', category: 'DATA_MANAGEMENT', permissionType: 'DATA' },
      ])
      render(
        <PermissionsTab
          {...defaultProps}
          pendingPermissionIds={['perm-data-1']}
          onPendingPermissionIdsChange={onPendingPermissionIdsChange}
          roleType={ROLE_TYPES.DATA}
        />,
      )

      fireEvent.click(screen.getAllByRole('checkbox')[0])

      expect(onPendingPermissionIdsChange).toHaveBeenCalledWith([])
    })
  })
})
