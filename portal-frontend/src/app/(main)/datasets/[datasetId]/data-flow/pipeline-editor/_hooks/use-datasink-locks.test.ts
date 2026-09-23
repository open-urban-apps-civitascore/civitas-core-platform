import { renderHook } from '@testing-library/react'

import { useGetDataSinks } from '@/app/services/api/datasets/datasinks/clientRequests'
import type { DataSink } from '@/types/datasinks'

import type { GeoPersistenceNodeData } from '../_types/nodes'
import type { PipelineNode } from '../_types/pipeline'
import { useDataSinkLocks } from './use-datasink-locks'

vi.mock('next/navigation', () => ({
  useParams: () => ({ datasetId: 'dataset-1' }),
}))

vi.mock('@/app/services/api/datasets/datasinks/clientRequests', () => ({
  useGetDataSinks: vi.fn(),
}))

const mockDataSinks = (sinks: Partial<DataSink>[]) => {
  vi.mocked(useGetDataSinks).mockReturnValue({ data: { data: sinks } } as unknown as ReturnType<typeof useGetDataSinks>)
}

const makeSinkNode = (entityId?: string): PipelineNode =>
  ({
    id: 'geo-1',
    type: 'geoPersistence',
    position: { x: 0, y: 0 },
    data: {
      label: 'Geo Persistence',
      configured: true,
      entityType: 'persistence',
      tableName: 'roads',
      entityId,
    } satisfies GeoPersistenceNodeData,
  }) as PipelineNode

const makeSourceNode = (): PipelineNode =>
  ({
    id: 'source-1',
    type: 'dataSource',
    position: { x: 0, y: 0 },
    data: { label: 'Source', configured: true, entityType: 'datasource', entityId: 'sink-1' },
  }) as PipelineNode

describe('useDataSinkLocks', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('reads the flags of a saved sink', () => {
    mockDataSinks([{ id: 'sink-1', provisioned: true }])

    const { result } = renderHook(() => useDataSinkLocks())

    expect(result.current.getSinkLocks('sink-1')).toEqual({ provisioned: true })
  })

  it('treats a missing sink and a missing flag as unlocked', () => {
    mockDataSinks([{ id: 'sink-1' }])

    const { result } = renderHook(() => useDataSinkLocks())

    expect(result.current.getSinkLocks('sink-1')).toEqual({ provisioned: false })
    expect(result.current.getSinkLocks('unknown')).toEqual({ provisioned: false })
    expect(result.current.getSinkLocks(undefined)).toEqual({ provisioned: false })
  })

  it('locks a provisioned sink node', () => {
    mockDataSinks([{ id: 'sink-1', provisioned: true }])

    const { result } = renderHook(() => useDataSinkLocks())

    expect(result.current.getSinkLockReason(makeSinkNode('sink-1'))).toBe('provisioned')
  })

  it('leaves an unprovisioned sink node unlocked', () => {
    mockDataSinks([{ id: 'sink-1', provisioned: false }])

    const { result } = renderHook(() => useDataSinkLocks())

    expect(result.current.getSinkLockReason(makeSinkNode('sink-1'))).toBeNull()
  })

  it('leaves an unsaved sink node unlocked', () => {
    mockDataSinks([{ id: 'sink-1', provisioned: true }])

    const { result } = renderHook(() => useDataSinkLocks())

    expect(result.current.getSinkLockReason(makeSinkNode(undefined))).toBeNull()
  })

  it('never locks a node that is not a sink', () => {
    mockDataSinks([{ id: 'sink-1', provisioned: true }])

    const { result } = renderHook(() => useDataSinkLocks())

    expect(result.current.getSinkLockReason(makeSourceNode())).toBeNull()
  })
})
