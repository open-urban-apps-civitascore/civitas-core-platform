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
  createdAt: string
  modifiedAt: string
}

export type PostgisDataSinkPayload = {
  id: string | null
  dataSinkType: typeof DATASINK_TYPES.POSTGIS
  configuration: {
    tableName: string
    dataStructureVersionId: string
  }
}

export type FrostDataSinkPayload = {
  id: string | null
  dataSinkType: typeof DATASINK_TYPES.FROST
  /** Empty for passthrough; a mapped pipeline references its final mapping's target structure. */
  configuration: { dataStructureVersionId?: string }
}

export type DataSinkPayload = PostgisDataSinkPayload | FrostDataSinkPayload

export type CreateDataSinkInput = { datasetId: string; data: DataSinkPayload }

export type UpdateDataSinkInput = { datasetId: string; dataSinkId: string; data: DataSinkPayload }

export type DeleteDataSinkInput = { datasetId: string; dataSinkId: string }
