import { enumFromConst } from '@/utils/common'

import { DatastructureVersionSummary } from './datastructures'

export const DATASINK_TYPES = {
  FROST: 'FROST',
  POSTGIS: 'POSTGIS',
} as const

export const DataSinkTypeEnum = enumFromConst(DATASINK_TYPES)

export type DataSinkType = (typeof DATASINK_TYPES)[keyof typeof DATASINK_TYPES]

export type DataSink = {
  id: string
  datasetId: string
  pipelineId: string
  dataSinkType: DataSinkType
  configuration: {
    tableName: string
    dataStructureVersion: DatastructureVersionSummary
  }
  /**
   * Versioned CORE URN of this DataSink's configuration artifact in Model Forge; used as the
   * pipeline node's `sinkRef`. Absent on sinks that carry no configuration (e.g. FROST passthrough).
   */
  configurationUrn?: string
  /** True once the sink's storage has been provisioned. */
  provisioned?: boolean
  /** True while a Layer references this sink. */
  inUseByLayer?: boolean
  createdAt: string
  modifiedAt: string
}

export type PostgisDataSinkPayload = {
  id: string | null
  dataSinkType: typeof DATASINK_TYPES.POSTGIS
  configuration: {
    tableName: string
    /**
     * Versioned CORE URN of the DataStructure whose rows are written to the table (the backend's
     * PostgisConfiguration.element, a Model-Forge soft reference — the same resolution FROST uses,
     * not the raw version id). Required by the backend. Always the geo persistence node's own data
     * structure, also when a mapping feeds the sink.
     */
    element?: string
  }
  /** Acknowledges that this update discards the sink's stored data, rebuilt on the next release (see backend guard). */
  confirmDataLoss?: boolean
}

export type FrostDataSinkPayload = {
  id: string | null
  dataSinkType: typeof DATASINK_TYPES.FROST
  /**
   * Empty for a passthrough pipeline; a mapped pipeline references its final mapping's Thing-shaped
   * target structure by its versioned CORE URN (the backend's FrostConfiguration.element, a
   * Model-Forge soft reference — not the raw version id).
   */
  configuration: { element?: string }
  /** Acknowledges that this update re-provisions the sink and discards its stored data (see backend guard). */
  confirmDataLoss?: boolean
}

export type DataSinkPayload = PostgisDataSinkPayload | FrostDataSinkPayload

export type CreateDataSinkInput = { datasetId: string; data: DataSinkPayload }

export type UpdateDataSinkInput = { datasetId: string; dataSinkId: string; data: DataSinkPayload }

export type DeleteDataSinkInput = { datasetId: string; dataSinkId: string }
