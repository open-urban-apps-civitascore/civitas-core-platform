import { render, screen } from '@testing-library/react'
import { toast } from 'sonner'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { PIPELINE_NODE_TYPES } from '@/app/(main)/datasets/[datasetId]/data-flow/pipeline-editor/_types/pipeline'
import { useGetDataSinks } from '@/app/services/api/datasets/datasinks/clientRequests'
import { useGetDatasources } from '@/app/services/api/datasources/clientRequests'
import { useGetPipelines } from '@/app/services/api/pipelines/clientRequests'
import { PipelineBasicInfo } from '@/types/datasets'

import { PipelineList } from './PipelineList'

vi.mock('./PipelineCard', () => ({
  PipelineCard: ({
    pipeline,
    badges,
    canDelete,
  }: {
    pipeline: PipelineBasicInfo
    badges: string[]
    canDelete: boolean
  }) => (
    <div data-testid={`pipelineCard-${pipeline.id}`} data-can-delete={canDelete}>
      {pipeline.name}
      {badges.map(label => (
        <span key={label}>{label}</span>
      ))}
    </div>
  ),
}))

vi.mock('@/components/guarded-link/GuardedLink', () => ({
  GuardedLink: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-permissions', () => ({
  usePermissions: () => ({ hasPermission: () => false }),
}))

vi.mock('@/app/services/api/pipelines/clientRequests', () => ({
  useGetPipelines: vi.fn().mockReturnValue({ data: { data: [] } }),
}))

vi.mock('@/app/services/api/datasources/clientRequests', () => ({
  useGetDatasources: vi.fn().mockReturnValue({ data: { data: [] } }),
}))

vi.mock('@/app/services/api/datasets/datasinks/clientRequests', () => ({
  useGetDataSinks: vi.fn().mockReturnValue({ data: { data: [] } }),
}))

vi.mock('sonner', () => ({
  toast: { error: vi.fn() },
}))

const makePipeline = (overrides: Partial<PipelineBasicInfo> = {}): PipelineBasicInfo => ({
  id: 'p1',
  name: 'Pipeline One',
  ...overrides,
})

const defaultProps = {
  datasetId: 'dataset-123',
  pipelines: [] as PipelineBasicInfo[],
  canCreatePipeline: true,
  canDeletePipeline: true,
}

const renderComponent = (props: Partial<typeof defaultProps> = {}) =>
  render(<PipelineList {...defaultProps} {...props} />)

