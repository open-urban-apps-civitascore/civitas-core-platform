import { renderHook } from '@testing-library/react'

import { useGetDataset } from '@/app/services/api/datasets/clientRequests'
import { useGetDataSinks } from '@/app/services/api/datasets/datasinks/clientRequests'
import { type DataSink, DATASINK_TYPES } from '@/types/datasinks'
import { API_STANDARDS, type NamedApi } from '@/types/namedApis'

import type { FrostNodeData, GeoPersistenceNodeData } from '../_types/nodes'
import type { PipelineNode } from '../_types/pipeline'
import { useDataSinkLocks } from './use-datasink-locks'

vi.mock('next/navigation', () => ({
  useParams: () => ({ datasetId: 'dataset-1' }),
}))

vi.mock('@/app/services/api/datasets/clientRequests', () => ({
  useGetDataset: vi.fn(),
}))

vi.mock('@/app/services/api/datasets/datasinks/clientRequests', () => ({
  useGetDataSinks: vi.fn(),
}))

const mockDataSinks = (sinks: Partial<DataSink>[]) => {
  vi.mocked(useGetDataSinks).mockReturnValue({ data: { data: sinks } } as unknown as ReturnType<typeof useGetDataSinks>)
}

const mockNamedApis = (namedApis: NamedApi[]) => {
  vi.mocked(useGetDataset).mockReturnValue({ data: { data: { namedApis } } } as unknown as ReturnType<
    typeof useGetDataset
  >)
}

const STA_API: NamedApi = { name: 'STA', slug: 'sta', standard: API_STANDARDS.STA }

const makeFrostNode = (id: string, entityId?: string): PipelineNode =>
  ({
    id,
    type: 'frost',
    position: { x: 0, y: 0 },
    data: { label: 'FROST', configured: true, entityType: 'frost', entityId } as FrostNodeData,
  }) as PipelineNode

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

const select = (node: PipelineNode): PipelineNode => ({ ...node, selected: true })

