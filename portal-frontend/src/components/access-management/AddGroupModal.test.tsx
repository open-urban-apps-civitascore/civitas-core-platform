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

describe('AddGroupModal', () => {
  const defaultProps = {
    open: true,
    onOpenChange: vi.fn(),
    groups: mockGroups,
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

  it('displays available groups excluding already assigned ones', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddGroupModal {...defaultProps} />
      </NextIntlClientProvider>,
    )

    expect(screen.getByText('Editor Group')).toBeInTheDocument()
    expect(screen.getByText('Viewer Group')).toBeInTheDocument()
    // Admin Group should not be in the list (already assigned)
    expect(screen.queryByText('Admin Group')).not.toBeInTheDocument()
  })

  it('shows selection counter when groups are selected', async () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddGroupModal {...defaultProps} />
      </NextIntlClientProvider>,
    )

    const checkboxes = screen.getAllByRole('checkbox')
    fireEvent.click(checkboxes[1]) // Select first available group

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

    const checkboxes = screen.getAllByRole('checkbox')
    fireEvent.click(checkboxes[1])

    await waitFor(() => {
      const confirmButton = screen.getByRole('button', { name: /hinzufügen/i })
      expect(confirmButton).toBeEnabled()
    })
  })

  it('calls onAddGroups with selected group IDs when confirmed', async () => {
    const onAddGroups = vi.fn()

    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddGroupModal {...defaultProps} onAddGroups={onAddGroups} />
      </NextIntlClientProvider>,
    )

    const checkboxes = screen.getAllByRole('checkbox')
    fireEvent.click(checkboxes[1]) // Select Editor Group (id: '2')

    const confirmButton = screen.getByRole('button', { name: /hinzufügen/i })
    fireEvent.click(confirmButton)

    await waitFor(() => {
      expect(onAddGroups).toHaveBeenCalledWith(['2'])
    })
  })

  it('shows empty state when all groups are assigned', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <AddGroupModal {...defaultProps} assignedGroupIds={['1', '2', '3']} />
      </NextIntlClientProvider>,
    )

    expect(screen.getByText(/keine gruppen verfügbar/i)).toBeInTheDocument()
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

    // Select Viewer Group - re-query after the first selection
    fireEvent.click(screen.getByLabelText(/gruppe auswählen viewer group/i))

    await waitFor(() => {
      expect(screen.getByText(/2 ausgewählt/i)).toBeInTheDocument()
    })

    const confirmButton = screen.getByRole('button', { name: /hinzufügen/i })
    fireEvent.click(confirmButton)

    await waitFor(() => {
      expect(onAddGroups).toHaveBeenCalledWith(['2', '3'])
    })
  })
})
