'use client'

/** Tells the canvas and the inspector which data sinks must not be deleted or edited. */

import { useParams } from 'next/navigation'
import { useCallback, useMemo } from 'react'

import { useGetDataSinks } from '@/app/services/api/datasets/datasinks/clientRequests'

import { isFrostNodeData, isGeoPersistenceNodeData } from '../_types/nodes'
import type { PipelineNode } from '../_types/pipeline'

export interface DataSinkLocks {
  provisioned: boolean
}

const UNLOCKED: DataSinkLocks = { provisioned: false }

/** Why a sink node must not be deleted. */
export type SinkLockReason = 'provisioned'

export interface DataSinkLocksLookup {
  /** Locks of the sink with this backend id; everything false when the id is unknown. */
  getSinkLocks: (entityId: string | undefined) => DataSinkLocks
  getSinkLockReason: (node: PipelineNode) => SinkLockReason | null
}

export const useDataSinkLocks = (): DataSinkLocksLookup => {
  const { datasetId } = useParams<{ datasetId: string }>()
  const { data } = useGetDataSinks(datasetId, { isEnabled: !!datasetId })

  const locksById = useMemo(() => {
    const map = new Map<string, DataSinkLocks>()
    for (const sink of data?.data ?? []) {
      map.set(sink.id, { provisioned: sink.provisioned ?? false })
    }
    return map
  }, [data?.data])

  const getSinkLocks = useCallback(
    (entityId: string | undefined): DataSinkLocks => (entityId ? (locksById.get(entityId) ?? UNLOCKED) : UNLOCKED),
    [locksById],
  )

  const getSinkLockReason = useCallback(
    (node: PipelineNode): SinkLockReason | null => {
      if (!isFrostNodeData(node.data) && !isGeoPersistenceNodeData(node.data)) return null
      return getSinkLocks(node.data.entityId).provisioned ? 'provisioned' : null
    },
    [getSinkLocks],
  )

  return { getSinkLocks, getSinkLockReason }
}
