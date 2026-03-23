import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'
import { Group } from '@/types/groups'

import { GroupOverview } from './GroupOverview'

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

vi.mock('@/app/services/api/groups/clientRequests', () => ({
  useCreateGroup: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useUpdateGroup: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useReplaceGroupAssignments: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: vi.fn(() => '/groups/1'),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: () => ({
    setSubTabValueParam: vi.fn(),
    subTabValue: 'info',
  }),
}))

vi.mock('./base-info-tab/BaseInfoTab', () => ({
  BaseInfoTab: () => <div data-testid="base-info-tab" />,
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
  contactUser: null,
  members: [],
  assignments: [],
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

const renderComponent = (props = {}) => render(<GroupOverview {...defaultProps} {...props} />)

describe('GroupOverview permission gating', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Users tab visibility', () => {
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
})