describe('useDataSinkLocks', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockNamedApis([])
  })

  it('reads the locks of a saved sink', () => {
    mockDataSinks([{ id: 'sink-1', provisioned: true, inUseByLayer: true }])

    const { result } = renderHook(() => useDataSinkLocks())

    expect(result.current.getSinkLocks('sink-1')).toEqual({ provisioned: true, inUseByLayer: true })
  })

  it('treats a missing sink and a missing flag as unlocked', () => {
    mockDataSinks([{ id: 'sink-1' }])
    const unlocked = { provisioned: false, inUseByLayer: false }

    const { result } = renderHook(() => useDataSinkLocks())

    expect(result.current.getSinkLocks('sink-1')).toEqual(unlocked)
    expect(result.current.getSinkLocks('unknown')).toEqual(unlocked)
    expect(result.current.getSinkLocks(undefined)).toEqual(unlocked)
  })

  it('locks a provisioned sink node', () => {
    mockDataSinks([{ id: 'sink-1', provisioned: true }])

    const { result } = renderHook(() => useDataSinkLocks())

    expect(result.current.getSinkLockReason(makeSinkNode('sink-1'))).toBe('provisioned')
  })

  it('locks a layer-referenced sink node', () => {
    mockDataSinks([{ id: 'sink-1', inUseByLayer: true }])

    const { result } = renderHook(() => useDataSinkLocks())

    expect(result.current.getSinkLockReason(makeSinkNode('sink-1'))).toBe('inUseByLayer')
  })

  it('reports the stricter provisioned lock when both apply', () => {
    mockDataSinks([{ id: 'sink-1', provisioned: true, inUseByLayer: true }])

    const { result } = renderHook(() => useDataSinkLocks())

    expect(result.current.getSinkLockReason(makeSinkNode('sink-1'))).toBe('provisioned')
  })

  it('leaves an unlocked sink node unlocked', () => {
    mockDataSinks([{ id: 'sink-1', provisioned: false, inUseByLayer: false }])

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

  it('reports why a selected sink node cannot be deleted', () => {
    mockDataSinks([{ id: 'sink-1', inUseByLayer: true }])

    const { result } = renderHook(() => useDataSinkLocks())

    expect(result.current.getSelectionLockReason([select(makeSinkNode('sink-1'))])).toBe('inUseByLayer')
  })

  it('ignores a locked sink node that is not selected', () => {
    mockDataSinks([{ id: 'sink-1', provisioned: true }])

    const { result } = renderHook(() => useDataSinkLocks())

    expect(result.current.getSelectionLockReason([makeSinkNode('sink-1'), select(makeSourceNode())])).toBeNull()
  })

  it('reports the locked node of a mixed selection', () => {
    mockDataSinks([{ id: 'sink-1', provisioned: true }])

    const { result } = renderHook(() => useDataSinkLocks())

    expect(result.current.getSelectionLockReason([select(makeSourceNode()), select(makeSinkNode('sink-1'))])).toBe(
      'provisioned',
    )
  })

  it('reports no lock for a deletable selection', () => {
    mockDataSinks([{ id: 'sink-1' }])

    const { result } = renderHook(() => useDataSinkLocks())

    expect(result.current.getSelectionLockReason([select(makeSinkNode('sink-1'))])).toBeNull()
  })

  describe('last FROST sink of an STA API', () => {
    const frostNode = makeFrostNode('frost-1', 'sink-1')

    beforeEach(() => {
      mockNamedApis([STA_API])
    })

    it('locks the only FROST node when no other pipeline has a FROST sink', () => {
      mockDataSinks([{ id: 'sink-1', pipelineId: 'pipeline-1', dataSinkType: DATASINK_TYPES.FROST }])

      const { result } = renderHook(() => useDataSinkLocks('pipeline-1', [frostNode]))

      expect(result.current.getSinkLockReason(frostNode)).toBe('inUseBySta')
    })

    it('ignores the FROST sink of a deleted pipeline', () => {
      mockDataSinks([{ id: 'sink-2', pipelineId: null as unknown as string, dataSinkType: DATASINK_TYPES.FROST }])

      const { result } = renderHook(() => useDataSinkLocks('pipeline-1', [frostNode]))

      expect(result.current.getSinkLockReason(frostNode)).toBe('inUseBySta')
    })

    it('leaves the FROST node unlocked when another pipeline has a FROST sink', () => {
      mockDataSinks([{ id: 'sink-2', pipelineId: 'pipeline-2', dataSinkType: DATASINK_TYPES.FROST }])

      const { result } = renderHook(() => useDataSinkLocks('pipeline-1', [frostNode]))

      expect(result.current.getSinkLockReason(frostNode)).toBeNull()
    })

    it('leaves the FROST nodes unlocked when the graph has a second one', () => {
      mockDataSinks([])
      const secondFrostNode = makeFrostNode('frost-2')

      const { result } = renderHook(() => useDataSinkLocks('pipeline-1', [frostNode, secondFrostNode]))

      expect(result.current.getSinkLockReason(frostNode)).toBeNull()
    })

    it('leaves the FROST node unlocked without an STA API', () => {
      mockNamedApis([])
      mockDataSinks([])

      const { result } = renderHook(() => useDataSinkLocks('pipeline-1', [frostNode]))

      expect(result.current.getSinkLockReason(frostNode)).toBeNull()
    })

    it('reports the provisioned lock before the STA lock', () => {
      mockDataSinks([{ id: 'sink-1', pipelineId: 'pipeline-1', dataSinkType: DATASINK_TYPES.FROST, provisioned: true }])

      const { result } = renderHook(() => useDataSinkLocks('pipeline-1', [frostNode]))

      expect(result.current.getSinkLockReason(frostNode)).toBe('provisioned')
    })

    it('detects a delete that removes every FROST node', () => {
      mockDataSinks([])
      const secondFrostNode = makeFrostNode('frost-2')

      const { result } = renderHook(() => useDataSinkLocks('pipeline-1', [frostNode, secondFrostNode]))

      expect(result.current.wouldDeleteLastStaFrostSink(['frost-1', 'frost-2'])).toBe(true)
      expect(result.current.wouldDeleteLastStaFrostSink(['frost-1'])).toBe(false)
    })
  })
})
