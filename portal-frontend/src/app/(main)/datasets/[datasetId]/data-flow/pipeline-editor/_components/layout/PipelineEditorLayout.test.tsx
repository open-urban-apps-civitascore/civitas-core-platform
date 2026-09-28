import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { NextIntlClientProvider } from 'next-intl'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetDataset } from '@/app/services/api/datasets/clientRequests'
import {
  useCreateDataSink,
  useDeleteDataSink,
  useUpdateDataSink,
} from '@/app/services/api/datasets/datasinks/clientRequests'
import { useGetDatasources } from '@/app/services/api/datasources/clientRequests'
import { useGetDatastructureVersion } from '@/app/services/api/datastructures/versions/clientRequests'
import { useCreateMapping, useUpdateMapping } from '@/app/services/api/mappings/clientRequests'
import {
  useCreatePipeline,
  useDeletePipeline,
  useGetPipelines,
  useUpdatePipeline,
} from '@/app/services/api/pipelines/clientRequests'
import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import messages from '@/messages/de.json'
import {
  FROST_SINK_URN,
  frostNode,
  geoPersistenceNode,
  POSTGIS_SINK_URN,
  savablePipeline,
  savedAs,
} from '@/test-support/pipelineFixtures'
import { PERMISSION_NAMES } from '@/types/currentUser'

import type { CorePipelineNode, PipelinePayload } from '../../_types/pipeline'
import { PipelineEditorWrapper } from './PipelineEditorWrapper'

vi.mock('next/navigation', () => ({
  useParams: () => ({ datasetId: 'dataset-1' }),
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/datasets/dataset-1/data-flow/pipeline-editor',
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('@/app/services/api/users/clientRequests', () => ({ useGetCurrentUser: vi.fn() }))
vi.mock('@/app/services/api/datasets/clientRequests', () => ({ useGetDataset: vi.fn() }))
vi.mock('@/app/services/api/datasources/clientRequests', () => ({ useGetDatasources: vi.fn() }))
vi.mock('@/app/services/api/datastructures/versions/clientRequests', () => ({
  useGetDatastructureVersion: vi.fn(),
}))
vi.mock('@/app/services/api/pipelines/clientRequests', () => ({
  useGetPipelines: vi.fn(),
  useCreatePipeline: vi.fn(),
  useUpdatePipeline: vi.fn(),
  useDeletePipeline: vi.fn(),
}))
vi.mock('@/app/services/api/datasets/datasinks/clientRequests', () => ({
  useCreateDataSink: vi.fn(),
  useUpdateDataSink: vi.fn(),
  useDeleteDataSink: vi.fn(),
}))
vi.mock('@/app/services/api/mappings/clientRequests', () => ({
  useCreateMapping: vi.fn(),
  useUpdateMapping: vi.fn(),
}))

const mutation = (mutateAsync = vi.fn().mockResolvedValue({ data: {} })) =>
  ({ mutate: vi.fn(), mutateAsync, isPending: false }) as never

let updatePipeline: ReturnType<typeof vi.fn>

beforeEach(() => {
  vi.clearAllMocks()
  updatePipeline = vi.fn().mockResolvedValue({ data: {} })

  vi.mocked(useGetCurrentUser).mockReturnValue({
    data: {
      username: 'test-user-1',
      email: 'test.user1@test.com',
      title: 'MR' as const,
      firstName: 'Test',
      lastName: 'User',
      assignments: [
        {
          scopeType: 'TENANT',
          scopeId: null,
          permissions: [
            PERMISSION_NAMES.DATASET_READ,
            PERMISSION_NAMES.DATASET_CREATE,
            PERMISSION_NAMES.DATASET_UPDATE,
            PERMISSION_NAMES.DATASOURCE_READ,
            PERMISSION_NAMES.DATASTRUCTURE_READ,
          ],
        },
      ],
    },
  } as ReturnType<typeof useGetCurrentUser>)

  vi.mocked(useGetDataset).mockReturnValue({
    data: { data: { id: 'dataset-1', dataSetStatus: 'DRAFT', datapool: null, provisioned: false } },
    isPending: false,
    isLoading: false,
  } as unknown as ReturnType<typeof useGetDataset>)

  vi.mocked(useGetDatasources).mockReturnValue({
    data: { data: [] },
    isLoading: false,
    isError: false,
  } as unknown as ReturnType<typeof useGetDatasources>)

  vi.mocked(useGetDatastructureVersion).mockReturnValue({ data: undefined } as unknown as ReturnType<
    typeof useGetDatastructureVersion
  >)

  vi.mocked(useUpdatePipeline).mockReturnValue(mutation(updatePipeline))
  vi.mocked(useCreatePipeline).mockReturnValue(mutation())
  vi.mocked(useDeletePipeline).mockReturnValue(mutation())
  vi.mocked(useCreateDataSink).mockReturnValue(mutation())
  vi.mocked(useUpdateDataSink).mockReturnValue(mutation())
  vi.mocked(useDeleteDataSink).mockReturnValue(mutation())
  vi.mocked(useCreateMapping).mockReturnValue(mutation())
  vi.mocked(useUpdateMapping).mockReturnValue(mutation())
})

const sinks = [
  ['PostGIS', geoPersistenceNode(), POSTGIS_SINK_URN],
  ['FROST', frostNode(), FROST_SINK_URN],
] as const

describe('saving from the Pipeline editor', () => {
  it.each(sinks)(
    'sends the %s pipeline with the cron expression edited in the inspector',
    async (_label, sink, sinkUrn) => {
      const saved = savablePipeline(sink)
      // Pre-selected: the inspector shows the selected node, and React Flow needs pointer events to select.
      const loaded = { ...saved, nodes: saved.nodes.map(node => ({ ...node, selected: node.id === 'cron-1' })) }
      vi.mocked(useGetPipelines).mockReturnValue({
        data: { data: [savedAs(loaded)] },
        isLoading: false,
      } as unknown as ReturnType<typeof useGetPipelines>)

      const user = userEvent.setup()
      render(
        <NextIntlClientProvider locale="de" messages={messages}>
          <PipelineEditorWrapper />
        </NextIntlClientProvider>,
      )

      const cron = await screen.findByDisplayValue('0 0 * * * ?')
      await user.clear(cron)
      await user.type(cron, '0 30 * * * ?')
      await user.click(screen.getByRole('button', { name: 'Alle speichern' }))

      await waitFor(() => expect(updatePipeline).toHaveBeenCalledTimes(1))
      const { pipelineId, data } = updatePipeline.mock.calls[0][0] as { pipelineId: string; data: PipelinePayload }
      const nodeById = (id: string): CorePipelineNode | undefined => data.model.nodes.find(node => node.id === id)

      expect(pipelineId).toBe('backend-pipeline-1')
      expect(data.model.nodes.map(node => node.kind)).toEqual(['start', 'cron', 'source', 'mapping', 'sink', 'end'])
      expect(nodeById('cron-1')).toMatchObject({ cronExpression: '0 30 * * * ?' })
      expect(nodeById(sink.id)).toMatchObject({ sinkRef: sinkUrn })
    },
  )
})
