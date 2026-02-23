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
  selection: {
    '2': true,
  },
  setSelection: vi.fn(),
  originalSelection: {},
  onGroupAssignmentUpdate: vi.fn(),
  haveGroupsBeenTouched: false,
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

  it('renders the confirm and cancel buttons in the correct initial state', () => {
    const renderedModal = renderWithProvider(<GroupAssignmentModal {...defaultProps} haveGroupsBeenTouched={false} />)

    const confirmButton = screen.getByText('actions.add') as HTMLButtonElement
    const cancelButton = screen.getByText('actions.cancel') as HTMLButtonElement

    expect(confirmButton).toBeInTheDocument()
    expect(cancelButton).toBeInTheDocument()

    expect(confirmButton.disabled).toBe(true)
    expect(cancelButton.disabled).toBe(false)

    renderedModal.unmount()
  })

  it('enables confirm button when changes have been made', () => {
    const renderedModal = renderWithProvider(<GroupAssignmentModal {...defaultProps} haveGroupsBeenTouched={true} />)

    const confirmButton = screen.getByText('actions.add') as HTMLButtonElement
    expect(confirmButton.disabled).toBe(false)

    renderedModal.unmount()
  })

  it('calls setSelection on group selection change', async () => {
    const renderedModal = renderWithProvider(<GroupAssignmentModal {...defaultProps} />)

    await waitFor(() => {
      const firstRowCheckbox = screen.getByRole('checkbox', { name: 'Select group Group 1' }) as HTMLInputElement
      fireEvent.click(firstRowCheckbox)
      expect(defaultProps.setSelection).toHaveBeenCalled()
    })
    renderedModal.unmount()
  })

  it('calls setSelection on select all checkbox click', async () => {
    const renderedModal = renderWithProvider(<GroupAssignmentModal {...defaultProps} />)

    await waitFor(() => {
      const selectAllCheckbox = screen.getAllByRole('checkbox')[0] as HTMLInputElement
      fireEvent.click(selectAllCheckbox)
      expect(defaultProps.setSelection).toHaveBeenCalled()
    })

    renderedModal.unmount()
  })

  it('calls handleGroupAssignmentUpdate on confirm click', async () => {
    const renderedModal = renderWithProvider(<GroupAssignmentModal {...defaultProps} haveGroupsBeenTouched={true} />)

    await waitFor(() => {
      const confirmButton = screen.getByText('actions.add')
      fireEvent.click(confirmButton)
    })

    expect(defaultProps.onGroupAssignmentUpdate).toHaveBeenCalled()
    expect(defaultProps.onOpenChange).toHaveBeenCalledWith(false)

    renderedModal.unmount()
  })

  it('checks the already selected groups correctly', async () => {
    const renderedModal = renderWithProvider(<GroupAssignmentModal {...defaultProps} haveGroupsBeenTouched={false} />)

    await waitFor(() => {
      const preSelectedCheckbox = screen.getByRole('checkbox', { name: 'Select group Group 2' }) as HTMLInputElement

      expect(preSelectedCheckbox).toBeInTheDocument()
      expect(preSelectedCheckbox).toBeChecked()
    })

    renderedModal.unmount()
  })

  it.skip('should select subrows when a parent row is selected', async () => {
    const renderedModal = renderWithProvider(<GroupAssignmentModal {...defaultProps} />)

    await waitFor(() => {
      const parentRowCheckbox = screen.getByRole('checkbox', { name: 'Select group Group 1' }) as HTMLInputElement
      fireEvent.click(parentRowCheckbox)

      expect(defaultProps.setSelection).toHaveBeenCalledWith({
        '1': true,
        '2': true,
        '3': true,
        '4': true,
        '5': true,
        '6': true,
      })
    })

    renderedModal.unmount()
  })

  it('calls onOpenChange on cancel click', async () => {
    const renderedModal = renderWithProvider(<GroupAssignmentModal {...defaultProps} />)

    await waitFor(() => {
      const cancelButton = screen.getByText('actions.cancel')
      fireEvent.click(cancelButton)
    })

    expect(defaultProps.setSelection).toHaveBeenCalledWith(defaultProps.originalSelection)
    expect(defaultProps.onOpenChange).toHaveBeenCalledWith(false)

    renderedModal.unmount()
  })
})
