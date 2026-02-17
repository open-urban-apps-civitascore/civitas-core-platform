import z from 'zod'

import { enumFromConst } from '@/utils/common'

import { StatusEnum } from './common'

export type DatastructureTab = 'basicInfo' | 'versions' | 'accessPermissions'

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

export type DatastructureVersionsListData = DatastructureVersionSummary

export type DatastructuresListData = Omit<DatastructureVersionSummary, 'versionNumber' | 'source'> & {
  name: string
  versionNumber: string | null
  source: Source | null
  versions: DatastructuresListData[]
}

export const DatastructureFormDraftSchema = z.object({
  id: z.string(),
  name: z.string().trim().min(1, 'common.errors.required'),
  description: z.string().trim(),
  status: StatusEnum,
})

export const DatastructureFormAvailableSchema = z.object({
  id: z.string(),
  name: z.string().trim().min(1, 'common.errors.required'),
  description: z.string().trim().min(1, 'common.errors.required'),
  status: StatusEnum,
})

export type DatastructureFormDraft = z.infer<typeof DatastructureFormDraftSchema>
export type DatastructureFormAvailable = z.infer<typeof DatastructureFormAvailableSchema>

export const DatastructureCreateFormSchema = DatastructureApiResponseSchema.pick({ name: true })
export type DatastructureCreateFormData = z.infer<typeof DatastructureCreateFormSchema>

// TODO: only use DatastructureCreateApiData when API is connected and rename it
export type DatastructureCreateApiData = z.infer<typeof DatastructureCreateFormSchema>
export type DatastructureCreateJsonServerData = Omit<Datastructure, 'id'>

export type DatastructureUpdateData = DatastructureFormDraft
