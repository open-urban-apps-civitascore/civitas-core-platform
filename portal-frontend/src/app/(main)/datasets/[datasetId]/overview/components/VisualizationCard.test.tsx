import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { toast } from 'sonner'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { Visualization } from '@/types/visualizations'

import { VisualizationCard } from './VisualizationCard'

const mockPush = vi.fn()
const mockRefresh = vi.fn()
const mockDeleteVisualization = vi.fn().mockResolvedValue(undefined)

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, refresh: mockRefresh }),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/app/services/api/visualizations/clientRequests', () => ({
  useDeleteVisualization: () => ({ mutateAsync: mockDeleteVisualization, isPending: false }),
}))

vi.mock('@/components/modals/warning-modal/WarningModal', () => ({
  // eslint-disable-next-line react/boolean-prop-naming
  WarningModal: ({ open, onDiscard, onConfirm }: { open: boolean; onDiscard: () => void; onConfirm: () => void }) =>
    open ? (
      <div data-testid="warningModal">
        <button data-testid="discardButton" onClick={onDiscard} />
        <button data-testid="confirmButton" onClick={onConfirm} />
      </div>
    ) : null,
}))

const makeVisualization = (overrides: Partial<Visualization> = {}): Visualization => ({
  id: 'vis-1',
  name: 'Sales Dashboard',
  type: 'dashboard',
  externalDashboardId: null,
  ...overrides,
})

const defaultProps = {
  visualization: makeVisualization(),
  datasetId: 'dataset-123',
  canEdit: true,
  isOpenDataAccess: false,
}

const renderComponent = (props: Partial<typeof defaultProps> = {}) =>
  render(<VisualizationCard {...defaultProps} {...props} />)

describe('VisualizationCard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockDeleteVisualization.mockResolvedValue(undefined)
    process.env.NEXT_PUBLIC_SUPERSET_URL = 'http://localhost:8092'
  })

  afterEach(() => {
    delete process.env.NEXT_PUBLIC_SUPERSET_URL
  })

  describe('Rendering', () => {
    it('displays the visualization name', () => {
      renderComponent()
      expect(screen.getByText('Sales Dashboard')).toBeInTheDocument()
    })

    it('displays the description when present', () => {
      renderComponent({ visualization: makeVisualization({ description: 'Tracks sales trends' }) })
      expect(screen.getByText('Tracks sales trends')).toBeInTheDocument()
    })

    it('shows the no-description fallback when description is absent', () => {
      renderComponent({ visualization: makeVisualization({ description: undefined }) })
      expect(screen.getByText('noDescription')).toBeInTheDocument()
    })

    it('shows openDataBadge when isOpenDataAccess is true', () => {
      renderComponent({ isOpenDataAccess: true })
      expect(screen.getByText('openDataBadge')).toBeInTheDocument()
      expect(screen.queryByText('protectedBadge')).not.toBeInTheDocument()
    })

    it('shows protectedBadge when isOpenDataAccess is false', () => {
      renderComponent({ isOpenDataAccess: false })
      expect(screen.getByText('protectedBadge')).toBeInTheDocument()
    })
  })

  describe('Permission gating', () => {
    it('shows the delete menu item when canEdit is true', async () => {
      renderComponent({ canEdit: true })
      await userEvent.click(screen.getByTestId('visualizationCardMenu-vis-1'))
      expect(screen.getByTestId('visualizationCardMenuDelete-vis-1')).toBeInTheDocument()
    })

    it('hides the delete menu item when canEdit is false', async () => {
      renderComponent({ canEdit: false })
      await userEvent.click(screen.getByTestId('visualizationCardMenu-vis-1'))
      expect(screen.queryByTestId('visualizationCardMenuDelete-vis-1')).not.toBeInTheDocument()
    })
  })

  describe('Navigation', () => {
    it('navigates to the visualization view route when the card is clicked', () => {
      renderComponent()
      fireEvent.click(screen.getByTestId('visualizationCard-vis-1'))
      expect(mockPush).toHaveBeenCalledWith('/datasets/dataset-123/visualizations/vis-1')
    })
  })

  describe('Superset deep link', () => {
    it('links directly to the dashboard when externalDashboardId is set', () => {
      renderComponent({ visualization: makeVisualization({ externalDashboardId: '42' }) })
      const link = screen.getByRole('link', { name: 'actions.openInSuperset' })
      expect(link).toHaveAttribute('href', 'http://localhost:8092/dashboard/42/')
      expect(link).toHaveAttribute('target', '_blank')
    })

    it('does not navigate to the internal detail page when the link is clicked', () => {
      renderComponent({ visualization: makeVisualization({ externalDashboardId: '42' }) })
      fireEvent.click(screen.getByRole('link', { name: 'actions.openInSuperset' }))
      expect(mockPush).not.toHaveBeenCalled()
    })

    it('shows a disabled button with a tooltip when the dashboard is not yet provisioned', async () => {
      renderComponent({ visualization: makeVisualization({ externalDashboardId: null }) })
      expect(screen.queryByRole('link', { name: 'actions.openInSuperset' })).not.toBeInTheDocument()
      const button = screen.getByRole('button', { name: 'actions.openInSuperset' })
      expect(button).toBeDisabled()
      await userEvent.hover(button)
      expect((await screen.findAllByText('notYetProvisionedTooltip')).length).toBeGreaterThan(0)
    })
  })

  describe('Delete flow', () => {
    it('opens the confirmation modal when delete is clicked', async () => {
      renderComponent()
      await userEvent.click(screen.getByTestId('visualizationCardMenu-vis-1'))
      await userEvent.click(screen.getByTestId('visualizationCardMenuDelete-vis-1'))
      expect(screen.getByTestId('warningModal')).toBeInTheDocument()
    })

    it('closes the modal without refreshing when discard is clicked', async () => {
      renderComponent()
      await userEvent.click(screen.getByTestId('visualizationCardMenu-vis-1'))
      await userEvent.click(screen.getByTestId('visualizationCardMenuDelete-vis-1'))
      fireEvent.click(screen.getByTestId('discardButton'))
      expect(screen.queryByTestId('warningModal')).not.toBeInTheDocument()
      expect(mockRefresh).not.toHaveBeenCalled()
    })

    it('calls router.refresh after confirming delete', async () => {
      renderComponent()
      await userEvent.click(screen.getByTestId('visualizationCardMenu-vis-1'))
      await userEvent.click(screen.getByTestId('visualizationCardMenuDelete-vis-1'))
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(mockRefresh).toHaveBeenCalled()
      })
    })

    it('calls the delete mutation with the dataset and visualization ids', async () => {
      renderComponent()
      await userEvent.click(screen.getByTestId('visualizationCardMenu-vis-1'))
      await userEvent.click(screen.getByTestId('visualizationCardMenuDelete-vis-1'))
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(mockDeleteVisualization).toHaveBeenCalledWith({ datasetId: 'dataset-123', visualizationId: 'vis-1' })
      })
      expect(toast.success).toHaveBeenCalled()
    })

    it('shows an error toast but still refreshes when the mutation fails', async () => {
      mockDeleteVisualization.mockRejectedValueOnce(new Error('network'))
      renderComponent()
      await userEvent.click(screen.getByTestId('visualizationCardMenu-vis-1'))
      await userEvent.click(screen.getByTestId('visualizationCardMenuDelete-vis-1'))
      fireEvent.click(screen.getByTestId('confirmButton'))
      await waitFor(() => {
        expect(toast.error).toHaveBeenCalled()
      })
      expect(mockRefresh).toHaveBeenCalled()
    })
  })
})
