import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { toast } from 'sonner'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getDatasetResources } from '@/app/services/api/datasets/data-sink-removal/clientRequests'
import { PipelineBasicInfo } from '@/types/datasets'
import { DataSink } from '@/types/datasinks'
import { Layer } from '@/types/layers'
import { DatasetResources } from '@/utils/dataSinkRemoval'

import { PipelineCard } from './PipelineCard'

const mockRequestNavigation = vi.fn()
const mockRefresh = vi.fn()
const mockDeletePipelineMutateAsync = vi.fn().mockResolvedValue(undefined)
let mockHasUnsavedChanges = false

vi.mock('@/contexts/unsaved-changes/UnsavedChangesContext', () => ({
  useUnsavedChanges: () => ({
    hasUnsavedChanges: mockHasUnsavedChanges,
    requestNavigation: mockRequestNavigation,
    requestBack: vi.fn(),
    setHasUnsavedChanges: vi.fn(),
    setSaveHandler: vi.fn(),
  }),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), refresh: mockRefresh }),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
  useLocale: () => 'en',
}))

vi.mock('@/app/services/api/pipelines/clientRequests', () => ({
  useDeletePipeline: () => ({ mutateAsync: mockDeletePipelineMutateAsync, isPending: false }),
}))

vi.mock('@/app/services/api/datasets/data-sink-removal/clientRequests', () => ({
  getDatasetResources: vi.fn(),
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

const makePipeline = (overrides: Partial<PipelineBasicInfo> = {}): PipelineBasicInfo => ({
  id: 'pipeline-1',
  name: 'My Pipeline',
  ...overrides,
})

const defaultProps = {
  datasetId: 'dataset-123',
  pipeline: makePipeline(),
  badges: [] as string[],
  canDelete: true,
}

const NO_REMOVAL: DatasetResources = { dataSinks: [], layers: [], apis: [] }

const POSTGIS_SINK = {
  id: 'sink-1',
  pipelineIds: ['pipeline-1'],
  dataSinkType: 'POSTGIS',
  provisioned: true,
} as DataSink

const LAYER_REMOVAL: DatasetResources = {
  dataSinks: [POSTGIS_SINK],
  layers: [{ id: 'layer-1', dataSinkId: 'sink-1', layerName: 'trees', title: 'Trees' } as Layer],
  apis: [{ id: 'api-1', slug: 'ows', name: 'Trees API', standard: 'OWS' }],
}

const API_ONLY_REMOVAL: DatasetResources = {
  dataSinks: [{ ...POSTGIS_SINK, provisioned: false }],
  layers: [],
  apis: [{ id: 'api-1', slug: 'ows', name: 'Trees API', standard: 'OWS' }],
}

const renderComponent = (props: Partial<typeof defaultProps> = {}) =>
  render(<PipelineCard {...defaultProps} {...props} />)

describe('PipelineCard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockHasUnsavedChanges = false
    mockDeletePipelineMutateAsync.mockResolvedValue(undefined)
    vi.mocked(getDatasetResources).mockResolvedValue(NO_REMOVAL)
  })

  const openMenu = () => userEvent.click(screen.getByTestId('pipelineCardMenu-pipeline-1'))

  describe('Rendering', () => {
    it('displays the pipeline name', () => {
      renderComponent()
      expect(screen.getByText('My Pipeline')).toBeInTheDocument()
    })

    it('links the card to the pipeline editor route', () => {
      renderComponent()
      expect(screen.getByTestId('pipelineCard-pipeline-1')).toHaveAttribute(
        'href',
        '/datasets/dataset-123/data-flow/pipeline-editor?pipeline=pipeline-1',
      )
    })

    it('renders no badge row when there is no status and no connector badges', () => {
      renderComponent()
      expect(screen.queryByTestId('pipelineCardBadges-pipeline-1')).not.toBeInTheDocument()
    })

    it('renders connector badges even without a runtime status', () => {
      renderComponent({ badges: ['MQTT', 'FROST'] })
      expect(screen.getByTestId('pipelineCardBadges-pipeline-1')).toBeInTheDocument()
      expect(screen.getByText('MQTT')).toBeInTheDocument()
      expect(screen.getByText('FROST')).toBeInTheDocument()
    })
  })

  describe('Active status', () => {
    it('shows the Active badge when runtimeStatus is OK', () => {
      renderComponent({ pipeline: makePipeline({ runtimeStatus: { state: 'OK' } }) })
      expect(screen.getByText('activeLabel')).toBeInTheDocument()
      expect(screen.queryByText('errorLabel')).not.toBeInTheDocument()
    })
  })

  describe('Error status', () => {
    const errorPipeline = makePipeline({
      runtimeStatus: { state: 'ERROR', message: 'Boom', occurredAt: '2024-01-01T00:00:00Z' },
    })

    it('shows the Error badge when runtimeStatus is ERROR', () => {
      renderComponent({ pipeline: errorPipeline })
      expect(screen.getByText('errorLabel')).toBeInTheDocument()
      expect(screen.queryByText('activeLabel')).not.toBeInTheDocument()
    })

    it('does not navigate the card when the error badge is clicked', () => {
      renderComponent({ pipeline: errorPipeline })
      const button = screen.getByRole('button', { name: 'errorDetails' })
      expect(fireEvent.click(button)).toBe(false)
    })
  })

  describe('Three-dot menu', () => {
    it('does not navigate the card when the menu trigger is clicked', () => {
      renderComponent()
      const trigger = screen.getByTestId('pipelineCardMenu-pipeline-1')
      expect(fireEvent.click(trigger)).toBe(false)
    })

    it('shows an "Open" item linking to the pipeline editor route', async () => {
      renderComponent()
      await openMenu()
      expect(screen.getByTestId('pipelineCardMenuOpen-pipeline-1')).toHaveAttribute(
        'href',
        '/datasets/dataset-123/data-flow/pipeline-editor?pipeline=pipeline-1',
      )
    })

    it('shows the delete item when canDelete is true', async () => {
      renderComponent({ canDelete: true })
      await openMenu()
      expect(screen.getByTestId('pipelineCardMenuDelete-pipeline-1')).toBeInTheDocument()
    })

    it('hides the delete item when canDelete is false', async () => {
      renderComponent({ canDelete: false })
      await openMenu()
      expect(screen.queryByTestId('pipelineCardMenuDelete-pipeline-1')).not.toBeInTheDocument()
    })
  })

  describe('Unsaved-changes guard', () => {
    it('requests navigation instead of following the "Open" menu item when there are unsaved changes', async () => {
      mockHasUnsavedChanges = true
      renderComponent()
      await openMenu()
      await userEvent.click(screen.getByTestId('pipelineCardMenuOpen-pipeline-1'))
      expect(mockRequestNavigation).toHaveBeenCalledWith(
        '/datasets/dataset-123/data-flow/pipeline-editor?pipeline=pipeline-1',
      )
    })
  })

  describe('Delete flow', () => {
    const startDelete = async () => {
      await openMenu()
      await userEvent.click(screen.getByTestId('pipelineCardMenuDelete-pipeline-1'))
      await screen.findByTestId('exitWarningModal')
    }

    it('shows no Layers or APIs when the delete takes none', async () => {
      renderComponent()
      await startDelete()
      expect(screen.queryByTestId('removalConfirmLayers')).not.toBeInTheDocument()
      expect(screen.queryByTestId('removalConfirmApis')).not.toBeInTheDocument()
    })

    it('lists the Layers and APIs that the delete takes', async () => {
      vi.mocked(getDatasetResources).mockResolvedValue(LAYER_REMOVAL)
      renderComponent()
      await startDelete()
      expect(screen.getByTestId('removalConfirmLayers')).toHaveTextContent('Trees')
      expect(screen.getByTestId('removalConfirmApis')).toHaveTextContent('Trees API')
      expect(screen.getByTestId('removalConfirmStoredData')).toBeInTheDocument()
    })

    it('closes the modal without deleting when cancel is clicked', async () => {
      renderComponent()
      await startDelete()
      fireEvent.click(screen.getByTestId('discardButton'))
      await waitFor(() => expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument())
      expect(mockDeletePipelineMutateAsync).not.toHaveBeenCalled()
    })

    it('deletes the pipeline with confirmDataLoss when it removes a provisioned data sink', async () => {
      vi.mocked(getDatasetResources).mockResolvedValue(LAYER_REMOVAL)
      renderComponent()
      await startDelete()
      fireEvent.click(screen.getByTestId('confirmButton'))

      await waitFor(() => {
        expect(mockDeletePipelineMutateAsync).toHaveBeenCalledWith({
          pipelineId: 'pipeline-1',
          isDataLossConfirmed: true,
        })
      })
    })

    it('deletes the pipeline with confirmDataLoss when it removes only an API', async () => {
      vi.mocked(getDatasetResources).mockResolvedValue(API_ONLY_REMOVAL)
      renderComponent()
      await startDelete()
      expect(screen.queryByTestId('removalConfirmStoredData')).not.toBeInTheDocument()
      fireEvent.click(screen.getByTestId('confirmButton'))

      await waitFor(() => {
        expect(mockDeletePipelineMutateAsync).toHaveBeenCalledWith({
          pipelineId: 'pipeline-1',
          isDataLossConfirmed: true,
        })
      })
    })

    it('deletes the pipeline without confirmDataLoss when it removes nothing that needs it', async () => {
      renderComponent()
      await startDelete()
      fireEvent.click(screen.getByTestId('confirmButton'))

      await waitFor(() => {
        expect(mockDeletePipelineMutateAsync).toHaveBeenCalledWith({
          pipelineId: 'pipeline-1',
          isDataLossConfirmed: false,
        })
      })
    })

    it('shows success toast and refreshes the page after successful delete', async () => {
      renderComponent()
      await startDelete()
      fireEvent.click(screen.getByTestId('confirmButton'))

      await waitFor(() => {
        expect(toast.success).toHaveBeenCalled()
        expect(mockRefresh).toHaveBeenCalled()
      })
    })

    it('shows an error toast when deletePipeline rejects', async () => {
      mockDeletePipelineMutateAsync.mockRejectedValueOnce(new Error('network'))
      renderComponent()
      await startDelete()
      fireEvent.click(screen.getByTestId('confirmButton'))

      await waitFor(() => {
        expect(toast.error).toHaveBeenCalled()
      })
    })

    it('shows an error toast and no confirmation when the data sinks cannot be loaded', async () => {
      vi.mocked(getDatasetResources).mockRejectedValueOnce(new Error('network'))
      renderComponent()
      await openMenu()
      await userEvent.click(screen.getByTestId('pipelineCardMenuDelete-pipeline-1'))

      await waitFor(() => expect(toast.error).toHaveBeenCalled())
      expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
    })
  })
})
