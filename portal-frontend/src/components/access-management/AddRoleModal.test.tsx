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

const mockUseGetRoles = vi.fn().mockReturnValue({
  data: { data: mockRoles, totalElements: mockRoles.length, totalPages: 1 },
  isLoading: false,
  isError: false,
})

vi.mock('@/app/services/api/roles/clientRequests', () => ({
  useGetRoles: (...args: unknown[]) => mockUseGetRoles(...args),
}))

describe('AddRoleModal', () => {
  const defaultProps = {
    open: true,
    onOpenChange: vi.fn(),
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

  it('displays all roles with assigned ones shown as disabled checkboxes', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddRoleModal {...defaultProps} />
      </NextIntlClientProvider>,
    )

    expect(screen.getByText('Admin')).toBeInTheDocument()
    expect(screen.getByText('Editor')).toBeInTheDocument()
    expect(screen.getByText('Viewer')).toBeInTheDocument()

    // Admin role checkbox should be checked and disabled
    const adminCheckbox = screen.getByLabelText(/rolle auswählen admin/i)
    expect(adminCheckbox).toBeChecked()
    expect(adminCheckbox).toBeDisabled()
  })

  it('shows selection counter when roles are selected', async () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddRoleModal {...defaultProps} />
      </NextIntlClientProvider>,
    )

    const editorCheckbox = screen.getByLabelText(/rolle auswählen editor/i)
    fireEvent.click(editorCheckbox)

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

    const editorCheckbox = screen.getByLabelText(/rolle auswählen editor/i)
    fireEvent.click(editorCheckbox)

    await waitFor(() => {
      const confirmButton = screen.getByRole('button', { name: /hinzufügen/i })
      expect(confirmButton).toBeEnabled()
    })
  })

  it('calls onAddRoles with selected roles when confirmed', async () => {
    const onAddRoles = vi.fn()

    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddRoleModal {...defaultProps} onAddRoles={onAddRoles} />
      </NextIntlClientProvider>,
    )

    const editorCheckbox = screen.getByLabelText(/rolle auswählen editor/i)
    fireEvent.click(editorCheckbox)

    const confirmButton = screen.getByRole('button', { name: /hinzufügen/i })
    fireEvent.click(confirmButton)

    await waitFor(() => {
      expect(onAddRoles).toHaveBeenCalledWith([mockRoles[1]])
    })
  })

  it('shows empty state when no roles exist', () => {
    mockUseGetRoles.mockReturnValue({
      data: { data: [], totalElements: 0, totalPages: 0 },
      isLoading: false,
      isError: false,
    })

    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddRoleModal {...defaultProps} assignedRoleIds={[]} />
      </NextIntlClientProvider>,
    )

    expect(screen.getByText(/keine rollen verfügbar, die hinzugefügt werden können/i)).toBeInTheDocument()

    // Restore default mock
    mockUseGetRoles.mockReturnValue({
      data: { data: mockRoles, totalElements: mockRoles.length, totalPages: 1 },
      isLoading: false,
      isError: false,
    })
  })

  it('shows error message when roles fetch fails', () => {
    mockUseGetRoles.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
    })

    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddRoleModal {...defaultProps} assignedRoleIds={[]} />
      </NextIntlClientProvider>,
    )

    expect(screen.getByText(/fehler beim laden der daten/i)).toBeInTheDocument()

    // Restore default mock
    mockUseGetRoles.mockReturnValue({
      data: { data: mockRoles, totalElements: mockRoles.length, totalPages: 1 },
      isLoading: false,
      isError: false,
    })
  })

  it('does not include assigned roles in onAddRoles callback', async () => {
    const onAddRoles = vi.fn()

    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddRoleModal {...defaultProps} onAddRoles={onAddRoles} />
      </NextIntlClientProvider>,
    )

    // Select an unassigned role
    fireEvent.click(screen.getByLabelText(/rolle auswählen editor/i))

    const confirmButton = screen.getByRole('button', { name: /hinzufügen/i })
    fireEvent.click(confirmButton)

    await waitFor(() => {
      // Should only contain Editor, not Admin (which is assigned)
      expect(onAddRoles).toHaveBeenCalledWith([mockRoles[1]])
      expect(onAddRoles).not.toHaveBeenCalledWith(expect.arrayContaining([mockRoles[0]]))
    })
  })

  it('does not count assigned roles in selection counter', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddRoleModal {...defaultProps} />
      </NextIntlClientProvider>,
    )

    // Admin role is assigned (checked+disabled) but counter should be 0
    expect(screen.getByText(/0 ausgewählt/i)).toBeInTheDocument()
  })

  it('does not call onAddRoles when cancelled', async () => {
    const onAddRoles = vi.fn()

    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddRoleModal {...defaultProps} onAddRoles={onAddRoles} />
      </NextIntlClientProvider>,
    )

    fireEvent.click(screen.getByLabelText(/rolle auswählen editor/i))

    const cancelButton = screen.getByRole('button', { name: /abbrechen/i })
    fireEvent.click(cancelButton)

    expect(onAddRoles).not.toHaveBeenCalled()
  })

  it('passes pagination and roleType params to useGetRoles', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddRoleModal {...defaultProps} />
      </NextIntlClientProvider>,
    )

    expect(mockUseGetRoles).toHaveBeenCalledWith(
      expect.objectContaining({
        params: expect.any(URLSearchParams),
      }),
    )
    const params = mockUseGetRoles.mock.calls[0][0].params as URLSearchParams
    expect(params.get('page')).toBe('0')
    expect(params.get('size')).toBe('10')
    expect(params.get('roleType')).toBe('DATA,GOVERNANCE')
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

    // Select Viewer role
    fireEvent.click(screen.getByLabelText(/rolle auswählen viewer/i))

    await waitFor(() => {
      expect(screen.getByText(/2 ausgewählt/i)).toBeInTheDocument()
    })

    const confirmButton = screen.getByRole('button', { name: /hinzufügen/i })
    fireEvent.click(confirmButton)

    await waitFor(() => {
      expect(onAddRoles).toHaveBeenCalledWith([mockRoles[1], mockRoles[2]])
    })
  })
})
