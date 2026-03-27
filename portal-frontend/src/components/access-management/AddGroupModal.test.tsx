import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it, vi } from 'vitest'

import messages from '@/messages/de.json'
import { Group } from '@/types/groups'

import { AddGroupModal } from './AddGroupModal'

vi.mock('next/navigation', () => ({
  usePathname: () => '/datasets/test-id/access-management',
}))

const makeGroup = (id: string, name: string, description: string): Group => ({
  id,
  name,
  description,
  roles: null,
  members: null,
  contactUser: null,
  createdAt: '2024-01-01',
  modifiedAt: '2024-01-01',
})

const mockGroups: Group[] = [
  makeGroup('1', 'Admin Group', 'Administrator group'),
  makeGroup('2', 'Editor Group', 'Editor group'),
  makeGroup('3', 'Viewer Group', 'Viewer group'),
]

// 15 groups for pagination tests (pageSize=10 → 2 pages)
const allGroups: Group[] = Array.from({ length: 15 }, (_, i) =>
  makeGroup(String(i + 1), `Group ${i + 1}`, `Description for group ${i + 1}`),
)
const page0Groups = allGroups.slice(0, 10)
const page1Groups = allGroups.slice(10, 15)

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

  describe('cross-page selection', () => {
    const paginatedMock = () =>
      mockUseGetGroups.mockImplementation(({ params }: { params: URLSearchParams }) => {
        const page = Number(params.get('page') ?? '0')
        const data = page === 0 ? page0Groups : page1Groups
        return {
          data: { data, totalElements: allGroups.length, totalPages: 2 },
          isLoading: false,
          isError: false,
        }
      })

    const paginatedProps = {
      open: true,
      onOpenChange: vi.fn(),
      assignedGroupIds: ['1'],
      onAddGroups: vi.fn(),
    }

    afterEach(() => {
      // Restore default mock
      mockUseGetGroups.mockReturnValue({
        data: { data: mockGroups, totalElements: mockGroups.length, totalPages: 1 },
        isLoading: false,
        isError: false,
      })
    })

    it('preserves selections when navigating between pages', async () => {
      paginatedMock()

      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <AddGroupModal {...paginatedProps} />
        </NextIntlClientProvider>,
      )

      // Page 0: select Group 3 (unassigned)
      await waitFor(() => {
        expect(screen.getByText('Group 3')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByLabelText(/gruppe auswählen group 3/i))

      await waitFor(() => {
        expect(screen.getByText(/1.*ausgewählt/i)).toBeInTheDocument()
      })

      // Navigate to page 2
      const nextPageButton = screen.getByRole('button', { name: /nächste/i })
      fireEvent.click(nextPageButton)

      // Page 1 groups should appear
      await waitFor(() => {
        expect(screen.getByText('Group 11')).toBeInTheDocument()
      })

      // Selection count should still be 1 (from page 0)
      expect(screen.getByText(/1.*ausgewählt/i)).toBeInTheDocument()

      // Select another group on page 2
      fireEvent.click(screen.getByLabelText(/gruppe auswählen group 12/i))

      await waitFor(() => {
        expect(screen.getByText(/2.*ausgewählt/i)).toBeInTheDocument()
      })

      // Navigate back to page 1
      const prevPageButton = screen.getByRole('button', { name: /vorherige/i })
      fireEvent.click(prevPageButton)

      await waitFor(() => {
        expect(screen.getByText('Group 3')).toBeInTheDocument()
      })

      // Should still have 2 selections total
      expect(screen.getByText(/2.*ausgewählt/i)).toBeInTheDocument()
    })

    it('includes selections from all pages when confirming', async () => {
      paginatedMock()
      const onAddGroups = vi.fn()

      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <AddGroupModal {...paginatedProps} onAddGroups={onAddGroups} />
        </NextIntlClientProvider>,
      )

      // Page 0: select Group 5
      await waitFor(() => {
        expect(screen.getByText('Group 5')).toBeInTheDocument()
      })
      fireEvent.click(screen.getByLabelText(/gruppe auswählen group 5/i))

      // Navigate to page 2
      const nextPageButton = screen.getByRole('button', { name: /nächste/i })
      fireEvent.click(nextPageButton)

      // Page 1: select Group 14
      await waitFor(() => {
        expect(screen.getByText('Group 14')).toBeInTheDocument()
      })
      fireEvent.click(screen.getByLabelText(/gruppe auswählen group 14/i))

      // Confirm
      const confirmButton = screen.getByRole('button', { name: /hinzufügen/i })
      fireEvent.click(confirmButton)

      await waitFor(() => {
        expect(onAddGroups).toHaveBeenCalledWith(
          expect.arrayContaining([
            expect.objectContaining({ id: '5', name: 'Group 5' }),
            expect.objectContaining({ id: '14', name: 'Group 14' }),
          ]),
        )
        expect(onAddGroups.mock.calls[0][0]).toHaveLength(2)
      })
    })

    it('shows assigned items as checked+disabled and does not affect page size', async () => {
      paginatedMock()

      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <AddGroupModal {...paginatedProps} />
        </NextIntlClientProvider>,
      )

      // Wait for page 0 data to load
      await waitFor(() => {
        expect(screen.getByText('Group 1')).toBeInTheDocument()
      })

      // Group 1 is assigned — should be checked and disabled
      const assignedCheckbox = screen.getByLabelText(/gruppe auswählen group 1$/i)
      expect(assignedCheckbox).toBeChecked()
      expect(assignedCheckbox).toBeDisabled()

      // All 10 groups on page 0 should be visible (assigned items not filtered out)
      for (let i = 1; i <= 10; i++) {
        expect(screen.getByText(`Group ${i}`)).toBeInTheDocument()
      }
    })
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
