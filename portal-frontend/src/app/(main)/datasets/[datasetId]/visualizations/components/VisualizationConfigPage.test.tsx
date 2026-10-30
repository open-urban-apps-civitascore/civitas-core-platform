import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { UseFormReturn } from 'react-hook-form'
import { toast } from 'sonner'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { Dataset } from '@/types/datasets'
import { Visualization, VISUALIZATION_TYPE_QUERY, VisualizationFormData } from '@/types/visualizations'

import { VisualizationConfigPage } from './VisualizationConfigPage'

// --- mocks ---

const mockPush = vi.fn()
const mockReplace = vi.fn()
const mockCreateVisualization = vi.fn().mockResolvedValue(undefined)
const mockUpdateVisualization = vi.fn().mockResolvedValue(undefined)
let mockCanEdit = true
let mockSearchParams = new URLSearchParams()

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace, refresh: vi.fn() }),
  usePathname: () => '/datasets/test-id/visualizations/vis-1',
  useSearchParams: () => mockSearchParams,
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/hooks/use-permissions', () => ({
  usePermissions: () => ({ hasScopedPermission: () => mockCanEdit }),
}))

vi.mock('@/app/services/api/visualizations/clientRequests', () => ({
  useCreateVisualization: () => ({ mutateAsync: mockCreateVisualization, isPending: false }),
  useUpdateVisualization: () => ({ mutateAsync: mockUpdateVisualization, isPending: false }),
}))

vi.mock('./VisualizationConfigForm', () => ({
  VisualizationConfigForm: ({
    form,
    isReadOnly,
  }: {
    form: UseFormReturn<VisualizationFormData>
    isReadOnly?: boolean
  }) => <input data-testid="nameInput" disabled={isReadOnly} {...form.register('name')} />,
}))

vi.mock('@/components/page-header/PageHeader', () => ({
  PageHeader: ({
    title,
    customElement,
    badgeTitle,
  }: {
    title?: string
    customElement?: React.ReactNode
    badgeTitle?: string
  }) => (
    <div data-testid="pageHeader">
      {title && <h1>{title}</h1>}
      {badgeTitle && <span>{badgeTitle}</span>}
      {customElement}
    </div>
  ),
}))

