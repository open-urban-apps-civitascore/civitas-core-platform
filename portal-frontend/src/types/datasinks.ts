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

export type PostgisPipelineDatasink = {
  id: string | null
  dataSinkType: typeof DATASINK_TYPES.POSTGIS
  configuration: {
    tableName: string
    dataStructureVersionId: string
  }
}

export type FrostPipelineDatasink = {
  id: string | null
  dataSinkType: typeof DATASINK_TYPES.FROST
  configuration: Record<string, never>
}

export type PipelineDatasink = PostgisPipelineDatasink | FrostPipelineDatasink
