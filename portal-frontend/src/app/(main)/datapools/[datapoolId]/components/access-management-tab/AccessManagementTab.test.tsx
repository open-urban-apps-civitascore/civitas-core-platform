import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { GroupRoleAssignmentTable } from '@/components/access-management/AccessManagementTable'
import { PERMISSION_NAMES } from '@/types/currentUser'

import { AccessManagementTab } from './AccessManagementTab'

vi.mock('next-intl', () => ({
  useTranslations: (namespace: string) => (key: string) => `${namespace}.${key}`,
}))

vi.mock('@/app/services/api/groups/clientRequests', () => ({
  useGetGroups: () => ({ data: { data: [], totalElements: 0 }, isLoading: false, isError: false }),
}))

vi.mock('@/app/services/api/roles/clientRequests', () => ({
  useGetRoles: () => ({ data: { data: [], totalElements: 0 }, isLoading: false, isError: false }),
}))

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => ({ get: vi.fn(() => null) }),
  usePathname: () => '/datapools/test-id',
}))

const mockAssignedGroups: GroupRoleAssignmentTable[] = [
  {
    groupId: '1',
    groupName: 'Admin Group',
    groupDescription: 'Administrator group',
    assignedRoles: [{ roleId: '1', roleName: 'Admin' }],
  },
]

const defaultProps = {
  assignedGroups: mockAssignedGroups,
  onAssignedGroupsChange: vi.fn(),
}

describe('AccessManagementTab', () => {
  beforeEach(() => {
    vi.mocked(useGetCurrentUser).mockReturnValue({
      data: {
        username: 'test',
        email: 'test@test.com',
        title: 'MR' as const,
        firstName: 'Test',
        lastName: 'User',
        assignments: [
          {
            scopeType: 'TENANT',
            scopeId: null,
            permissions: [PERMISSION_NAMES.GROUP_READ, PERMISSION_NAMES.ROLE_READ],
          },
        ],
      },
    } as ReturnType<typeof useGetCurrentUser>)
  })

  it('renders assigned groups', () => {
    render(<AccessManagementTab {...defaultProps} />)
    expect(screen.getByText('Admin Group')).toBeInTheDocument()
    expect(screen.getByText('Admin')).toBeInTheDocument()
  })

  it('shows no-data state when assignedGroups is empty', () => {
    render(<AccessManagementTab {...defaultProps} assignedGroups={[]} />)
    expect(screen.getByText('accessManagement.noDataPage.title')).toBeInTheDocument()
  })

  it('shows add-group button when isReadOnly is false', () => {
    render(<AccessManagementTab {...defaultProps} isReadOnly={false} />)
    expect(screen.getByText('accessManagement.addAssignment')).toBeInTheDocument()
  })

  it('hides add-group button when isReadOnly is true', () => {
    render(<AccessManagementTab {...defaultProps} isReadOnly={true} />)
    expect(screen.queryByText('accessManagement.addAssignment')).not.toBeInTheDocument()
  })

  it('defaults to edit mode (isReadOnly=false) when prop is not passed', () => {
    render(<AccessManagementTab {...defaultProps} />)
    expect(screen.getByText('accessManagement.addAssignment')).toBeInTheDocument()
  })

  it('passes the datapool-specific firstBoxText to GenericAssignmentsList', () => {
    render(<AccessManagementTab {...defaultProps} />)
    expect(screen.getByText('datapools.accessManagementTab.infoBoxes.firstBox')).toBeInTheDocument()
  })
})
