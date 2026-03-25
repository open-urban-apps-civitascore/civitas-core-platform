import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetPermissions } from '@/app/services/api/permissions/clientRequests'
import { Permission } from '@/types/permissions'
import { ROLE_TYPES, RoleType } from '@/types/roles'

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
  useGetRoles: () => ({ data: { data: [] }, error: null }),
}))
vi.mock('./CategoryList', () => ({
  CategoryList: ({ permissionList }: { permissionList: { category: { id: string } }[] }) => (
    <div data-testid={`category-${permissionList[0]?.category.id}`} />
  ),
}))
vi.mock('./DataPermissionsGrid', () => ({
  DataPermissionsGrid: () => <div data-testid="data-permissions-grid" />,
}))
vi.mock('./RoleTemplateSelect', () => ({
  RoleTemplateSelect: () => <div data-testid="role-template-select" />,
}))

const mockPermissions = (permissions: Permission[]) => {
  vi.mocked(useGetPermissions).mockReturnValue({
    data: { data: permissions, totalElements: permissions.length },
    isFetching: false,
    error: null,
  } as unknown as ReturnType<typeof useGetPermissions>)
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

  describe('search bar visibility', () => {
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

      expect(screen.getByTestId('data-permissions-grid')).toBeInTheDocument()
    })

    it('renders CategoryList for SYSTEM roles', () => {
      mockPermissions([{ id: '1', name: 'USER_READ', category: 'TENANT_ADMINISTRATION', permissionType: 'SYSTEM' }])
      renderTab(ROLE_TYPES.SYSTEM)

      expect(screen.getByTestId('category-TENANT_ADMINISTRATION')).toBeInTheDocument()
    })
  })
})
