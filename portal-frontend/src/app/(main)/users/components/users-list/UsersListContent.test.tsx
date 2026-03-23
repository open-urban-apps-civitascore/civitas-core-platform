import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PERMISSION_NAMES } from '@/types/currentUser'

import { UsersListContent } from './UsersListContent'

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: vi.fn(() => '/users'),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('./UsersTable', () => ({ default: () => <div data-testid="users-table" /> }))

const defaultProps = {
  users: [],
  totalCount: 0,
  pageIndex: 0,
  pageSize: 10,
  sorting: [],
  totalPages: 0,
  search: '',
  newUserLabel: 'New User',
}

const renderComponent = (props = {}) => render(<UsersListContent {...defaultProps} {...props} />)

describe('UsersListContent permission gating', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows create button when user has USER_CREATE permission', () => {
    vi.mocked(useGetCurrentUser).mockReturnValue({
      data: {
        username: 'test',
        email: 'test@test.com',
        title: 'MR' as const,
        firstName: 'Test',
        lastName: 'User',
        assignments: [{ scopeType: 'TENANT', scopeId: null, permissions: [PERMISSION_NAMES.USER_CREATE] }],
      },
    } as unknown as ReturnType<typeof useGetCurrentUser>)

    renderComponent()

    expect(screen.getByTestId('addUserButton')).toBeInTheDocument()
  })

  it('hides create button when user lacks USER_CREATE permission', () => {
    vi.mocked(useGetCurrentUser).mockReturnValue({
      data: {
        username: 'test',
        email: 'test@test.com',
        title: 'MR' as const,
        firstName: 'Test',
        lastName: 'User',
        assignments: [{ scopeType: 'TENANT', scopeId: null, permissions: [] }],
      },
    } as unknown as ReturnType<typeof useGetCurrentUser>)

    renderComponent()

    expect(screen.queryByTestId('addUserButton')).not.toBeInTheDocument()
  })
})
