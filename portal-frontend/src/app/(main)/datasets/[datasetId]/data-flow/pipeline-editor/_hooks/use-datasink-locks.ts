'use client'

/** Tells the inspector which data sinks must not be edited. */

import { useParams } from 'next/navigation'
import { useCallback, useMemo } from 'react'

import { useGetDataSinks } from '@/app/services/api/datasets/datasinks/clientRequests'

import { isFrostNodeData, isGeoPersistenceNodeData } from '../_types/nodes'
import type { PipelineNode } from '../_types/pipeline'

export interface DataSinkLocks {
  provisioned: boolean
  inUseByLayer: boolean
}

const UNLOCKED: DataSinkLocks = { provisioned: false, inUseByLayer: false }

/** Why a sink node must not be edited. */
export type SinkLockReason = 'provisioned' | 'inUseByLayer'

export interface DataSinkLocksLookup {
  /** Locks of the sink with this backend id; everything false when the id is unknown. */
  getSinkLocks: (entityId: string | undefined) => DataSinkLocks
  getSinkLockReason: (node: PipelineNode) => SinkLockReason | null
  /** True while the sinks are still being loaded, so no lock is known yet. */
  isLoading: boolean
}

export const useDataSinkLocks = (): DataSinkLocksLookup => {
  const { datasetId } = useParams<{ datasetId: string }>()
  const { data, isLoading } = useGetDataSinks(datasetId, { isEnabled: !!datasetId })

  const locksById = useMemo(() => {
    const map = new Map<string, DataSinkLocks>()
    for (const sink of data?.data ?? []) {
      map.set(sink.id, { provisioned: sink.provisioned ?? false, inUseByLayer: sink.inUseByLayer ?? false })
    }
    return map
  }, [data?.data])

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
      return null
    },
    [getSinkLocks],
  )

  return { getSinkLocks, getSinkLockReason, isLoading }
}