describe('PipelineList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useGetPipelines).mockReturnValue({ data: { data: [] } } as never)
    vi.mocked(useGetDatasources).mockReturnValue({ data: { data: [] } } as never)
    vi.mocked(useGetDataSinks).mockReturnValue({ data: { data: [] } } as never)
  })

  describe('Empty state', () => {
    it('shows the empty message when there are no pipelines', () => {
      renderComponent({ pipelines: [] })
      expect(screen.getByText('empty')).toBeInTheDocument()
    })

    it('does not render any PipelineCard when the list is empty', () => {
      renderComponent({ pipelines: [] })
      expect(screen.queryByTestId(/^pipelineCard-/)).not.toBeInTheDocument()
    })
  })

  describe('Populated list', () => {
    it('renders one PipelineCard per pipeline', () => {
      renderComponent({ pipelines: [makePipeline({ id: 'p1' }), makePipeline({ id: 'p2', name: 'Pipeline Two' })] })
      expect(screen.getByTestId('pipelineCard-p1')).toBeInTheDocument()
      expect(screen.getByTestId('pipelineCard-p2')).toBeInTheDocument()
    })

    it('forwards canDeletePipeline to each PipelineCard as canDelete', () => {
      renderComponent({ pipelines: [makePipeline({ id: 'p1' })], canDeletePipeline: false })
      expect(screen.getByTestId('pipelineCard-p1')).toHaveAttribute('data-can-delete', 'false')
    })

    it('does not show the empty message when there are pipelines', () => {
      renderComponent({ pipelines: [makePipeline()] })
      expect(screen.queryByText('empty')).not.toBeInTheDocument()
    })
  })

  describe('Add pipeline button', () => {
    it('links to the pipeline editor when canCreatePipeline is true', () => {
      renderComponent({ canCreatePipeline: true })
      const link = screen.getByText('addButton').closest('a')
      expect(link).toHaveAttribute('href', '/datasets/dataset-123/data-flow/pipeline-editor')
    })

    it('hides the add button when canCreatePipeline is false', () => {
      renderComponent({ canCreatePipeline: false })
      expect(screen.queryByText('addButton')).not.toBeInTheDocument()
    })
  })

  describe('Connector badges', () => {
    it('derives connector-type badges from the matching pipeline DTO and datasource connectors', () => {
      vi.mocked(useGetPipelines).mockReturnValue({
        data: {
          data: [
            {
              id: 'p1',
              styles: {
                nodes: [
                  { id: 'n1', type: PIPELINE_NODE_TYPES.DataSource, data: { entityId: 'ds-1' } },
                  { id: 'n2', type: PIPELINE_NODE_TYPES.Frost, data: {} },
                ],
              },
            },
          ],
        },
      } as never)
      vi.mocked(useGetDatasources).mockReturnValue({
        data: { data: [{ id: 'ds-1', connectorType: 'MQTT' }] },
      } as never)

      renderComponent({ pipelines: [makePipeline({ id: 'p1' })] })
      expect(screen.getByText('MQTT')).toBeInTheDocument()
      expect(screen.getByText('FROST')).toBeInTheDocument()
    })

    it('renders no badges for a pipeline with no matching DTO', () => {
      renderComponent({ pipelines: [makePipeline({ id: 'p1' })] })
      expect(screen.queryByText('MQTT')).not.toBeInTheDocument()
      expect(screen.queryByText('FROST')).not.toBeInTheDocument()
    })

    describe('for a pipeline without a stored graph (an installed package)', () => {
      const installedPipeline = { id: 'p1', styles: null, dataSourceIds: ['ds-1'], dataSinkIds: ['sink-1'] }

      beforeEach(() => {
        vi.mocked(useGetPipelines).mockReturnValue({ data: { data: [installedPipeline] } } as never)
        vi.mocked(useGetDatasources).mockReturnValue({
          data: { data: [{ id: 'ds-1', connectorType: 'SQL' }] },
        } as never)
        vi.mocked(useGetDataSinks).mockReturnValue({
          data: { data: [{ id: 'sink-1', dataSinkType: 'POSTGIS' }] },
        } as never)
      })

      it('derives the badges from the data source and the data sink the pipeline is linked to', () => {
        renderComponent({ pipelines: [makePipeline({ id: 'p1' })] })
        expect(screen.getByText('SQL')).toBeInTheDocument()
        expect(screen.getByText('POSTGIS')).toBeInTheDocument()
      })

      it('asks for the data sinks of the dataset', () => {
        renderComponent({ pipelines: [makePipeline({ id: 'p1' })] })
        expect(useGetDataSinks).toHaveBeenLastCalledWith('dataset-123', { isEnabled: true })
      })

      it('shows no badge for a data sink of another pipeline', () => {
        vi.mocked(useGetDataSinks).mockReturnValue({
          data: { data: [{ id: 'sink-of-another-pipeline', dataSinkType: 'FROST' }] },
        } as never)

        renderComponent({ pipelines: [makePipeline({ id: 'p1' })] })
        expect(screen.queryByText('FROST')).not.toBeInTheDocument()
      })
    })

    it('does not ask for the data sinks when each pipeline has a stored graph', () => {
      vi.mocked(useGetPipelines).mockReturnValue({
        data: { data: [{ id: 'p1', styles: { nodes: [{ id: 'n1', type: PIPELINE_NODE_TYPES.Start, data: {} }] } }] },
      } as never)

      renderComponent({ pipelines: [makePipeline({ id: 'p1' })] })
      expect(useGetDataSinks).toHaveBeenLastCalledWith('dataset-123', { isEnabled: false })
    })
  })

  describe('Error toast', () => {
    it('shows an error toast once for a pipeline in ERROR state', () => {
      renderComponent({
        pipelines: [makePipeline({ id: 'p1', runtimeStatus: { state: 'ERROR', message: 'Boom' } })],
      })
      expect(toast.error).toHaveBeenCalledTimes(1)
    })

    it('does not show a toast for a pipeline in OK state', () => {
      renderComponent({ pipelines: [makePipeline({ id: 'p1', runtimeStatus: { state: 'OK' } })] })
      expect(toast.error).not.toHaveBeenCalled()
    })

    it('does not repeat the toast for the same error on re-render', () => {
      const { rerender } = renderComponent({
        pipelines: [makePipeline({ id: 'p1', runtimeStatus: { state: 'ERROR', lastEventId: 'evt-1' } })],
      })
      rerender(
        <PipelineList
          {...defaultProps}
          pipelines={[makePipeline({ id: 'p1', runtimeStatus: { state: 'ERROR', lastEventId: 'evt-1' } })]}
        />,
      )
      expect(toast.error).toHaveBeenCalledTimes(1)
    })

    it('shows a new toast when a different error occurs on the same pipeline', () => {
      const { rerender } = renderComponent({
        pipelines: [makePipeline({ id: 'p1', runtimeStatus: { state: 'ERROR', lastEventId: 'evt-1' } })],
      })
      rerender(
        <PipelineList
          {...defaultProps}
          pipelines={[makePipeline({ id: 'p1', runtimeStatus: { state: 'ERROR', lastEventId: 'evt-2' } })]}
        />,
      )
      expect(toast.error).toHaveBeenCalledTimes(2)
    })
  })
})
