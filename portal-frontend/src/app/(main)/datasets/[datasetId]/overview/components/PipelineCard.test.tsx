import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { toast } from 'sonner'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { mockApiError } from '@/__mocks__/errors/apiError.mock'
import { PipelineBasicInfo } from '@/types/datasets'
import type { DatasetRemovalState } from '@/types/dataSinkRemoval'

import { PipelineCard } from './PipelineCard'

const mockRequestNavigation = vi.fn()
const mockRefresh = vi.fn()
const mockDeletePipelineMutateAsync = vi.fn().mockResolvedValue(undefined)
let mockHasUnsavedChanges = false
let mockRemovalState: DatasetRemovalState | null = null

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

vi.mock('@/hooks/use-data-sink-removal-state', () => ({
  useDataSinkRemovalState: () => mockRemovalState,
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

const removalNotConfirmed = (removal: Record<string, unknown>) =>
  mockApiError(409, 'set confirmDataLoss=true to proceed', 'urn:civitas:error:REMOVAL_NOT_CONFIRMED', { removal })

const LAYER_REMOVAL_ERROR = removalNotConfirmed({
  dataSinks: [{ id: 'sink-1', dataSinkType: 'POSTGIS', provisioned: true }],
  layers: [{ id: 'layer-1', layerName: 'trees', title: 'Trees' }],
  apis: [{ id: 'api-1', slug: 'ows', name: 'Trees API', standard: 'OWS' }],
})

const EMPTY_STATE: DatasetRemovalState = { dataSinks: [], layers: [], apis: [] }

const MAP_STATE: DatasetRemovalState = {
  dataSinks: [{ id: 'sink-1', dataSinkType: 'POSTGIS', provisioned: true, pipelineIds: ['pipeline-1'] }],
  layers: [{ id: 'layer-1', dataSinkId: 'sink-1', namedApiId: 'api-1', layerName: 'trees', title: 'Trees' }],
  apis: [{ id: 'api-1', slug: 'ows', name: 'Trees API', standard: 'OWS' }],
}

const SENSOR_STATE: DatasetRemovalState = {
  dataSinks: [{ id: 'sink-1', dataSinkType: 'FROST', provisioned: false, pipelineIds: ['pipeline-1'] }],
  layers: [],
  apis: [{ id: 'api-1', slug: 'sta', name: 'Sensor API', standard: 'STA' }],
}

const renderComponent = (props: Partial<typeof defaultProps> = {}) =>
  render(<PipelineCard {...defaultProps} {...props} />)

describe('PipelineCard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockHasUnsavedChanges = false
    mockRemovalState = EMPTY_STATE
    mockDeletePipelineMutateAsync.mockResolvedValue(undefined)
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

    const confirm = () => fireEvent.click(screen.getByTestId('confirmButton'))

    it('asks the plain delete question when the delete takes nothing along', async () => {
      renderComponent()
      await startDelete()
      expect(screen.queryByTestId('removalConfirmLayers')).not.toBeInTheDocument()
      expect(screen.queryByTestId('removalConfirmApis')).not.toBeInTheDocument()
      expect(screen.queryByTestId('removalConfirmStoredData')).not.toBeInTheDocument()
      expect(mockDeletePipelineMutateAsync).not.toHaveBeenCalled()
    })

    it('closes the modal without deleting when cancel is clicked', async () => {
      mockRemovalState = MAP_STATE
      renderComponent()
      await startDelete()
      fireEvent.click(screen.getByTestId('discardButton'))
      await waitFor(() => expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument())
      expect(mockDeletePipelineMutateAsync).not.toHaveBeenCalled()
    })

    it('deletes once without confirmDataLoss when the delete takes nothing along', async () => {
      renderComponent()
      await startDelete()
      confirm()

      await waitFor(() => expect(toast.success).toHaveBeenCalled())
      expect(mockDeletePipelineMutateAsync).toHaveBeenCalledOnce()
      expect(mockDeletePipelineMutateAsync).toHaveBeenCalledWith({
        pipelineId: 'pipeline-1',
        isDataLossConfirmed: false,
      })
      expect(mockRefresh).toHaveBeenCalled()
    })

    it('lists the Layers, APIs and stored data the delete takes along in its one question', async () => {
      mockRemovalState = MAP_STATE
      renderComponent()
      await startDelete()

      expect(screen.getByTestId('removalConfirmLayers')).toHaveTextContent('Trees')
      expect(screen.getByTestId('removalConfirmApis')).toHaveTextContent('Trees API')
      expect(screen.getByTestId('removalConfirmStoredData')).toBeInTheDocument()
      expect(mockDeletePipelineMutateAsync).not.toHaveBeenCalled()
    })

    it('deletes once with confirmDataLoss when the agreed removal deletes stored data', async () => {
      mockRemovalState = MAP_STATE
      renderComponent()
      await startDelete()
      confirm()

      await waitFor(() => expect(toast.success).toHaveBeenCalled())
      expect(mockDeletePipelineMutateAsync).toHaveBeenCalledOnce()
      expect(mockDeletePipelineMutateAsync).toHaveBeenCalledWith({
        pipelineId: 'pipeline-1',
        isDataLossConfirmed: true,
      })
    })

    it('deletes once without confirmDataLoss when the agreed removal keeps all stored data', async () => {
      mockRemovalState = SENSOR_STATE
      renderComponent()
      await startDelete()
      expect(screen.getByTestId('removalConfirmApis')).toHaveTextContent('Sensor API')
      expect(screen.queryByTestId('removalConfirmStoredData')).not.toBeInTheDocument()
      confirm()

      await waitFor(() => expect(toast.success).toHaveBeenCalled())
      expect(mockDeletePipelineMutateAsync).toHaveBeenCalledOnce()
      expect(mockDeletePipelineMutateAsync).toHaveBeenCalledWith({
        pipelineId: 'pipeline-1',
        isDataLossConfirmed: false,
      })
    })

    it('asks again with the list the backend names when it still refuses the delete', async () => {
      mockDeletePipelineMutateAsync.mockRejectedValueOnce(LAYER_REMOVAL_ERROR)
      renderComponent()
      await startDelete()
      confirm()

      expect(await screen.findByTestId('removalConfirmLayers')).toHaveTextContent('Trees')
      expect(screen.getByTestId('removalConfirmStoredData')).toBeInTheDocument()
      confirm()

      await waitFor(() => expect(toast.success).toHaveBeenCalled())
      expect(mockDeletePipelineMutateAsync).toHaveBeenCalledTimes(2)
      expect(mockDeletePipelineMutateAsync).toHaveBeenLastCalledWith({
        pipelineId: 'pipeline-1',
        isDataLossConfirmed: true,
      })
    })

    it('sends nothing more when the list the backend names is cancelled', async () => {
      mockDeletePipelineMutateAsync.mockRejectedValueOnce(LAYER_REMOVAL_ERROR)
      renderComponent()
      await startDelete()
      confirm()
      await screen.findByTestId('removalConfirmLayers')
      fireEvent.click(screen.getByTestId('discardButton'))

      await waitFor(() => expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument())
      expect(mockDeletePipelineMutateAsync).toHaveBeenCalledOnce()
      expect(toast.success).not.toHaveBeenCalled()
      expect(toast.error).not.toHaveBeenCalled()
    })

    // Otherwise the backend removes Layers and APIs of an unprovisioned Data sink that the dialog did not list.
    it('disables Delete until the Data sinks, Layers and APIs of the Dataset are loaded', async () => {
      mockRemovalState = null
      renderComponent()
      await openMenu()

      expect(screen.getByTestId('pipelineCardMenuDelete-pipeline-1')).toHaveAttribute('data-disabled')
      expect(mockDeletePipelineMutateAsync).not.toHaveBeenCalled()
    })

    it('shows an error toast when deletePipeline rejects', async () => {
      mockDeletePipelineMutateAsync.mockRejectedValueOnce(new Error('network'))
      renderComponent()
      await startDelete()
      confirm()

      await waitFor(() => {
        expect(toast.error).toHaveBeenCalled()
      })
      expect(mockDeletePipelineMutateAsync).toHaveBeenCalledOnce()
    })
  })
})
