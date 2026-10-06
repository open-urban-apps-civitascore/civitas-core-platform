import { redirect } from 'next/navigation'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getDataSinks } from '@/app/services/api/datasets/datasinks/serverRequests'
import { getDataset } from '@/app/services/api/datasets/serverRequests'
import { Dataset, DATASET_STATUS_TYPES, DatasetStatusTypes } from '@/types/datasets'
import { DataSink, DATASINK_TYPES } from '@/types/datasinks'
import { NamedApi } from '@/types/namedApis'

import ApisPage from './page'

const REDIRECT = 'NEXT_REDIRECT'

vi.mock('next/navigation', () => ({
  redirect: vi.fn(() => {
    throw new Error(REDIRECT)
  }),
}))

vi.mock('@/app/services/api/datasets/serverRequests', () => ({
  getDataset: vi.fn(),
}))

vi.mock('@/app/services/api/datasets/datasinks/serverRequests', () => ({
  getDataSinks: vi.fn(),
}))

vi.mock('./components/ApiConfigPage', () => ({
  ApiConfigPage: () => null,
}))

const DATASET_ID = 'test-id'

const makeDataset = (overrides: Partial<Dataset> = {}): Dataset => ({
  id: DATASET_ID,
  name: 'Test Dataset 1',
  description: 'A test dataset',
  dataSetStatus: DATASET_STATUS_TYPES.DRAFT,
  openDataAccess: false,
  pipelines: [],
  datapool: null,
  createdAt: '2024-01-01',
  modifiedAt: '2024-01-01',
  createdBy: { id: 'user-1', name: 'Test User 1' },
  ...overrides,
})

const mockDataset = (overrides: Partial<Dataset> = {}) =>
  vi.mocked(getDataset).mockResolvedValue({ data: makeDataset(overrides) } as Awaited<ReturnType<typeof getDataset>>)

const frostSink = { id: 'sink-1', dataSinkType: DATASINK_TYPES.FROST, pipelineId: 'pipeline-1' } as DataSink
const postgisSink = { id: 'sink-2', dataSinkType: DATASINK_TYPES.POSTGIS, pipelineId: 'pipeline-1' } as DataSink

const mockDataSinks = (dataSinks: DataSink[]) =>
  vi.mocked(getDataSinks).mockResolvedValue({ data: dataSinks } as Awaited<ReturnType<typeof getDataSinks>>)

const owsApi: NamedApi = { id: 'api-1', name: 'My OWS API', slug: 'my-ows', standard: 'OWS' }
const staApi: NamedApi = { id: 'api-2', name: 'My STA API', slug: 'my-sta', standard: 'STA' }

const openPage = (type = 'sensorthings') =>
  ApisPage({
    params: Promise.resolve({ datasetId: DATASET_ID }),
    searchParams: Promise.resolve({ type }),
  })

describe('ApisPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockDataset()
    mockDataSinks([frostSink, postgisSink])
  })

  describe('dataset status guard', () => {
    it.each([DATASET_STATUS_TYPES.READY, DATASET_STATUS_TYPES.AVAILABLE])(
      'redirects to the dataset when it is %s',
      async (dataSetStatus: DatasetStatusTypes) => {
        mockDataset({ dataSetStatus })

        await expect(openPage()).rejects.toThrow(REDIRECT)
        expect(redirect).toHaveBeenCalledWith(`/datasets/${DATASET_ID}`)
      },
    )

    it('renders the create form on a DRAFT dataset', async () => {
      await expect(openPage()).resolves.toBeTruthy()
      expect(redirect).not.toHaveBeenCalled()
    })
  })

  describe('existing guards stay intact', () => {
    it('redirects without fetching the dataset when the api type is unknown', async () => {
      await expect(openPage('nonsense')).rejects.toThrow(REDIRECT)
      expect(redirect).toHaveBeenCalledWith(`/datasets/${DATASET_ID}`)
      expect(getDataset).not.toHaveBeenCalled()
    })

    it('redirects when an OWS api already exists on a DRAFT dataset', async () => {
      mockDataset({ namedApis: [owsApi] })

      await expect(openPage('ows')).rejects.toThrow(REDIRECT)
      expect(redirect).toHaveBeenCalledWith(`/datasets/${DATASET_ID}`)
    })

    it('redirects when a SensorThings api already exists on a DRAFT dataset', async () => {
      mockDataset({ namedApis: [staApi] })

      await expect(openPage('sensorthings')).rejects.toThrow(REDIRECT)
      expect(redirect).toHaveBeenCalledWith(`/datasets/${DATASET_ID}`)
    })

    it('renders the OWS create form when no OWS api exists yet', async () => {
      await expect(openPage('ows')).resolves.toBeTruthy()
      expect(redirect).not.toHaveBeenCalled()
    })
  })

  describe('data sink guard', () => {
    it('redirects to the dataset when no FROST sink of a pipeline exists for a SensorThings api', async () => {
      mockDataSinks([postgisSink, { ...frostSink, pipelineId: undefined } as unknown as DataSink])

      await expect(openPage('sensorthings')).rejects.toThrow(REDIRECT)
      expect(redirect).toHaveBeenCalledWith(`/datasets/${DATASET_ID}`)
    })

    it('redirects to the dataset when no POSTGIS sink exists for an OWS api', async () => {
      mockDataSinks([frostSink])

      await expect(openPage('ows')).rejects.toThrow(REDIRECT)
      expect(redirect).toHaveBeenCalledWith(`/datasets/${DATASET_ID}`)
    })
  })
})
