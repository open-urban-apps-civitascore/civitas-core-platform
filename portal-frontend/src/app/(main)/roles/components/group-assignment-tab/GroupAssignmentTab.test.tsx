import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetGroups } from '@/app/services/api/groups/clientRequests'
import { usePermissions } from '@/hooks/use-permissions'
import { useQueryParams } from '@/hooks/use-query-params'
import { type Assignment, ASSIGNMENT_SCOPE_TYPES } from '@/types/assignments'
import type { Group } from '@/types/groups'
import { ROLE_TYPES } from '@/types/roles'

import { GroupAssignmentTab } from './GroupAssignmentTab'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: vi.fn(),
}))

vi.mock('@/app/services/api/groups/clientRequests', () => ({
  useGetGroups: vi.fn(() => ({
    data: { data: [], totalElements: 0 },
    isFetching: false,
  })),
}))

vi.mock('@/hooks/use-permissions', () => ({
  usePermissions: vi.fn(),
}))

vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: vi.fn(),
}))

vi.mock('./GroupAssignmentModal', () => ({
  GroupAssignmentModal: () => null,
}))

const onGroupAssignmentUpdate = vi.fn()

const makeDefaultQueryParams = () => ({
  pageIndex: 0,
  pageSize: 10,
  sorting: [],
  search: '',
  totalPages: 0,
  tabValue: '',
  subTabValue: '',
  setTotalPages: vi.fn(),
  setPaginationParams: vi.fn(),
  setSortingParams: vi.fn(),
  setSearchParam: vi.fn(),
  setTabValueParam: vi.fn(),
  setSubTabValueParam: vi.fn(),
  getApiRequestParams: vi.fn().mockImplementation(() => new URLSearchParams()),
  getApiRequestParamsByUrl: vi.fn().mockImplementation(() => new URLSearchParams()),
})

const mockPermissions = {
  hasPermission: (perm: string) => perm === 'ASSIGNMENT_CREATE',
  hasAnyPermission: () => false,
  hasScopedPermission: () => false,
}

const mockGroups: Group[] = [
  {
    id: 'group-1',
    name: 'Group 1',
    description: '',
    members: [],
    contactUser: null,
    roles: null,
    assignments: null,
    createdAt: '2024-01-01',
    modifiedAt: '2024-01-01',
  },
  {
    id: 'group-2',
    name: 'Group 2',
    description: '',
    members: [],
    contactUser: null,
    roles: null,
    assignments: null,
    createdAt: '2024-01-01',
    modifiedAt: '2024-01-01',
  },
  {
    id: 'group-3',
    name: 'Group 3',
    description: '',
    members: [],
    contactUser: null,
    roles: null,
    assignments: null,
    createdAt: '2024-01-01',
    modifiedAt: '2024-01-01',
  },
]

const mockAssignments: Assignment[] = [
  {
    id: 'assignment-1',
    createdAt: '2024-01-01',
    modifiedAt: '2024-01-01',
    group: { id: 'group-1', name: 'Group 1' },
    role: { id: 'role-1', name: 'Test Role', roleType: ROLE_TYPES.SYSTEM, description: '', readonly: false },
    scopeType: ASSIGNMENT_SCOPE_TYPES.TENANT,
    scope: null,
  },
  {
    id: 'assignment-2',
    createdAt: '2024-01-01',
    modifiedAt: '2024-01-01',
    group: { id: 'group-2', name: 'Group 2' },
    role: { id: 'role-2', name: 'Test Role 2', roleType: ROLE_TYPES.DATA, description: '', readonly: false },
    scopeType: ASSIGNMENT_SCOPE_TYPES.TENANT,
    scope: null,
  },
  {
    id: 'assignment-3',
    createdAt: '2024-01-01',
    modifiedAt: '2024-01-01',
    group: { id: 'group-3', name: 'Group 3' },
    role: { id: 'role-3', name: 'Test Role 3', roleType: ROLE_TYPES.DATA, description: '', readonly: false },
    scopeType: ASSIGNMENT_SCOPE_TYPES.DATASET,
    scope: { id: 'dataset-1', name: 'Dataset 1' },
  },
]

const mockDataRoleAssignments = mockAssignments.filter(a => a.role.roleType === ROLE_TYPES.DATA)
const mockSystemRoleAssignments = mockAssignments.filter(a => a.role.roleType === ROLE_TYPES.SYSTEM)

