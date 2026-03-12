import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it, vi } from 'vitest'

import messages from '@/messages/de.json'
import { Role } from '@/types/roles'

import { AddRoleModal } from './AddRoleModal'

vi.mock('next/navigation', () => ({
  usePathname: () => '/datasets/test-id/access-management',
}))

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
  {
    id: '2',
    name: 'Editor',
    description: 'Editor role',
    roleType: 'DATA',
    permissions: [],
    readonly: false,
    modifiedBy: null,
    modifiedAt: null,
    createdAt: '2024-01-01',
    groupCount: 0,
    userCount: 0,
  },
  {
    id: '3',
    name: 'Viewer',
    description: 'Viewer role',
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

describe('AddRoleModal', () => {
  const defaultProps = {
    open: true,
    onOpenChange: vi.fn(),
    roles: mockRoles,
    assignedRoleIds: ['1'], // Admin role already assigned
    onAddRoles: vi.fn(),
    groupName: 'Test Group',
  }

  it('renders the modal when open', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddRoleModal {...defaultProps} />
      </NextIntlClientProvider>,
    )

    expect(screen.getByText(/rolle hinzufügen/i)).toBeInTheDocument()
  })

  it('displays group name in subtitle', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddRoleModal {...defaultProps} />
      </NextIntlClientProvider>,
    )

    expect(screen.getByText(/test group/i)).toBeInTheDocument()
  })

  it('displays available roles excluding already assigned ones', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddRoleModal {...defaultProps} />
      </NextIntlClientProvider>,
    )

    expect(screen.getByText('Editor')).toBeInTheDocument()
    expect(screen.getByText('Viewer')).toBeInTheDocument()
    // Admin role should not be in the list (already assigned)
    expect(screen.queryByText('Admin')).not.toBeInTheDocument()
  })

  it('shows selection counter when roles are selected', async () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddRoleModal {...defaultProps} />
      </NextIntlClientProvider>,
    )

    const checkboxes = screen.getAllByRole('checkbox')
    fireEvent.click(checkboxes[1]) // Select first available role

    await waitFor(() => {
      expect(screen.getByText(/1.*ausgewählt/i)).toBeInTheDocument()
    })
  })

  it('disables confirm button when no roles are selected', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddRoleModal {...defaultProps} />
      </NextIntlClientProvider>,
    )

    const confirmButton = screen.getByRole('button', { name: /hinzufügen/i })
    expect(confirmButton).toBeDisabled()
  })

  it('enables confirm button when roles are selected', async () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddRoleModal {...defaultProps} />
      </NextIntlClientProvider>,
    )

    const checkboxes = screen.getAllByRole('checkbox')
    fireEvent.click(checkboxes[1])

    await waitFor(() => {
      const confirmButton = screen.getByRole('button', { name: /hinzufügen/i })
      expect(confirmButton).toBeEnabled()
    })
  })

  it('calls onAddRoles with selected role IDs when confirmed', async () => {
    const onAddRoles = vi.fn()

    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddRoleModal {...defaultProps} onAddRoles={onAddRoles} />
      </NextIntlClientProvider>,
    )

    const checkboxes = screen.getAllByRole('checkbox')
    fireEvent.click(checkboxes[1]) // Select Editor role (id: '2')

    const confirmButton = screen.getByRole('button', { name: /hinzufügen/i })
    fireEvent.click(confirmButton)

    await waitFor(() => {
      expect(onAddRoles).toHaveBeenCalledWith(['2'])
    })
  })

  it('shows empty state when all roles are assigned', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddRoleModal {...defaultProps} assignedRoleIds={['1', '2', '3']} />
      </NextIntlClientProvider>,
    )

    expect(screen.getByText(/keine rollen verfügbar, die hinzugefügt werden können/i)).toBeInTheDocument()
  })

  it('allows selecting multiple roles', async () => {
    const onAddRoles = vi.fn()

    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddRoleModal {...defaultProps} onAddRoles={onAddRoles} />
      </NextIntlClientProvider>,
    )

    // Select Editor role
    fireEvent.click(screen.getByLabelText(/rolle auswählen editor/i))

    await waitFor(() => {
      expect(screen.getByText(/1 ausgewählt/i)).toBeInTheDocument()
    })

    // Select Viewer role - re-query after the first selection
    fireEvent.click(screen.getByLabelText(/rolle auswählen viewer/i))

    await waitFor(() => {
      expect(screen.getByText(/2 ausgewählt/i)).toBeInTheDocument()
    })

    const confirmButton = screen.getByRole('button', { name: /hinzufügen/i })
    fireEvent.click(confirmButton)

    await waitFor(() => {
      expect(onAddRoles).toHaveBeenCalledWith(['2', '3'])
    })
  })
})
