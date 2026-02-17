import z from 'zod'

import { enumFromConst } from '@/utils/common'

import { StatusEnum } from './common'

export const SOURCE = {
  OWN: 'OWN',
} as const

export const SourceEnum = enumFromConst(SOURCE)

export type Source = (typeof SOURCE)[keyof typeof SOURCE]

export const DatastructureVersionApiResponseSchema = z.object({
  id: z.string(),
  versionNumber: z.string(),
  description: z.string(),
  source: SourceEnum,
  status: StatusEnum,
  umlModelData: z.json(),
  inUse: z.boolean(),
})

export const DatastructureVersionSummaryApiResponseSchema = DatastructureVersionApiResponseSchema.omit({
  umlModelData: true,
  inUse: true,
})

export const DatastructureApiResponseSchema = DatastructureVersionApiResponseSchema.omit({
  umlModelData: true,
  versionNumber: true,
}).extend({
  name: z.string(),
  versions: z.array(DatastructureVersionSummaryApiResponseSchema),
})

export type DatastructureVersion = z.infer<typeof DatastructureVersionApiResponseSchema>
export type DatastructureVersionSummary = z.infer<typeof DatastructureVersionSummaryApiResponseSchema>
export type Datastructure = z.infer<typeof DatastructureApiResponseSchema>

export type DatastructuresListData = DatastructureVersionSummary & { versions: DatastructuresListData[]; name: string }
