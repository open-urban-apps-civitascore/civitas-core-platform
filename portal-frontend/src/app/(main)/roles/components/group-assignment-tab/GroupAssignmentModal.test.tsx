import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { JSX } from 'react'
import { vi } from 'vitest'

import groups from '@/__mocks__/groups/groupsResponse.json'
import { apiRequest } from '@/app/services/api/request/apiRequest'

import { GroupAssignmentModal } from './GroupAssignmentModal'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    refresh: vi.fn(),
  }),
  useSearchParams: () => new URLSearchParams('page=1'),
  usePathname: vi.fn(),
}))

vi.mock('@/app/services/api/request/apiRequest', () => ({
  apiRequest: vi.fn(),
}))

const mockApiRequest = vi.mocked(apiRequest)

const defaultProps = {
  open: true,
  onOpenChange: vi.fn(),
  assignedGroupIds: ['2'],
  onAddGroups: vi.fn(),
  roleName: 'Admin',
}

const queryClient = new QueryClient()

const renderWithProvider = (children: JSX.Element) =>
  render(<QueryClientProvider client={queryClient}>{children}</QueryClientProvider>)

describe('GroupAssignmentModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockApiRequest.mockResolvedValue({ data: groups })
  })

  it('renders modal title and description', () => {
    const renderedModal = renderWithProvider(<GroupAssignmentModal {...defaultProps} />)

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('groupAssignmentTab.modal.title')).toBeInTheDocument()
    expect(screen.getByText('groupAssignmentTab.modal.description')).toBeInTheDocument()

    renderedModal.unmount()
  })

  it('renders the group table correctly', async () => {
    const renderedModal = renderWithProvider(<GroupAssignmentModal {...defaultProps} />)
    await waitFor(() => {
      expect(screen.getByRole('cell', { name: 'Group 1' })).toBeInTheDocument()
      expect(screen.getByRole('cell', { name: 'description for Group 1' })).toBeInTheDocument()
      expect(screen.getByRole('cell', { name: '2' })).toBeInTheDocument() // User count for Group 1
      expect(screen.getByRole('cell', { name: 'Group 2' })).toBeInTheDocument()
      expect(screen.getByRole('cell', { name: '0' })).toBeInTheDocument() // User count for Group 2
      expect(screen.getByRole('cell', { name: 'description for Group 2' })).toBeInTheDocument()
    })
    renderedModal.unmount()
  })

  it('renders the confirm button disabled when no new groups are selected', () => {
    const renderedModal = renderWithProvider(<GroupAssignmentModal {...defaultProps} />)

    const confirmButton = screen.getByText('actions.add') as HTMLButtonElement
    const cancelButton = screen.getByText('actions.cancel') as HTMLButtonElement

    expect(confirmButton).toBeInTheDocument()
    expect(cancelButton).toBeInTheDocument()

    expect(confirmButton.disabled).toBe(true)
    expect(cancelButton.disabled).toBe(false)

    renderedModal.unmount()
  })

  it('enables confirm button when a new group is selected', async () => {
    const renderedModal = renderWithProvider(<GroupAssignmentModal {...defaultProps} />)

    await waitFor(() => {
      const group1Checkbox = screen.getByRole('checkbox', { name: 'Select group Group 1' }) as HTMLInputElement
      fireEvent.click(group1Checkbox)
    })

    const confirmButton = screen.getByText('actions.add') as HTMLButtonElement
    expect(confirmButton.disabled).toBe(false)

    renderedModal.unmount()
  })

  it('shows assigned groups as checked and disabled', async () => {
    const renderedModal = renderWithProvider(<GroupAssignmentModal {...defaultProps} />)

    await waitFor(() => {
      const assignedCheckbox = screen.getByRole('checkbox', { name: 'Select group Group 2' }) as HTMLInputElement

      expect(assignedCheckbox).toBeInTheDocument()
      expect(assignedCheckbox).toBeChecked()
      expect(assignedCheckbox).toBeDisabled()
    })

    renderedModal.unmount()
  })

  it('calls onAddGroups with newly selected group IDs on confirm', async () => {
    const renderedModal = renderWithProvider(<GroupAssignmentModal {...defaultProps} />)

    await waitFor(() => {
      const group1Checkbox = screen.getByRole('checkbox', { name: 'Select group Group 1' }) as HTMLInputElement
      fireEvent.click(group1Checkbox)
    })

    const confirmButton = screen.getByText('actions.add')
    fireEvent.click(confirmButton)

    expect(defaultProps.onAddGroups).toHaveBeenCalledWith(['1'])
    expect(defaultProps.onOpenChange).toHaveBeenCalledWith(false)

    renderedModal.unmount()
  })

  it('calls onOpenChange on cancel click', async () => {
    const renderedModal = renderWithProvider(<GroupAssignmentModal {...defaultProps} />)

    await waitFor(() => {
      const cancelButton = screen.getByText('actions.cancel')
      fireEvent.click(cancelButton)
    })

    expect(defaultProps.onOpenChange).toHaveBeenCalledWith(false)

    renderedModal.unmount()
  })
})
