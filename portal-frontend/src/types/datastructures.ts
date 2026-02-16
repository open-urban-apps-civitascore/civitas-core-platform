import z from 'zod'

import { enumFromConst } from '@/utils/common'

import { StatusEnum } from './common'

export const SOURCE = {
  OWN: 'OWN',
} as const

export const SourceEnum = enumFromConst(SOURCE)

export type Source = (typeof SOURCE)[keyof typeof SOURCE]

export const DatastructureVersionApiSchema = z.object({
  id: z.string(),
  name: z.string(),
  versionNumber: z.string(),
  description: z.string(),
  source: SourceEnum,
  status: StatusEnum,
  umlModelData: z.json(),
  inUse: z.boolean(),
})

export const DatastructureVersionSummaryApiSchema = DatastructureVersionApiSchema.omit({
  umlModelData: true,
  inUse: true,
})

export const DatastructureApiSchema = DatastructureVersionApiSchema.omit({
  umlModelData: true,
  versionNumber: true,
}).extend({
  versions: z.array(DatastructureVersionSummaryApiSchema),
})

export type DatastructureVersion = z.infer<typeof DatastructureVersionApiSchema>
export type DatastructureVersionSummary = z.infer<typeof DatastructureVersionSummaryApiSchema>
export type Datastructure = z.infer<typeof DatastructureApiSchema>

export type DatastructuresListData = DatastructureVersionSummary & { versions: DatastructuresListData[] }
