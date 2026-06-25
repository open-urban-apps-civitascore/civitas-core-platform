import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { UseFormReturn } from 'react-hook-form'
import { toast } from 'sonner'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { Dataset } from '@/types/datasets'
import { VISUALIZATION_TYPE_QUERY, VisualizationFormData } from '@/types/visualizations'

import { VisualizationConfigPage } from './VisualizationConfigPage'

// --- mocks ---

const mockPush = vi.fn()

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('./VisualizationConfigForm', () => ({
  VisualizationConfigForm: ({ form }: { form: UseFormReturn<VisualizationFormData> }) => (
    <input data-testid="nameInput" {...form.register('name')} />
  ),
}))

vi.mock('@/components/page-header/PageHeader', () => ({
  PageHeader: ({ customElement, badgeTitle }: { customElement?: React.ReactNode; badgeTitle?: string }) => (
    <div data-testid="pageHeader">
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

const renderComponent = (props: { dataset?: Dataset } = {}) =>
  render(
    <VisualizationConfigPage
      dataset={props.dataset ?? makeDataset()}
      visualizationType={VISUALIZATION_TYPE_QUERY.DASHBOARD}
    />,
  )

// --- tests ---

describe('VisualizationConfigPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
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
    it('shows an error toast on submit (API not yet implemented)', async () => {
      renderComponent()
      await userEvent.type(screen.getByTestId('nameInput'), 'My Dashboard')
      fireEvent.submit(screen.getByTestId('visualizationConfigForm'))
      await waitFor(() => {
        expect(toast.error).toHaveBeenCalled()
      })
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
})