const defaultProps = {
  selectedGroupIds: [] as string[],
  onGroupAssignmentUpdate: vi.fn(),
  roleName: 'Test Role',
  isReadOnly: true,
  isSystemRole: false,
  initialAssignments: [],
}

// canEdit = !isReadOnly && isTenantScope
describe('Add group and Delete group button Visibility', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(usePermissions).mockReturnValue({
      hasPermission: (perm: string) => perm === 'ASSIGNMENT_CREATE',
      hasAnyPermission: () => false,
      hasScopedPermission: () => false,
    })
    vi.mocked(useGetGroups).mockReturnValue({
      data: { data: mockGroups, totalElements: 3 },
      isFetching: false,
    } as unknown as ReturnType<typeof useGetGroups>)
    vi.mocked(useQueryParams).mockReturnValue(makeDefaultQueryParams() as unknown as ReturnType<typeof useQueryParams>)
  })

  it('hides buttons when isReadOnly=true on tenant scope (canEdit=false)', () => {
    render(<GroupAssignmentTab {...defaultProps} />)
    expect(screen.queryByText('addGroup')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Open menu' })).not.toBeInTheDocument()
  })

  it('hides buttons for data role on non-tenant scope when isReadOnly=false (canEdit=false)', () => {
    render(
      <GroupAssignmentTab
        {...defaultProps}
        isReadOnly={false}
        selectedGroupIds={['group-3']}
        initialAssignments={mockDataRoleAssignments}
        onGroupAssignmentUpdate={onGroupAssignmentUpdate}
      />,
    )
    fireEvent.click(screen.getByText('roles.groupAssignmentTab.scopeTabs.dataset'))
    expect(screen.getByText('Group 3')).toBeInTheDocument()
    expect(screen.queryByText('addGroup')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Open menu' })).not.toBeInTheDocument()
  })

  it('shows buttons for system role when isReadOnly=false on tenant scope (canEdit=true)', () => {
    render(
      <GroupAssignmentTab
        {...defaultProps}
        isSystemRole={true}
        isReadOnly={false}
        selectedGroupIds={['group-1']}
        initialAssignments={mockSystemRoleAssignments}
        onGroupAssignmentUpdate={onGroupAssignmentUpdate}
      />,
    )
    expect(screen.getByText('Group 1')).toBeInTheDocument()
    expect(screen.getByText('addGroup')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Open menu' })).toBeInTheDocument()
  })

  it('shows buttons for data role when isReadOnly=false on tenant scope (canEdit=true)', () => {
    render(
      <GroupAssignmentTab
        {...defaultProps}
        isReadOnly={false}
        selectedGroupIds={['group-2']}
        initialAssignments={mockDataRoleAssignments}
        onGroupAssignmentUpdate={onGroupAssignmentUpdate}
      />,
    )
    expect(screen.getByText('Group 2')).toBeInTheDocument()
    expect(screen.getByText('addGroup')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Open menu' })).toBeInTheDocument()
  })
})

describe('GroupAssignmentTab permission gating', () => {
  it('hides add-group button in edit mode when user lacks ASSIGNMENT_CREATE', () => {
    vi.mocked(usePermissions).mockReturnValue({
      hasPermission: () => false,
      hasAnyPermission: () => false,
      hasScopedPermission: () => false,
    })
    render(<GroupAssignmentTab {...defaultProps} isReadOnly={false} />)
    expect(screen.queryByText('addGroup')).not.toBeInTheDocument()
  })

  it('shows add-group button in edit mode when user has ASSIGNMENT_CREATE', () => {
    vi.mocked(usePermissions).mockReturnValue({
      hasPermission: (perm: string) => perm === 'ASSIGNMENT_CREATE',
      hasAnyPermission: () => false,
      hasScopedPermission: () => false,
    })
    render(<GroupAssignmentTab {...defaultProps} isReadOnly={false} />)
    expect(screen.getByText('addGroup')).toBeInTheDocument()
  })
})

describe('GroupAssignmentTab scope filtering', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(usePermissions).mockReturnValue(mockPermissions)
    vi.mocked(useQueryParams).mockReturnValue(makeDefaultQueryParams() as unknown as ReturnType<typeof useQueryParams>)
    vi.mocked(useGetGroups).mockReturnValue({
      data: { data: mockGroups, totalElements: 2 },
      isFetching: false,
    } as unknown as ReturnType<typeof useGetGroups>)
  })
  it('does not show scope tabs for system roles', () => {
    render(
      <GroupAssignmentTab
        {...defaultProps}
        isSystemRole={true}
        selectedGroupIds={['group-2']}
        initialAssignments={mockSystemRoleAssignments}
      />,
    )
    expect(screen.queryByTestId('segmentedControlBar')).not.toBeInTheDocument()
  })
  it('shows scope tabs for data roles', () => {
    render(
      <GroupAssignmentTab
        {...defaultProps}
        selectedGroupIds={['group-2']}
        initialAssignments={mockDataRoleAssignments}
      />,
    )
    expect(screen.queryByTestId('segmentedControlBar')).toBeInTheDocument()
  })
  it('shows only scope-specific groups after switching to a non-TENANT scope tab', () => {
    render(
      <GroupAssignmentTab
        {...defaultProps}
        selectedGroupIds={['group-2', 'group-3']}
        initialAssignments={mockDataRoleAssignments}
      />,
    )
    fireEvent.click(screen.getByText('roles.groupAssignmentTab.scopeTabs.dataset'))
    expect(screen.queryByText('Group 2')).not.toBeInTheDocument()
    expect(screen.getByText('Group 3')).toBeInTheDocument()
  })
})

