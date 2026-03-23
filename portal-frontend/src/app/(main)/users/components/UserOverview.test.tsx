import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'
import { User } from '@/types/users'

import { UserOverview } from './UserOverview'

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useCreateUser: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useUpdateUser: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useGetCurrentUser: vi.fn(),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: vi.fn(() => '/users/1'),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: () => ({
    setSubTabValueParam: vi.fn(),
    subTabValue: '',
  }),
}))

vi.mock('@tanstack/react-query', async () => {
  const actual = await vi.importActual('@tanstack/react-query')
  return {
    ...actual,
    useQueryClient: () => ({ invalidateQueries: vi.fn() }),
  }
})

vi.mock('./basic-info-tab/UserBasicInfoTab', () => ({
  UserBasicInfoTab: () => <div data-testid="user-basic-info-tab" />,
}))

vi.mock('./groups-tab/GroupsTab', () => ({
  GroupsTab: () => <div data-testid="groups-tab" />,
}))

vi.mock('./roles-tab/RolesTab', () => ({
  RolesTab: () => <div data-testid="roles-tab" />,
}))

const mockUserData: User = {
  id: '1',
  email: 'test@test.com',
  title: 'MR',
  firstName: 'Test',
  lastName: 'User',
  phone: null,
  active: true,
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

const renderComponent = (props = {}) => render(<UserOverview {...defaultProps} {...props} />)

describe('UserOverview permission gating', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Groups tab visibility', () => {
    it('shows Groups tab when user has GROUP_READ permission', () => {
      mockCurrentUser([PERMISSION_NAMES.GROUP_READ])
      renderComponent()
      expect(screen.getByTestId('tab-groups')).toBeInTheDocument()
    })

    it('hides Groups tab when user lacks GROUP_READ permission', () => {
      mockCurrentUser([])
      renderComponent()
      expect(screen.queryByTestId('tab-groups')).not.toBeInTheDocument()
    })
  })

  describe('Roles tab visibility', () => {
    it('shows Roles tab when user has ASSIGNMENT_READ permission', () => {
      mockCurrentUser([PERMISSION_NAMES.ASSIGNMENT_READ])
      renderComponent()
      expect(screen.getByTestId('tab-roles')).toBeInTheDocument()
    })

    it('hides Roles tab when user lacks ASSIGNMENT_READ permission', () => {
      mockCurrentUser([])
      renderComponent()
      expect(screen.queryByTestId('tab-roles')).not.toBeInTheDocument()
    })
  })

  describe('Edit button visibility', () => {
    it('shows Edit button when user has USER_UPDATE permission in read-only mode', () => {
      mockCurrentUser([PERMISSION_NAMES.USER_UPDATE])
      renderComponent()
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
    })

    it('hides Edit button when user lacks USER_UPDATE permission in read-only mode', () => {
      mockCurrentUser([])
      renderComponent()
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
    })
  })
})
