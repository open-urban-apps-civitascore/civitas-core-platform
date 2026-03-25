import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

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
  usePermissions: () => ({
    hasPermission: (perm: string) => perm === 'ASSIGNMENT_CREATE',
    hasAnyPermission: () => false,
  }),
}))

const defaultProps = {
  selectedGroupIds: [] as string[],
  onGroupAssignmentUpdate: vi.fn(),
  roleName: 'Test Role',
  isReadOnly: true,
  isSystemRole: false,
  initialAssignments: [],
}

describe('GroupAssignmentTab permission gating', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('hides add-group button in read-only mode even on tenant scope', () => {
    render(<GroupAssignmentTab {...defaultProps} isReadOnly={true} />)
    expect(screen.queryByText('addGroup')).not.toBeInTheDocument()
  })

  it('shows add-group button in edit mode on tenant scope', () => {
    render(<GroupAssignmentTab {...defaultProps} isReadOnly={false} />)
    expect(screen.getByText('addGroup')).toBeInTheDocument()
  })

  it('hides add-group button in edit mode on non-tenant scope', () => {
    render(<GroupAssignmentTab {...defaultProps} isReadOnly={false} />)
    // Switch to dataset scope tab
    const datasetTab = screen.getByText('roles.groupAssignmentTab.scopeTabs.dataset')
    fireEvent.click(datasetTab)
    expect(screen.queryByText('addGroup')).not.toBeInTheDocument()
  })
})
