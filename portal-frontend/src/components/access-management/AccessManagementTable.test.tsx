import { render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it, vi } from 'vitest'

import messages from '@/messages/de.json'

import { AccessManagementTable, GroupRoleAssignmentTable } from './AccessManagementTable'

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    refresh: vi.fn(),
  }),
  useSearchParams: () => ({
    get: vi.fn(),
  }),
  usePathname: () => '/datasets/test-id/access-management',
}))

const mockAssignments: GroupRoleAssignmentTable[] = [
  {
    groupId: '1',
    groupName: 'Admin Group',
    groupDescription: 'Administrator group',
    assignedRoles: [
      { roleId: 'r1', roleName: 'Admin' },
      { roleId: 'r2', roleName: 'Editor' },
    ],
  },
  {
    groupId: '2',
    groupName: 'Viewer Group',
    groupDescription: 'Read-only group',
    assignedRoles: [{ roleId: 'r3', roleName: 'Viewer' }],
  },
]

describe('AccessManagementTable', () => {
  const defaultProps = {
    assignments: mockAssignments,
    rowCount: 2,
    pageIndex: 0,
    pageSize: 10,
    totalPages: 1,
    sorting: [],
    onPaginationChange: vi.fn(),
    onSortingChange: vi.fn(),
    isReadOnly: true,
  }

  it('renders the table with assignments', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AccessManagementTable {...defaultProps} />
      </NextIntlClientProvider>,
    )

    expect(screen.getByText('Admin Group')).toBeInTheDocument()
    expect(screen.getByText('Viewer Group')).toBeInTheDocument()
    expect(screen.getByText('Administrator group')).toBeInTheDocument()
  })

  it('displays assigned roles as badges', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AccessManagementTable {...defaultProps} />
      </NextIntlClientProvider>,
    )

    expect(screen.getByText('Admin')).toBeInTheDocument()
    expect(screen.getByText('Editor')).toBeInTheDocument()
    expect(screen.getByText('Viewer')).toBeInTheDocument()
  })

  it('shows add role button for groups without roles when not read-only', () => {
    const assignmentsWithoutRoles: GroupRoleAssignmentTable[] = [
      {
        groupId: '3',
        groupName: 'Empty Group',
        groupDescription: 'No roles assigned',
        assignedRoles: [],
      },
    ]

    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AccessManagementTable {...defaultProps} assignments={assignmentsWithoutRoles} isReadOnly={false} />
      </NextIntlClientProvider>,
    )

    expect(screen.getByRole('button', { name: /rolle hinzufügen/i })).toBeInTheDocument()
  })

  it('hides actions column when read-only', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AccessManagementTable {...defaultProps} isReadOnly={true} />
      </NextIntlClientProvider>,
    )

    expect(screen.queryByRole('button', { name: /actions/i })).not.toBeInTheDocument()
  })

  it('shows actions column when not read-only', () => {
    const onDeleteClick = vi.fn()
    const onAddRoleClick = vi.fn()

    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AccessManagementTable
          {...defaultProps}
          isReadOnly={false}
          onDeleteClick={onDeleteClick}
          onAddRoleClick={onAddRoleClick}
        />
      </NextIntlClientProvider>,
    )

    // Actions dropdown should be visible
    const dropdownButtons = screen.getAllByRole('button', { name: '' })
    expect(dropdownButtons.length).toBeGreaterThan(0)
  })

  it('displays empty state correctly', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AccessManagementTable {...defaultProps} assignments={[]} isReadOnly={true} />
      </NextIntlClientProvider>,
    )

    expect(screen.getByText(/Es gibt leider keine Ergebnisse./i)).toBeInTheDocument()
  })
})
