import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import messages from '@/messages/de.json'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { hasAssignmentChanges } from '@/utils/assignments'

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

const { hasAssignmentChanges: realHasAssignmentChanges } =
  await vi.importActual<typeof import('@/utils/assignments')>('@/utils/assignments')

vi.mock('@/utils/assignments', async importOriginal => {
  const actual = await importOriginal<typeof import('@/utils/assignments')>()
  return {
    ...actual,
    hasAssignmentChanges: vi.fn(actual.hasAssignmentChanges),
  }
})

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    refresh: vi.fn(),
  }),
  useSearchParams: () => ({
    get: vi.fn(() => null),
  }),
  usePathname: () => '/datasets/test-id/access-management',
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
    vi.mocked(hasAssignmentChanges).mockImplementation(realHasAssignmentChanges)
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

  describe('uncontrolled mode', () => {
    const defaultProps = {
      entityId: 'test-entity-1',
      initialAssignments: mockAssignments,
      onPatchEntity: vi.fn().mockResolvedValue(undefined),
      title: 'Zugriffsberechtigungen',
      subtitle: 'Hier werden Zuständigkeiten und Zugriffsrechte definiert.',
      hasSecondBox: true,
    }

    it('renders in read-only mode by default', () => {
      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...defaultProps} />
        </NextIntlClientProvider>,
      )

      expect(screen.getByText('Zugriffsberechtigungen')).toBeInTheDocument()
      expect(screen.getByText('Hier werden Zuständigkeiten und Zugriffsrechte definiert.')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /bearbeiten/i })).toBeInTheDocument()
    })

    it('displays assignments table', () => {
      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...defaultProps} />
        </NextIntlClientProvider>,
      )

      expect(screen.getByText('Admin Group')).toBeInTheDocument()
      expect(screen.getByText('Admin')).toBeInTheDocument()
    })

    it('shows no data page when there are no assignments', () => {
      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...defaultProps} initialAssignments={[]} />
        </NextIntlClientProvider>,
      )

      expect(screen.getByText('Keine Gruppen und Rollen vorhanden')).toBeInTheDocument()
      expect(screen.getByText('Keine Gruppe besitzt Berechtigungen.')).toBeInTheDocument()
    })

    it('switches to edit mode when edit button is clicked', async () => {
      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...defaultProps} />
        </NextIntlClientProvider>,
      )

      const editButton = screen.getByRole('button', { name: /bearbeiten/i })
      fireEvent.click(editButton)

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /speichern/i })).toBeInTheDocument()
        expect(screen.getByRole('button', { name: /beenden/i })).toBeInTheDocument()
      })
    })

    it('shows add assignment button in edit mode', async () => {
      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...defaultProps} />
        </NextIntlClientProvider>,
      )

      const editButton = screen.getByRole('button', { name: /bearbeiten/i })
      fireEvent.click(editButton)

      await waitFor(() => {
        expect(screen.getByText(/gruppe hinzufügen/i)).toBeInTheDocument()
      })
    })

    it('disables submit button when no changes have been made', async () => {
      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...defaultProps} />
        </NextIntlClientProvider>,
      )

      const editButton = screen.getByRole('button', { name: /bearbeiten/i })
      fireEvent.click(editButton)

      await waitFor(() => {
        const submitButton = screen.getByRole('button', { name: /speichern/i })
        expect(submitButton).toBeDisabled()
      })
    })

    it('shows first info box in read-only mode', () => {
      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...defaultProps} />
        </NextIntlClientProvider>,
      )

      expect(
        screen.getByText(
          /gruppen, die plattformweite berechtigungen an allen datenbezogenen Elementen besitzen, haben zugriff/i,
        ),
      ).toBeInTheDocument()
      expect(screen.queryByText(/durch das entfernen der eigenen gruppen-rollen-zuordnung/i)).not.toBeInTheDocument()
    })

    it('shows both info boxes in edit mode', async () => {
      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...defaultProps} />
        </NextIntlClientProvider>,
      )

      const editButton = screen.getByRole('button', { name: /bearbeiten/i })
      fireEvent.click(editButton)

      await waitFor(() => {
        expect(
          screen.getByText(
            /gruppen, die plattformweite berechtigungen an allen datenbezogenen Elementen besitzen, haben zugriff/i,
          ),
        ).toBeInTheDocument()
        expect(screen.getByText(/durch das entfernen der eigenen gruppen-rollen-zuordnung/i)).toBeInTheDocument()
      })
    })

    it('disables submit button when groups without roles exist', async () => {
      const assignmentsWithoutRoles: GroupRoleAssignmentTable[] = [
        {
          groupId: '2',
          groupName: 'Empty Group',
          groupDescription: 'No roles',
          assignedRoles: [],
        },
      ]

      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...defaultProps} initialAssignments={assignmentsWithoutRoles} />
        </NextIntlClientProvider>,
      )

      const editButton = screen.getByRole('button', { name: /bearbeiten/i })
      fireEvent.click(editButton)

      await waitFor(() => {
        const submitButton = screen.getByRole('button', { name: /speichern/i })
        expect(submitButton).toBeDisabled()
      })
    })

    it('calls onExit when exit button is clicked with no changes', async () => {
      const onExit = vi.fn()
      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...defaultProps} onExit={onExit} />
        </NextIntlClientProvider>,
      )

      fireEvent.click(screen.getByRole('button', { name: /bearbeiten/i }))
      await waitFor(() => screen.getByRole('button', { name: /beenden/i }))
      fireEvent.click(screen.getByRole('button', { name: /beenden/i }))

      expect(onExit).toHaveBeenCalledOnce()
    })

    it('calls onExit when discarding changes via exit modal', async () => {
      const onExit = vi.fn()
      // Make hasAssignmentChanges return true so the exit modal appears without UI interaction
      vi.mocked(hasAssignmentChanges).mockReturnValue(true)

      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...defaultProps} onExit={onExit} />
        </NextIntlClientProvider>,
      )

      fireEvent.click(screen.getByRole('button', { name: /bearbeiten/i }))
      await waitFor(() => screen.getByRole('button', { name: /beenden/i }))
      fireEvent.click(screen.getByRole('button', { name: /beenden/i }))
      await waitFor(() => screen.getByRole('button', { name: /verwerfen/i }))
      fireEvent.click(screen.getByRole('button', { name: /verwerfen/i }))

      expect(onExit).toHaveBeenCalledOnce()
    })

    it('calls onExit after successfully saving via exit modal', async () => {
      const onExit = vi.fn()
      const onPatchEntity = vi.fn().mockResolvedValue(undefined)
      // Make hasAssignmentChanges return true so the exit modal appears without UI interaction
      vi.mocked(hasAssignmentChanges).mockReturnValue(true)

      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...defaultProps} onPatchEntity={onPatchEntity} onExit={onExit} />
        </NextIntlClientProvider>,
      )

      fireEvent.click(screen.getByRole('button', { name: /bearbeiten/i }))
      await waitFor(() => screen.getByRole('button', { name: /beenden/i }))
      fireEvent.click(screen.getByRole('button', { name: /beenden/i }))
      await waitFor(() => screen.getByRole('button', { name: /speichern/i }))
      fireEvent.click(screen.getByRole('button', { name: /speichern/i }))

      await waitFor(() => expect(onExit).toHaveBeenCalledOnce())
    })

    it('does not call onExit when saving via exit modal fails', async () => {
      const onExit = vi.fn()
      const onPatchEntity = vi.fn().mockRejectedValue(new Error('save failed'))
      // Make hasAssignmentChanges return true so the exit modal appears without UI interaction
      vi.mocked(hasAssignmentChanges).mockReturnValue(true)

      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...defaultProps} onPatchEntity={onPatchEntity} onExit={onExit} />
        </NextIntlClientProvider>,
      )

      fireEvent.click(screen.getByRole('button', { name: /bearbeiten/i }))
      await waitFor(() => screen.getByRole('button', { name: /beenden/i }))
      fireEvent.click(screen.getByRole('button', { name: /beenden/i }))
      await waitFor(() => screen.getByRole('button', { name: /speichern/i }))
      fireEvent.click(screen.getByRole('button', { name: /speichern/i }))

      await waitFor(() => expect(onPatchEntity).toHaveBeenCalled())
      expect(onExit).not.toHaveBeenCalled()
    })

    it('does not call onPatchEntity when submit button is disabled', async () => {
      const onPatchEntity = vi.fn().mockResolvedValue(undefined)
      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <GenericAssignmentsList {...defaultProps} onPatchEntity={onPatchEntity} initialAssignments={[]} />
        </NextIntlClientProvider>,
      )

      const editButton = screen.getByRole('button', { name: /bearbeiten/i })
      fireEvent.click(editButton)

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /speichern/i })).toBeDisabled()
      })
      expect(onPatchEntity).not.toHaveBeenCalled()
    })
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
