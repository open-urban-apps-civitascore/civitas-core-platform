import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { Visualization } from '@/types/visualizations'

import { VisualizationList } from './VisualizationList'

vi.mock('./VisualizationCard', () => ({
  VisualizationCard: ({ visualization, canEdit }: { visualization: Visualization; canEdit: boolean }) => (
    <div data-testid={`visualizationCard-${visualization.id}`} data-can-edit={String(canEdit)} />
  ),
}))

vi.mock('@/components/guarded-link/GuardedLink', () => ({
  GuardedLink: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

const makeVisualization = (overrides: Partial<Visualization> = {}): Visualization => ({
  id: '1',
  name: 'Sales Dashboard',
  type: 'dashboard',
  externalDashboardId: null,
  ...overrides,
})

const defaultProps = {
  datasetId: 'dataset-123',
  visualizations: [] as Visualization[],
  canEdit: true,
  isOpenDataAccess: false,
}

const renderComponent = (props: Partial<typeof defaultProps> = {}) =>
  render(<VisualizationList {...defaultProps} {...props} />)

describe('VisualizationList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Add button', () => {
    it('renders the add visualization dropdown button', () => {
      renderComponent()
      expect(screen.getByText('addButton')).toBeInTheDocument()
    })

    it('opens the dropdown and shows the dashboard option', async () => {
      renderComponent()
      await userEvent.click(screen.getByText('addButton'))
      expect(screen.getByText('dashboard')).toBeInTheDocument()
    })

    it('dashboard option links to the correct route', async () => {
      renderComponent({ datasetId: 'ds-1' })
      await userEvent.click(screen.getByText('addButton'))
      const link = screen.getByText('dashboard').closest('a')
      expect(link).toHaveAttribute('href', '/datasets/ds-1/visualizations?type=dashboard')
    })
  })

  describe('Visualization cards', () => {
    it('renders a VisualizationCard for each passed-in visualization', () => {
      renderComponent({ visualizations: [makeVisualization({ id: '1' }), makeVisualization({ id: '2' })] })
      expect(screen.getByTestId('visualizationCard-1')).toBeInTheDocument()
      expect(screen.getByTestId('visualizationCard-2')).toBeInTheDocument()
    })

    it('passes canEdit to each VisualizationCard', () => {
      renderComponent({ visualizations: [makeVisualization({ id: '1' })], canEdit: false })
      expect(screen.getByTestId('visualizationCard-1')).toHaveAttribute('data-can-edit', 'false')
    })

    it('shows the empty state when there are no visualizations', () => {
      renderComponent({ visualizations: [] })
      expect(screen.getByText('empty')).toBeInTheDocument()
    })
  })
})
