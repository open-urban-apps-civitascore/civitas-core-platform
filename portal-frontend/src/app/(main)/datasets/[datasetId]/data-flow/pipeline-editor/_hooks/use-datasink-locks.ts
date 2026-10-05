'use client'

/** Tells the canvas and the inspector which data sinks must not be deleted or edited. */

import { useParams } from 'next/navigation'
import { useCallback, useMemo } from 'react'

import { useGetDataset } from '@/app/services/api/datasets/clientRequests'
import { useGetDataSinks } from '@/app/services/api/datasets/datasinks/clientRequests'
import { DATASINK_TYPES, DataSinkType } from '@/types/datasinks'
import { API_STANDARDS } from '@/types/namedApis'
import { hasApiType } from '@/utils/namedApis'

import { isFrostNodeData, isGeoPersistenceNodeData, type PipelineNodeData } from '../_types/nodes'
import type { PipelineNode } from '../_types/pipeline'

export interface DataSinkLocks {
  provisioned: boolean
  inUseByLayer: boolean
}

const UNLOCKED: DataSinkLocks = { provisioned: false, inUseByLayer: false }

/** The last sink of its type that an API of the dataset reads from. */
export type ApiSinkLockReason = 'inUseBySta' | 'inUseByOws'

/** Why a sink node must not be deleted. */
export type SinkLockReason = 'provisioned' | 'inUseByLayer' | ApiSinkLockReason

const getNodeIdsOfType = (nodes: PipelineNode[], isOfType: (data: PipelineNodeData) => boolean): string[] => {
  const nodeIds: string[] = []
  for (const node of nodes) {
    if (isOfType(node.data)) nodeIds.push(node.id)
  }
  return nodeIds
}

const isEveryNodeDeleted = (nodeIds: string[], nodeIdsToDelete: string[]) =>
  nodeIds.length > 0 && nodeIds.every(nodeId => nodeIdsToDelete.includes(nodeId))

export interface DataSinkLocksLookup {
  /** Locks of the sink with this backend id; everything false when the id is unknown. */
  getSinkLocks: (entityId: string | undefined) => DataSinkLocks
  getSinkLockReason: (node: PipelineNode) => SinkLockReason | null
  /** Lock reason of the first locked node among the selected ones. */
  getSelectionLockReason: (nodes: PipelineNode[]) => SinkLockReason | null
  /** The API that deleting these nodes leaves without a sink, or null when there is none. */
  getLastApiSinkLockReason: (nodeIdsToDelete: string[]) => ApiSinkLockReason | null
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

  // Sinks of the active pipeline are counted from its graph nodes.
  const { isStaWithoutOtherFrostSink, isOwsWithoutOtherPostgisSink } = useMemo(() => {
    const namedApis = datasetData?.data?.namedApis ?? []
    const hasSinkInOtherPipeline = (dataSinkType: DataSinkType) =>
      (data?.data ?? []).some(
        sink => sink.dataSinkType === dataSinkType && sink.pipelineId && sink.pipelineId !== pipelineId,
      )
    return {
      isStaWithoutOtherFrostSink:
        hasApiType(namedApis, API_STANDARDS.STA) && !hasSinkInOtherPipeline(DATASINK_TYPES.FROST),
      isOwsWithoutOtherPostgisSink:
        hasApiType(namedApis, API_STANDARDS.OWS) && !hasSinkInOtherPipeline(DATASINK_TYPES.POSTGIS),
    }
  }, [datasetData?.data?.namedApis, data?.data, pipelineId])

  const frostNodeIds = useMemo(() => getNodeIdsOfType(nodes, isFrostNodeData), [nodes])
  const geoNodeIds = useMemo(() => getNodeIdsOfType(nodes, isGeoPersistenceNodeData), [nodes])

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
      const isOnlyFrostNode = isFrostNodeData(node.data) && frostNodeIds.length === 1
      if (isOnlyFrostNode && isStaWithoutOtherFrostSink) return 'inUseBySta'
      const isOnlyGeoNode = isGeoPersistenceNodeData(node.data) && geoNodeIds.length === 1
      if (isOnlyGeoNode && isOwsWithoutOtherPostgisSink) return 'inUseByOws'
      return null
    },
    [getSinkLocks, frostNodeIds, geoNodeIds, isStaWithoutOtherFrostSink, isOwsWithoutOtherPostgisSink],
  )

  const getSelectionLockReason = useCallback(
    (nodes: PipelineNode[]): SinkLockReason | null =>
      nodes
        .filter(node => node.selected)
        .map(getSinkLockReason)
        .find(reason => reason !== null) ?? null,
    [getSinkLockReason],
  )

  // The per-node lock misses a delete of several sink nodes of the same pipeline at once.
  const getLastApiSinkLockReason = useCallback(
    (nodeIdsToDelete: string[]): ApiSinkLockReason | null => {
      if (isStaWithoutOtherFrostSink && isEveryNodeDeleted(frostNodeIds, nodeIdsToDelete)) return 'inUseBySta'
      if (isOwsWithoutOtherPostgisSink && isEveryNodeDeleted(geoNodeIds, nodeIdsToDelete)) return 'inUseByOws'
      return null
    },
    [frostNodeIds, geoNodeIds, isStaWithoutOtherFrostSink, isOwsWithoutOtherPostgisSink],
  )

  return { getSinkLocks, getSinkLockReason, getSelectionLockReason, getLastApiSinkLockReason, isLoading }
}
