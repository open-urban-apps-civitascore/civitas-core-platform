'use client'

/** Tells the canvas and the inspector which data sinks must not be deleted or edited. */

import { useParams } from 'next/navigation'
import { useCallback, useMemo } from 'react'

import { useGetDataset } from '@/app/services/api/datasets/clientRequests'
import { useGetDataSinks } from '@/app/services/api/datasets/datasinks/clientRequests'
import { DATASINK_TYPES } from '@/types/datasinks'
import { API_STANDARDS } from '@/types/namedApis'
import { hasApiType } from '@/utils/namedApis'

import { isFrostNodeData, isGeoPersistenceNodeData } from '../_types/nodes'
import type { PipelineNode } from '../_types/pipeline'

export interface DataSinkLocks {
  provisioned: boolean
  inUseByLayer: boolean
}

const UNLOCKED: DataSinkLocks = { provisioned: false, inUseByLayer: false }

/** Why a sink node must not be deleted. */
export type SinkLockReason = 'provisioned' | 'inUseByLayer' | 'inUseBySta'

export interface DataSinkLocksLookup {
  /** Locks of the sink with this backend id; everything false when the id is unknown. */
  getSinkLocks: (entityId: string | undefined) => DataSinkLocks
  getSinkLockReason: (node: PipelineNode) => SinkLockReason | null
  /** Lock reason of the first locked node among the selected ones. */
  getSelectionLockReason: (nodes: PipelineNode[]) => SinkLockReason | null
  /** True when deleting these nodes leaves the STA API of the dataset without a FROST sink. */
  wouldDeleteLastStaFrostSink: (nodeIdsToDelete: string[]) => boolean
  /** True while the sinks are still being loaded, so no lock is known yet. */
  isLoading: boolean
}

export const useDataSinkLocks = (pipelineId?: string, nodes: PipelineNode[] = []): DataSinkLocksLookup => {
  const { datasetId } = useParams<{ datasetId: string }>()
  const { data, isLoading } = useGetDataSinks(datasetId, { isEnabled: !!datasetId })
  const { data: datasetData } = useGetDataset({ id: datasetId, isEnabled: !!datasetId })

  const locksById = useMemo(() => {
    const map = new Map<string, DataSinkLocks>()
    for (const sink of data?.data ?? []) {
      map.set(sink.id, { provisioned: sink.provisioned ?? false, inUseByLayer: sink.inUseByLayer ?? false })
    }
    return map
  }, [data?.data])

  // FROST sinks of the active pipeline are counted from its graph nodes.
  const isStaWithoutOtherFrostSink = useMemo(() => {
    const hasSta = hasApiType(datasetData?.data?.namedApis ?? [], API_STANDARDS.STA)
    const hasFrostSinkInOtherPipeline = (data?.data ?? []).some(
      sink => sink.dataSinkType === DATASINK_TYPES.FROST && sink.pipelineId && sink.pipelineId !== pipelineId,
    )
    return hasSta && !hasFrostSinkInOtherPipeline
  }, [datasetData?.data?.namedApis, data?.data, pipelineId])

  const frostNodeIds = useMemo(() => nodes.flatMap(node => (isFrostNodeData(node.data) ? [node.id] : [])), [nodes])
  const frostNodeCount = frostNodeIds.length

  const getSinkLocks = useCallback(
    (entityId: string | undefined): DataSinkLocks => (entityId ? (locksById.get(entityId) ?? UNLOCKED) : UNLOCKED),
    [locksById],
  )

  // `provisioned` wins because it is the stricter lock.
  const getSinkLockReason = useCallback(
    (node: PipelineNode): SinkLockReason | null => {
      if (!isFrostNodeData(node.data) && !isGeoPersistenceNodeData(node.data)) return null
      const locks = getSinkLocks(node.data.entityId)
      if (locks.provisioned) return 'provisioned'
      if (locks.inUseByLayer) return 'inUseByLayer'
      if (isFrostNodeData(node.data) && isStaWithoutOtherFrostSink && frostNodeCount === 1) return 'inUseBySta'
      return null
    },
    [getSinkLocks, isStaWithoutOtherFrostSink, frostNodeCount],
  )

  const getSelectionLockReason = useCallback(
    (nodes: PipelineNode[]): SinkLockReason | null =>
      nodes
        .filter(node => node.selected)
        .map(getSinkLockReason)
        .find(reason => reason !== null) ?? null,
    [getSinkLockReason],
  )

  // The per-node lock misses a delete of several FROST nodes of the same pipeline at once.
  const wouldDeleteLastStaFrostSink = useCallback(
    (nodeIdsToDelete: string[]) => {
      const isEveryFrostNodeDeleted = frostNodeIds.every(id => nodeIdsToDelete.includes(id))
      return isStaWithoutOtherFrostSink && frostNodeCount > 0 && isEveryFrostNodeDeleted
    },
    [isStaWithoutOtherFrostSink, frostNodeIds, frostNodeCount],
  )

  return { getSinkLocks, getSinkLockReason, getSelectionLockReason, wouldDeleteLastStaFrostSink, isLoading }
}
