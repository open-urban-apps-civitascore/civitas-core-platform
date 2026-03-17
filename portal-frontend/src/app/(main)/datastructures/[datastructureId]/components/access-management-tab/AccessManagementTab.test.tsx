import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { GroupRoleAssignmentTable } from '@/components/access-management/AccessManagementTable'
import { Group } from '@/types/groups'
import { Role } from '@/types/roles'

import { AccessManagementTab } from './AccessManagementTab'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/components/access-management/GenericAssignmentsList', () => ({
  GenericAssignmentsList: ({
    assignedGroups,
    isReadOnly,
    firstBoxText,
  }: {
    assignedGroups: GroupRoleAssignmentTable[]
    isReadOnly: boolean
    firstBoxText: string
  }) => (
    <div
      data-testid="genericAssignmentsList"
      data-readonly={isReadOnly}
      data-groups={assignedGroups.length}
      data-firstboxtext={firstBoxText}
    />
  ),
}))

const mockGroups: Group[] = [
  {
    id: '1',
    name: 'Admin Group',
    description: 'Administrator group',
    roles: null,
    members: null,
    contactUser: null,
    assignments: null,
    createdAt: '2024-01-01',
    modifiedAt: '2024-01-01',
  },
]

const mockRoles: Role[] = [
  {
    id: '1',
    name: 'Admin',
    description: 'Administrator role',
    roleType: 'DATA',
    permissions: [],
    readonly: false,
    modifiedBy: null,
    modifiedAt: null,
    createdAt: '2024-01-01',
    groupCount: 0,
    userCount: 0,
  },
]

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
  groups: mockGroups,
  roles: mockRoles,
}

describe('AccessManagementTab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders GenericAssignmentsList', () => {
    render(<AccessManagementTab {...defaultProps} />)
    expect(screen.getByTestId('genericAssignmentsList')).toBeInTheDocument()
  })

  it('passes isReadOnly=false by default', () => {
    render(<AccessManagementTab {...defaultProps} />)
    expect(screen.getByTestId('genericAssignmentsList')).toHaveAttribute('data-readonly', 'false')
  })

  it('passes isReadOnly=true when prop is set', () => {
    render(<AccessManagementTab {...defaultProps} isReadOnly={true} />)
    expect(screen.getByTestId('genericAssignmentsList')).toHaveAttribute('data-readonly', 'true')
  })

  it('passes assignedGroups to GenericAssignmentsList', () => {
    render(<AccessManagementTab {...defaultProps} />)
    expect(screen.getByTestId('genericAssignmentsList')).toHaveAttribute(
      'data-groups',
      String(mockAssignedGroups.length),
    )
  })

  it('passes firstBoxText translation key to GenericAssignmentsList', () => {
    render(<AccessManagementTab {...defaultProps} />)
    expect(screen.getByTestId('genericAssignmentsList')).toHaveAttribute('data-firstboxtext', 'infoBoxes.firstBox')
  })

  it('renders with empty assignedGroups', () => {
    render(<AccessManagementTab {...defaultProps} assignedGroups={[]} />)
    expect(screen.getByTestId('genericAssignmentsList')).toHaveAttribute('data-groups', '0')
  })
})
