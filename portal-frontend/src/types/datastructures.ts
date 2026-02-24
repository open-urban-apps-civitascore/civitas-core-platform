import z from 'zod'

import { enumFromConst } from '@/utils/common'

import { AssignmentSchema } from './assignments'
import { ItemSchema, WithId } from './common'

export const DATASTRUCTURE_STATUS_TYPES = {
  DRAFT: 'DRAFT',
  AVAILABLE: 'AVAILABLE',
} as const

export const DatastructureStatusEnum = enumFromConst(DATASTRUCTURE_STATUS_TYPES)

export type DatastructureStatus = (typeof DATASTRUCTURE_STATUS_TYPES)[keyof typeof DATASTRUCTURE_STATUS_TYPES]

export type DatastructureTab = 'basicInfo' | 'versions' | 'accessPermissions'

export type DatastructureVersionTab = 'structure' | 'versionInfo'

export const DATASTRUCTURE_VERSION_SOURCE = {
  OWN: 'OWN',
} as const

export const DatastructureVersionSourceEnum = enumFromConst(DATASTRUCTURE_VERSION_SOURCE)

export type DatastructureVersionSource =
  (typeof DATASTRUCTURE_VERSION_SOURCE)[keyof typeof DATASTRUCTURE_VERSION_SOURCE]

// DATASTRUCTURE VERSIONS

export const DatastructureVersionApiResponseSchema = z.object({
  id: z.string(),
  version: z.string(),
  description: z.string(),
  dataStructureVersionStatus: DatastructureStatusEnum,
  dataStructureVersionSource: DatastructureVersionSourceEnum,
  modelAtlasUri: z.string().nullable(),
  modelName: z.string().nullable(),
  styles: z.record(z.string(), z.unknown()).nullable(),
  inUse: z.boolean(),
  datastructure: ItemSchema,
  createdAt: z.string(),
  modifiedAt: z.string(),
})

export const DatastructureVersionFormDraftSchema = z.object({
  id: z.string(),
  version: z.string().trim().min(1, 'common.errors.required'),
  description: z.string().trim(),
  dataStructureVersionSource: DatastructureVersionSourceEnum,
  dataStructureVersionStatus: DatastructureStatusEnum,
  modelAtlasUri: z.string().trim().nullable(),
  modelName: z.string().trim().nullable(),
  model: z.string().trim().nullable(),
  styles: z.record(z.string(), z.unknown()).nullable(),
})

export const DatastructureVersionFormAvailableSchema = DatastructureVersionFormDraftSchema.extend({
  description: z.string().trim().min(1, 'common.errors.required'),
  modelAtlasUri: z.string().trim().min(1, 'common.errors.required'),
  modelName: z.string().trim().min(1, 'common.errors.required'),
  model: z.string().trim().min(1, 'common.errors.required'),
  styles: z.record(z.string(), z.unknown()),
})

export type DatastructureVersion = z.infer<typeof DatastructureVersionApiResponseSchema>

export type DatastructureVersionFormData = z.infer<typeof DatastructureVersionFormDraftSchema>
export type DatastructureVersionCreateData = Omit<DatastructureVersionFormData, 'id'>
export type DatastructureVersionPutData = DatastructureVersionFormData
export type DatastructureVersionPatchData = Partial<DatastructureVersionCreateData> & WithId

export type DatastructureVersionsListData = {
  id: string
  name: string
  description: string
  status: DatastructureStatus
  source: DatastructureVersionSource
  versionNumber: string
}

// DATASTRUCTURE TYPES

export const DatastructureApiResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string(),
  dataStructureStatus: DatastructureStatusEnum,
  createdFromDataSource: z.boolean(),
  assignments: z.array(AssignmentSchema),
  inUse: z.boolean(),
  createdAt: z.string(),
  modifiedAt: z.string(),
  dataStructureVersions: z.array(DatastructureVersionApiResponseSchema),
})

export type Datastructure = z.infer<typeof DatastructureApiResponseSchema>

export const DatastructureApiResponseSummarySchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string(),
})

export const DatastructureFormDraftSchema = z.object({
  id: z.string(),
  name: z.string().trim().min(1, 'common.errors.required'),
  description: z.string().trim(),
  dataStructureStatus: DatastructureStatusEnum,
  dataStructureVersionIds: z.array(z.string()),
  assignments: z.array(AssignmentSchema),
})

export const DatastructureFormAvailableSchema = DatastructureFormDraftSchema.extend({
  description: z.string().trim().min(1, 'common.errors.required').max(150, 'common.errors.descriptionMaxLength'),
})

export type DatastructureFormDraft = z.infer<typeof DatastructureFormDraftSchema>
export type DatastructureFormAvailable = z.infer<typeof DatastructureFormAvailableSchema>

export const DatastructureCreateFormSchema = DatastructureFormDraftSchema.omit({ id: true })
export type DatastructureCreateData = z.infer<typeof DatastructureCreateFormSchema>

export type DatastructureUpdateData = DatastructureFormDraft

// DATASTRUCTURE LIST DATA

export type DatastructuresListData = {
  id: string
  name: string
  description: string
  status: DatastructureStatus
  source: DatastructureVersionSource | null
  versionNumber: string | null
  versions: DatastructuresListData[]
}