describe('Remove Group', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(usePermissions).mockReturnValue(mockPermissions)
    vi.mocked(useQueryParams).mockReturnValue(makeDefaultQueryParams() as unknown as ReturnType<typeof useQueryParams>)
    vi.mocked(useGetGroups).mockReturnValue({
      data: { data: mockGroups, totalElements: 2 },
      isFetching: false,
    } as unknown as ReturnType<typeof useGetGroups>)
  })

  it('calls onGroupAssignmentUpdate with remaining group ids after removal confirmation', () => {
    render(
      <GroupAssignmentTab
        {...defaultProps}
        isReadOnly={false}
        selectedGroupIds={['group-1', 'group-2']}
        initialAssignments={mockAssignments}
        onGroupAssignmentUpdate={onGroupAssignmentUpdate}
      />,
    )
    fireEvent.pointerDown(screen.getAllByRole('button', { name: 'Open menu' })[0], { button: 0 })
    fireEvent.click(screen.getByText('removeAssignment'))
    fireEvent.click(screen.getByRole('button', { name: 'removeModal.confirm' }))
    expect(onGroupAssignmentUpdate).toHaveBeenCalledWith(['group-2'])
  })
})

describe('GroupAssignmentTab no-data state', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(usePermissions).mockReturnValue(mockPermissions)
    vi.mocked(useQueryParams).mockReturnValue(makeDefaultQueryParams() as unknown as ReturnType<typeof useQueryParams>)
    vi.mocked(useGetGroups).mockReturnValue({
      data: { data: [], totalElements: 0 },
      isFetching: false,
    } as unknown as ReturnType<typeof useGetGroups>)
  })

  it('shows no-groups message when no groups are assigned', () => {
    render(<GroupAssignmentTab {...defaultProps} />)
    expect(screen.getByText('noGroupsAssigned')).toBeInTheDocument()
  })

  it('shows loading error page when getAssignmentsError is set', () => {
    render(<GroupAssignmentTab {...defaultProps} getAssignmentsError={new Error('fetch failed')} />)
    expect(screen.getByText('errors.loadingError')).toBeInTheDocument()
  })
})

describe('GroupAssignmentTab pagination reset', () => {
  it('resets pageIndex to 0 when scope changes', () => {
    vi.clearAllMocks()
    vi.mocked(usePermissions).mockReturnValue(mockPermissions)
    vi.mocked(useGetGroups).mockReturnValue({
      data: { data: [], totalElements: 0 },
      isFetching: false,
    } as unknown as ReturnType<typeof useGetGroups>)
    const setPaginationParams = vi.fn()
    vi.mocked(useQueryParams).mockReturnValue({
      ...makeDefaultQueryParams(),
      pageIndex: 2,
      setPaginationParams,
    } as unknown as ReturnType<typeof useQueryParams>)

    render(<GroupAssignmentTab {...defaultProps} />)
    setPaginationParams.mockClear()

    fireEvent.click(screen.getByText('roles.groupAssignmentTab.scopeTabs.dataset'))

    expect(setPaginationParams).toHaveBeenCalledWith({ pageIndex: 0, pageSize: 10 })
  })
})