vi.mock('@/components/page-container/PageContainer', () => ({
  PageContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/components/page-background/PageBackground', () => ({
  PageBackground: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/components/content-card/ContentCard', () => ({
  ContentCard: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

// --- helpers ---

const makeDataset = (overrides: Partial<Dataset> = {}): Dataset => ({
  id: 'test-id',
  name: 'Test Dataset',
  description: 'A test dataset',
  dataSetStatus: 'DRAFT',
  pipelines: [],
  createdAt: '2024-01-01',
  modifiedAt: '2024-01-01',
  openDataAccess: false,
  createdBy: { id: 'user-1', name: 'User One' },
  ...overrides,
})

const makeVisualization = (overrides: Partial<Visualization> = {}): Visualization => ({
  id: 'vis-1',
  name: 'Sales Dashboard',
  type: 'dashboard',
  externalDashboardId: null,
  released: false,
  standaloneMode: 1,
  showFilters: true,
  filterExpansion: 'DEFAULT',
  ...overrides,
})

const renderComponent = (props: { dataset?: Dataset; existingVisualization?: Visualization } = {}) =>
  render(
    <VisualizationConfigPage
      dataset={props.dataset ?? makeDataset()}
      visualizationType={props.existingVisualization ? undefined : VISUALIZATION_TYPE_QUERY.DASHBOARD}
      existingVisualization={props.existingVisualization}
    />,
  )

// --- tests ---

describe('VisualizationConfigPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockCreateVisualization.mockResolvedValue(undefined)
    mockUpdateVisualization.mockResolvedValue(undefined)
    mockCanEdit = true
    mockSearchParams = new URLSearchParams()
  })

  describe('Cancel / exit without unsaved changes', () => {
    it('navigates to dataset overview when cancel is clicked with no changes', () => {
      renderComponent()
      fireEvent.click(screen.getByTestId('cancelButton'))
      expect(mockPush).toHaveBeenCalledWith('/datasets/test-id')
    })
  })

  describe('Exit warning modal', () => {
    it('opens the exit modal when cancel is clicked with unsaved changes', async () => {
      renderComponent()
      await userEvent.type(screen.getByTestId('nameInput'), 'My Dashboard')
      fireEvent.click(screen.getByTestId('cancelButton'))
      expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument()
    })

    it('navigates to dataset overview when discard is clicked in the modal', async () => {
      renderComponent()
      await userEvent.type(screen.getByTestId('nameInput'), 'My Dashboard')
      fireEvent.click(screen.getByTestId('cancelButton'))
      fireEvent.click(screen.getByTestId('discardButton'))
      expect(mockPush).toHaveBeenCalledWith('/datasets/test-id')
    })
  })

  describe('Save', () => {
    it('creates the visualization and navigates to the dataset overview on success', async () => {
      renderComponent()
      await userEvent.type(screen.getByTestId('nameInput'), 'My Dashboard')
      fireEvent.submit(screen.getByTestId('visualizationConfigForm'))
      await waitFor(() => {
        expect(mockCreateVisualization).toHaveBeenCalledWith({
          datasetId: 'test-id',
          visualization: expect.objectContaining({ name: 'My Dashboard' }),
        })
      })
      expect(toast.success).toHaveBeenCalled()
      expect(mockPush).toHaveBeenCalledWith('/datasets/test-id')
    })

    it('shows an error toast and does not navigate when the mutation fails', async () => {
      mockCreateVisualization.mockRejectedValueOnce(new Error('network'))
      renderComponent()
      await userEvent.type(screen.getByTestId('nameInput'), 'My Dashboard')
      fireEvent.submit(screen.getByTestId('visualizationConfigForm'))
      await waitFor(() => {
        expect(toast.error).toHaveBeenCalled()
      })
      expect(mockPush).not.toHaveBeenCalled()
    })

    it('confirm button is disabled when there are no unsaved changes', () => {
      renderComponent()
      expect(screen.getByTestId('confirmButton')).toBeDisabled()
    })
  })

  describe('Access badges', () => {
    it('shows the protected badge when openDataAccess is false', () => {
      renderComponent({ dataset: makeDataset({ openDataAccess: false }) })
      expect(screen.getByText('protectedBadge')).toBeInTheDocument()
    })

    it('shows the open data badge when openDataAccess is true', () => {
      renderComponent({ dataset: makeDataset({ openDataAccess: true }) })
      expect(screen.getByText('openDataBadge')).toBeInTheDocument()
    })
  })

  describe('Edit mode (existing visualization)', () => {
    it('shows the visualization name as the page title', () => {
      renderComponent({ existingVisualization: makeVisualization({ name: 'Sales Dashboard' }) })
      expect(screen.getByText('Sales Dashboard')).toBeInTheDocument()
    })

    it('starts read-only and shows an Edit button when the viewer can edit', () => {
      renderComponent({ existingVisualization: makeVisualization() })
      expect(screen.getByTestId('nameInput')).toBeDisabled()
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
    })

    it('hides the Edit button when the viewer lacks update permission', () => {
      mockCanEdit = false
      renderComponent({ existingVisualization: makeVisualization() })
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
    })

    it('keeps the form read-only when mode=edit is set without permission', () => {
      mockCanEdit = false
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent({ existingVisualization: makeVisualization() })
      expect(screen.getByTestId('nameInput')).toBeDisabled()
      fireEvent.submit(screen.getByTestId('visualizationConfigForm'))
      expect(mockUpdateVisualization).not.toHaveBeenCalled()
    })

    it('disables dashboard creation on an available dataset', () => {
      renderComponent({ dataset: makeDataset({ dataSetStatus: 'AVAILABLE' }) })
      expect(screen.getByTestId('nameInput')).toBeDisabled()
      fireEvent.submit(screen.getByTestId('visualizationConfigForm'))
      expect(mockCreateVisualization).not.toHaveBeenCalled()
    })

    it('switches to editable mode and updates the URL when Edit is clicked', async () => {
      renderComponent({ existingVisualization: makeVisualization() })
      await userEvent.click(screen.getByTestId('editButton'))
      expect(screen.getByTestId('nameInput')).not.toBeDisabled()
      expect(mockReplace).toHaveBeenCalledWith('/datasets/test-id/visualizations/vis-1?mode=edit', { scroll: false })
    })

    it('starts editable when the mode=edit query param is present', () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent({ existingVisualization: makeVisualization() })
      expect(screen.getByTestId('nameInput')).not.toBeDisabled()
    })

    it('updates the visualization, returns to read-only, and refreshes on success', async () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent({ existingVisualization: makeVisualization({ id: 'vis-1' }) })
      await userEvent.clear(screen.getByTestId('nameInput'))
      await userEvent.type(screen.getByTestId('nameInput'), 'Updated Name')
      fireEvent.submit(screen.getByTestId('visualizationConfigForm'))
      await waitFor(() => {
        expect(mockUpdateVisualization).toHaveBeenCalledWith({
          datasetId: 'test-id',
          visualizationId: 'vis-1',
          visualization: expect.objectContaining({ name: 'Updated Name' }),
        })
      })
      expect(toast.success).toHaveBeenCalled()
      expect(mockPush).not.toHaveBeenCalled()
      await waitFor(() => {
        expect(screen.getByTestId('nameInput')).toBeDisabled()
      })
    })

    it('returns to read-only without navigating away when cancel is clicked with no changes', () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent({ existingVisualization: makeVisualization() })
      fireEvent.click(screen.getByTestId('cancelButton'))
      expect(mockPush).not.toHaveBeenCalled()
      expect(screen.getByTestId('nameInput')).toBeDisabled()
    })

    it('opens the exit warning modal when cancel is clicked with unsaved changes, and discarding returns to read-only', async () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent({ existingVisualization: makeVisualization() })
      await userEvent.type(screen.getByTestId('nameInput'), ' extra')
      fireEvent.click(screen.getByTestId('cancelButton'))
      expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument()
      fireEvent.click(screen.getByTestId('discardButton'))
      expect(mockPush).not.toHaveBeenCalled()
      expect(screen.getByTestId('nameInput')).toBeDisabled()
    })
  })
})
