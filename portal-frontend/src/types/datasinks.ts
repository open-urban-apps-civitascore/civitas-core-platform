import { enumFromConst } from '@/utils/common'

import { DatastructureVersionSummary } from './datastructures'

export const DATASINK_TYPES = {
  FROST: 'FROST',
  POSTGIS: 'POSTGIS',
} as const

export const DatasinkTypeEnum = enumFromConst(DATASINK_TYPES)

export type DatasinkType = (typeof DATASINK_TYPES)[keyof typeof DATASINK_TYPES]

export type Datasink = {
  id: string
  datasetId: string
  pipelineId: string
  dataSinkType: DatasinkType
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
  configuration: Record<string, never>
}

export type DataSinkPayload = PostgisDataSinkPayload | FrostDataSinkPayload

export type CreateDatasinkInput = { datasetId: string; data: DataSinkPayload }

export type UpdateDatasinkInput = { datasetId: string; datasinkId: string; data: DataSinkPayload }

export type DeleteDatasinkInput = { datasetId: string; datasinkId: string }
