import { render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import messages from '@/messages/de.json'
import { PERMISSION_NAMES } from '@/types/currentUser'

import { GroupRoleAssignmentTable } from './AccessManagementTable'
import { GenericAssignmentsList } from './GenericAssignmentsList'

vi.mock('@/app/services/api/groups/clientRequests', () => ({
  useGetGroups: () => ({
    data: { data: [], totalElements: 0 },
    isLoading: false,
    isError: false,
  }),
}))

vi.mock('@/app/services/api/roles/clientRequests', () => ({
  useGetRoles: () => ({
    data: { data: [], totalElements: 0 },
    isLoading: false,
    isError: false,
  }),
}))

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

const mockAssignments: GroupRoleAssignmentTable[] = [
  {
    groupId: '1',
    groupName: 'Admin Group',
    groupDescription: 'Administrator group',
    assignedRoles: [{ roleId: '1', roleName: 'Admin' }],
  },
]

describe('GenericAssignmentsList', () => {
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

  describe('controlled mode', () => {
    const controlledProps = {
      assignedGroups: mockAssignments,
      onAssignedGroupsChange: vi.fn(),
    }

    it('renders without title and subtitle', () => {
      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...controlledProps} />
        </NextIntlClientProvider>,
      )

      expect(screen.queryByText('Zugriffsberechtigungen')).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /bearbeiten/i })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /speichern/i })).not.toBeInTheDocument()
    })

    it('renders assignments from assignedGroups prop', () => {
      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...controlledProps} />
        </NextIntlClientProvider>,
      )

      expect(screen.getByText('Admin Group')).toBeInTheDocument()
      expect(screen.getByText('Admin')).toBeInTheDocument()
    })

    it('shows add-group button when isReadOnly is false', () => {
      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...controlledProps} isReadOnly={false} />
        </NextIntlClientProvider>,
      )

      expect(screen.getByText(/gruppe hinzufügen/i)).toBeInTheDocument()
    })

    it('hides add-group button when isReadOnly is true', () => {
      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...controlledProps} isReadOnly={true} />
        </NextIntlClientProvider>,
      )

      expect(screen.queryByText(/gruppe hinzufügen/i)).not.toBeInTheDocument()
    })

    it('defaults to edit mode (isReadOnly=false) when isReadOnly is not passed', () => {
      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...controlledProps} />
        </NextIntlClientProvider>,
      )

      expect(screen.getByText(/gruppe hinzufügen/i)).toBeInTheDocument()
    })

    it('shows no data page when assignedGroups is empty', () => {
      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...controlledProps} assignedGroups={[]} />
        </NextIntlClientProvider>,
      )

      expect(screen.getByText('Keine Gruppen und Rollen vorhanden')).toBeInTheDocument()
    })

    it('renders tableTitle and tableSubtitle above the table when assignments exist', () => {
      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...controlledProps} tableTitle="Table Title" tableSubtitle="Table Subtitle" />
        </NextIntlClientProvider>,
      )

      expect(screen.getByText('Table Title')).toBeInTheDocument()
      expect(screen.getByText('Table Subtitle')).toBeInTheDocument()
    })

    it('does not render tableTitle and tableSubtitle when no assignments exist', () => {
      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList
            {...controlledProps}
            assignedGroups={[]}
            tableTitle="Table Title"
            tableSubtitle="Table Subtitle"
          />
        </NextIntlClientProvider>,
      )

      expect(screen.queryByText('Table Title')).not.toBeInTheDocument()
      expect(screen.queryByText('Table Subtitle')).not.toBeInTheDocument()
    })

    it('does not render tableTitle and tableSubtitle when not provided', () => {
      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...controlledProps} />
        </NextIntlClientProvider>,
      )

      expect(screen.queryByRole('heading')).not.toBeInTheDocument()
    })
  })

  describe('permission gating', () => {
    const controlledEditProps = {
      assignedGroups: mockAssignments,
      onAssignedGroupsChange: vi.fn(),
      isReadOnly: false,
    }

    it('shows add-group button when user has GROUP_READ and ROLE_READ', () => {
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

      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...controlledEditProps} />
        </NextIntlClientProvider>,
      )

      expect(screen.getByText('Gruppe hinzufügen')).toBeInTheDocument()
    })

    it('hides add-group button when user lacks GROUP_READ', () => {
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
              permissions: [PERMISSION_NAMES.ROLE_READ],
            },
          ],
        },
      } as ReturnType<typeof useGetCurrentUser>)

      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...controlledEditProps} />
        </NextIntlClientProvider>,
      )

      expect(screen.queryByText('Gruppe hinzufügen')).not.toBeInTheDocument()
    })

    it('hides add-group button when user lacks ROLE_READ', () => {
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
              permissions: [PERMISSION_NAMES.GROUP_READ],
            },
          ],
        },
      } as ReturnType<typeof useGetCurrentUser>)

      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...controlledEditProps} />
        </NextIntlClientProvider>,
      )

      expect(screen.queryByText('Gruppe hinzufügen')).not.toBeInTheDocument()
    })
  })
})
