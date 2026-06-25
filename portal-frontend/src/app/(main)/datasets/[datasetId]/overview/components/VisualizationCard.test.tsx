import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { Visualization } from '@/types/visualizations'

import { VisualizationCard } from './VisualizationCard'

const mockPush = vi.fn()
const mockRefresh = vi.fn()

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, refresh: mockRefresh }),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
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
  })
})
