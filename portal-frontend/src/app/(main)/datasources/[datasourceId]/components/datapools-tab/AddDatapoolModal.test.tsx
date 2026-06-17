import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { Datapool } from '@/types/datapools'

import { AddDatapoolModal } from './AddDatapoolModal'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string, params?: Record<string, unknown>) =>
    params ? `${key} ${JSON.stringify(params)}` : key,
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/',
}))

vi.mock('@/hooks/use-mobile', () => ({
  useIsMobile: () => false,
}))

const mockUseGetDatapools = vi.fn().mockReturnValue({ data: undefined, isLoading: false, isError: false })
vi.mock('@/app/services/api/datapools/clientRequests', () => ({
  useGetDatapools: (...args: unknown[]) => mockUseGetDatapools(...args),
}))

const mockDatapools: Datapool[] = [
  {
    id: 'dp-1',
    name: 'Datapool Alpha',
    description: 'First datapool',
    contactPerson: null,
    createdAt: '2024-01-01T00:00:00Z',
    modifiedAt: '2024-01-01T00:00:00Z',
  },
  {
    id: 'dp-2',
    name: 'Datapool Beta',
    description: 'Second datapool',
    contactPerson: null,
    createdAt: '2024-01-02T00:00:00Z',
    modifiedAt: '2024-01-02T00:00:00Z',
  },
]

const defaultProps = {
  open: true,
  assignedDatapoolIds: [],
  onAddDatapools: vi.fn(),
  onOpenChange: vi.fn(),
}

describe('AddDatapoolModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUseGetDatapools.mockReturnValue({ data: undefined, isLoading: false, isError: false })
  })

  describe('Visibility', () => {
    it('does not render when open is false', () => {
      render(<AddDatapoolModal {...defaultProps} open={false} />)
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('renders the dialog when open is true', () => {
      render(<AddDatapoolModal {...defaultProps} />)
      expect(screen.getByRole('dialog')).toBeInTheDocument()
    })

    it('renders title and description', () => {
      render(<AddDatapoolModal {...defaultProps} />)
      expect(screen.getByText('title')).toBeInTheDocument()
      expect(screen.getByText('description')).toBeInTheDocument()
    })
  })

  describe('With datapools', () => {
    beforeEach(() => {
      mockUseGetDatapools.mockReturnValue({
        data: { data: mockDatapools, totalPages: 1 },
        isLoading: false,
        isError: false,
      })
    })

    it('renders datapool names in the table', () => {
      render(<AddDatapoolModal {...defaultProps} />)
      expect(screen.getByText('Datapool Alpha')).toBeInTheDocument()
      expect(screen.getByText('Datapool Beta')).toBeInTheDocument()
    })

    it('disables checkboxes for already assigned datapools', () => {
      render(<AddDatapoolModal {...defaultProps} assignedDatapoolIds={['dp-1']} />)
      const checkboxes = screen.getAllByRole('checkbox')
      const dp1Checkbox = checkboxes.find(cb => cb.getAttribute('aria-label')?.includes('Datapool Alpha'))
      expect(dp1Checkbox).toBeDisabled()
    })

    it('confirm button is disabled when no new datapools are selected', () => {
      render(<AddDatapoolModal {...defaultProps} />)
      expect(screen.getByTestId('confirmButton')).toBeDisabled()
    })

    it('confirm button is enabled after selecting a datapool', async () => {
      const user = userEvent.setup()
      render(<AddDatapoolModal {...defaultProps} />)

      const checkboxes = screen.getAllByRole('checkbox')
      const dp1Checkbox = checkboxes.find(cb => cb.getAttribute('aria-label')?.includes('Datapool Alpha'))!
      await user.click(dp1Checkbox)

      expect(screen.getByTestId('confirmButton')).not.toBeDisabled()
    })

    it('calls onAddDatapools with selected datapools on confirm', async () => {
      const user = userEvent.setup()
      const onAddDatapools = vi.fn()
      render(<AddDatapoolModal {...defaultProps} onAddDatapools={onAddDatapools} />)

      const checkboxes = screen.getAllByRole('checkbox')
      const dp1Checkbox = checkboxes.find(cb => cb.getAttribute('aria-label')?.includes('Datapool Alpha'))!
      await user.click(dp1Checkbox)
      await user.click(screen.getByTestId('confirmButton'))

      expect(onAddDatapools).toHaveBeenCalledWith(expect.arrayContaining([expect.objectContaining({ id: 'dp-1' })]))
    })

    it('shows selected count label', async () => {
      const user = userEvent.setup()
      render(<AddDatapoolModal {...defaultProps} />)

      const checkboxes = screen.getAllByRole('checkbox')
      const dp1Checkbox = checkboxes.find(cb => cb.getAttribute('aria-label')?.includes('Datapool Alpha'))!
      await user.click(dp1Checkbox)

      expect(screen.getByText('selectedDatapools {"number":1}')).toBeInTheDocument()
    })
  })

  describe('Error state', () => {
    it('shows error message when loading fails', () => {
      mockUseGetDatapools.mockReturnValue({ data: undefined, isLoading: false, isError: true })
      render(<AddDatapoolModal {...defaultProps} />)
      expect(screen.getByText('errors.loadingError')).toBeInTheDocument()
    })
  })

  describe('Cancel', () => {
    it('calls onOpenChange with false when cancel is clicked', () => {
      const onOpenChange = vi.fn()
      render(<AddDatapoolModal {...defaultProps} onOpenChange={onOpenChange} />)
      fireEvent.click(screen.getByTestId('cancelButton'))
      expect(onOpenChange).toHaveBeenCalledWith(false)
    })
  })

  describe('Only fetches when open', () => {
    it('passes isEnabled=false to useGetDatapools when closed', () => {
      render(<AddDatapoolModal {...defaultProps} open={false} />)
      expect(mockUseGetDatapools).toHaveBeenCalledWith(expect.objectContaining({ isEnabled: false }))
    })

    it('passes isEnabled=true to useGetDatapools when open', () => {
      render(<AddDatapoolModal {...defaultProps} open={true} />)
      expect(mockUseGetDatapools).toHaveBeenCalledWith(expect.objectContaining({ isEnabled: true }))
    })
  })
})
