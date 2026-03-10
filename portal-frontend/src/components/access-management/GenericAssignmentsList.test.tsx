import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it, vi } from 'vitest'

import messages from '@/messages/de.json'
import { Group } from '@/types/groups'
import { Role } from '@/types/roles'

import { GroupRoleAssignmentTable } from './AccessManagementTable'
import { GenericAssignmentsList } from './GenericAssignmentsList'

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

const mockGroups: Group[] = [
  {
    id: '1',
    name: 'Admin Group',
    description: 'Administrator group',
    roles: null,
    members: null,
    contactUser: null,
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

const mockAssignments: GroupRoleAssignmentTable[] = [
  {
    groupId: '1',
    groupName: 'Admin Group',
    groupDescription: 'Administrator group',
    assignedRoles: [{ roleId: '1', roleName: 'Admin' }],
  },
]

describe('GenericAssignmentsList', () => {
  const defaultProps = {
    entityId: 'test-entity-1',
    initialAssignments: mockAssignments,
    groups: mockGroups,
    roles: mockRoles,
    onPatchEntity: vi.fn().mockResolvedValue(undefined),
    title: 'Zugriffsberechtigungen',
    subtitle: 'Hier werden Zuständigkeiten und Zugriffsrechte definiert.',
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
        screen.getByText(/gruppen, die plattformweite berechtigungen an allen datenobjekten besitzen, haben zugriff/i),
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
})
