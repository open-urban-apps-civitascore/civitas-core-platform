import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it, vi } from 'vitest'

import messages from '@/messages/de.json'

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
        screen.getByText(/gruppen, die plattformweite berechtigungen an allen datenobjekten besitzen, haben zugriff/i),
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
            /gruppen, die plattformweite berechtigungen an allen datenobjekten besitzen, haben zugriff/i,
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
  })
})
