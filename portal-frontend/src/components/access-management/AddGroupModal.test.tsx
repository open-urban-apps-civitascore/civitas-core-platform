import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it, vi } from 'vitest'

import messages from '@/messages/de.json'
import { Group } from '@/types/groups'

import { AddGroupModal } from './AddGroupModal'

vi.mock('next/navigation', () => ({
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
  {
    id: '2',
    name: 'Editor Group',
    description: 'Editor group',
    roles: null,
    members: null,
    contactUser: null,
    createdAt: '2024-01-01',
    modifiedAt: '2024-01-01',
  },
  {
    id: '3',
    name: 'Viewer Group',
    description: 'Viewer group',
    roles: null,
    members: null,
    contactUser: null,
    createdAt: '2024-01-01',
    modifiedAt: '2024-01-01',
  },
]

const mockUseGetGroups = vi.fn().mockReturnValue({
  data: { data: mockGroups, totalElements: mockGroups.length, totalPages: 1 },
  isLoading: false,
  isError: false,
})

vi.mock('@/app/services/api/groups/clientRequests', () => ({
  useGetGroups: (...args: unknown[]) => mockUseGetGroups(...args),
}))

describe('AddGroupModal', () => {
  const defaultProps = {
    open: true,
    onOpenChange: vi.fn(),
    assignedGroupIds: ['1'], // Admin Group already assigned
    onAddGroups: vi.fn(),
  }

  it('renders the modal when open', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddGroupModal {...defaultProps} />
      </NextIntlClientProvider>,
    )

    expect(screen.getByText(/gruppe hinzufügen/i)).toBeInTheDocument()
  })

  it('displays all groups with assigned ones shown as disabled checkboxes', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddGroupModal {...defaultProps} />
      </NextIntlClientProvider>,
    )

    expect(screen.getByText('Admin Group')).toBeInTheDocument()
    expect(screen.getByText('Editor Group')).toBeInTheDocument()
    expect(screen.getByText('Viewer Group')).toBeInTheDocument()

    // Admin Group checkbox should be checked and disabled
    const adminCheckbox = screen.getByLabelText(/gruppe auswählen admin group/i)
    expect(adminCheckbox).toBeChecked()
    expect(adminCheckbox).toBeDisabled()
  })

  it('shows selection counter when groups are selected', async () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddGroupModal {...defaultProps} />
      </NextIntlClientProvider>,
    )

    const editorCheckbox = screen.getByLabelText(/gruppe auswählen editor group/i)
    fireEvent.click(editorCheckbox)

    await waitFor(() => {
      expect(screen.getByText(/1.*ausgewählt/i)).toBeInTheDocument()
    })
  })

  it('disables confirm button when no groups are selected', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddGroupModal {...defaultProps} />
      </NextIntlClientProvider>,
    )

    const confirmButton = screen.getByRole('button', { name: /hinzufügen/i })
    expect(confirmButton).toBeDisabled()
  })

  it('enables confirm button when groups are selected', async () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddGroupModal {...defaultProps} />
      </NextIntlClientProvider>,
    )

    const editorCheckbox = screen.getByLabelText(/gruppe auswählen editor group/i)
    fireEvent.click(editorCheckbox)

    await waitFor(() => {
      const confirmButton = screen.getByRole('button', { name: /hinzufügen/i })
      expect(confirmButton).toBeEnabled()
    })
  })

  it('calls onAddGroups with selected groups when confirmed', async () => {
    const onAddGroups = vi.fn()

    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddGroupModal {...defaultProps} onAddGroups={onAddGroups} />
      </NextIntlClientProvider>,
    )

    const editorCheckbox = screen.getByLabelText(/gruppe auswählen editor group/i)
    fireEvent.click(editorCheckbox)

    const confirmButton = screen.getByRole('button', { name: /hinzufügen/i })
    fireEvent.click(confirmButton)

    await waitFor(() => {
      expect(onAddGroups).toHaveBeenCalledWith([mockGroups[1]])
    })
  })

  it('shows empty state when no groups exist', () => {
    mockUseGetGroups.mockReturnValue({
      data: { data: [], totalElements: 0, totalPages: 0 },
      isLoading: false,
      isError: false,
    })

    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddGroupModal {...defaultProps} assignedGroupIds={[]} />
      </NextIntlClientProvider>,
    )

    expect(screen.getByText(/keine gruppen verfügbar/i)).toBeInTheDocument()

    // Restore default mock
    mockUseGetGroups.mockReturnValue({
      data: { data: mockGroups, totalElements: mockGroups.length, totalPages: 1 },
      isLoading: false,
      isError: false,
    })
  })

  it('shows error message when groups fetch fails', () => {
    mockUseGetGroups.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
    })

    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddGroupModal {...defaultProps} assignedGroupIds={[]} />
      </NextIntlClientProvider>,
    )

    expect(screen.getByText(/fehler beim laden der daten/i)).toBeInTheDocument()

    // Restore default mock
    mockUseGetGroups.mockReturnValue({
      data: { data: mockGroups, totalElements: mockGroups.length, totalPages: 1 },
      isLoading: false,
      isError: false,
    })
  })

  it('does not include assigned groups in onAddGroups callback', async () => {
    const onAddGroups = vi.fn()

    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddGroupModal {...defaultProps} onAddGroups={onAddGroups} />
      </NextIntlClientProvider>,
    )

    // Select an unassigned group
    fireEvent.click(screen.getByLabelText(/gruppe auswählen editor group/i))

    const confirmButton = screen.getByRole('button', { name: /hinzufügen/i })
    fireEvent.click(confirmButton)

    await waitFor(() => {
      // Should only contain Editor Group, not Admin Group (which is assigned)
      expect(onAddGroups).toHaveBeenCalledWith([mockGroups[1]])
      expect(onAddGroups).not.toHaveBeenCalledWith(expect.arrayContaining([mockGroups[0]]))
    })
  })

  it('does not count assigned groups in selection counter', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddGroupModal {...defaultProps} />
      </NextIntlClientProvider>,
    )

    // Admin Group is assigned (checked+disabled) but counter should be 0
    expect(screen.getByText(/0 ausgewählt/i)).toBeInTheDocument()
  })

  it('does not call onAddGroups when cancelled', async () => {
    const onAddGroups = vi.fn()

    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddGroupModal {...defaultProps} onAddGroups={onAddGroups} />
      </NextIntlClientProvider>,
    )

    fireEvent.click(screen.getByLabelText(/gruppe auswählen editor group/i))

    const cancelButton = screen.getByRole('button', { name: /abbrechen/i })
    fireEvent.click(cancelButton)

    expect(onAddGroups).not.toHaveBeenCalled()
  })

  it('passes pagination params to useGetGroups', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddGroupModal {...defaultProps} />
      </NextIntlClientProvider>,
    )

    expect(mockUseGetGroups).toHaveBeenCalledWith(
      expect.objectContaining({
        params: expect.any(URLSearchParams),
      }),
    )
    const params = mockUseGetGroups.mock.calls[0][0].params as URLSearchParams
    expect(params.get('page')).toBe('0')
    expect(params.get('size')).toBe('10')
  })

  it('allows selecting multiple groups', async () => {
    const onAddGroups = vi.fn()

    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddGroupModal {...defaultProps} onAddGroups={onAddGroups} />
      </NextIntlClientProvider>,
    )

    // Select Editor Group
    fireEvent.click(screen.getByLabelText(/gruppe auswählen editor group/i))

    await waitFor(() => {
      expect(screen.getByText(/1 ausgewählt/i)).toBeInTheDocument()
    })

    // Select Viewer Group
    fireEvent.click(screen.getByLabelText(/gruppe auswählen viewer group/i))

    await waitFor(() => {
      expect(screen.getByText(/2 ausgewählt/i)).toBeInTheDocument()
    })

    const confirmButton = screen.getByRole('button', { name: /hinzufügen/i })
    fireEvent.click(confirmButton)

    await waitFor(() => {
      expect(onAddGroups).toHaveBeenCalledWith([mockGroups[1], mockGroups[2]])
    })
  })
})
