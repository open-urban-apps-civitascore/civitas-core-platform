import z from 'zod'

import { enumFromConst } from '@/utils/common'

import { AssignmentSchema } from './assignments'
import { ItemSchema, STATUS_TYPES, WithId } from './common'

export const DATASTRUCTURE_STATUS_TYPES = {
  DRAFT: 'DRAFT',
  AVAILABLE: 'AVAILABLE',
} as const satisfies Partial<typeof STATUS_TYPES>

export const DatastructureStatusEnum = enumFromConst(DATASTRUCTURE_STATUS_TYPES)

export type DatastructureStatusTypes = (typeof DATASTRUCTURE_STATUS_TYPES)[keyof typeof DATASTRUCTURE_STATUS_TYPES]

export type DatastructureTab = 'basicInfo' | 'versions' | 'accessPermissions'

export type DatastructureVersionTab = 'structure' | 'versionInfo'

export const DATASTRUCTURE_VERSION_SOURCE = {
  OWN: 'OWN',
} as const

export const UMLModelStylesPayloadSchema = z.object({
  viewport: z.object({ x: z.number(), y: z.number(), zoom: z.number() }).optional(),
  nodePositions: z.record(z.string(), z.object({ x: z.number(), y: z.number() })),
})

export const DatastructureVersionSourceEnum = enumFromConst(DATASTRUCTURE_VERSION_SOURCE)

export type DatastructureVersionSource =
  (typeof DATASTRUCTURE_VERSION_SOURCE)[keyof typeof DATASTRUCTURE_VERSION_SOURCE]

// DATASTRUCTURE VERSIONS

export const DatastructureVersionApiResponseSchema = z.object({
  id: z.string(),
  version: z.string(),
  description: z.string().nullable(),
  dataStructureVersionStatus: DatastructureStatusEnum,
  dataStructureVersionSource: DatastructureVersionSourceEnum,
  modelAtlasUri: z.string().nullable(),
  modelName: z.string().nullable(),
  model: z.string().nullable(),
  styles: UMLModelStylesPayloadSchema.nullable(),
  inUse: z.boolean().optional(),
  dataStructure: ItemSchema,
  createdAt: z.string(),
  modifiedAt: z.string(),
})

export const DatastructureVersionSummaryApiResponseSchema = z.object({
  id: z.string(),
  version: z.string(),
  description: z.string().nullable(),
  dataStructureVersionStatus: DatastructureStatusEnum,
  dataStructureVersionSource: DatastructureVersionSourceEnum,
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
  styles: UMLModelStylesPayloadSchema.nullable(),
})

export const DatastructureVersionFormAvailableSchema = DatastructureVersionFormDraftSchema.extend({
  description: z.string().trim().min(1, 'common.errors.required'),
  modelAtlasUri: z.string().trim().min(1, 'common.errors.required'),
  modelName: z.string().trim().min(1, 'common.errors.required'),
  model: z.string().trim().min(1, 'common.errors.required'),
  styles: UMLModelStylesPayloadSchema,
})

export const DatastructureVersionCreateSchema = DatastructureVersionFormDraftSchema.omit({ id: true })
  .partial()
  .extend({
    version: z.string().trim().min(1, 'common.errors.required'),
    dataStructureVersionSource: DatastructureVersionSourceEnum,
  })

export type DatastructureVersion = z.infer<typeof DatastructureVersionApiResponseSchema>
export type DatastructureVersionSummary = z.infer<typeof DatastructureVersionSummaryApiResponseSchema>

export type DatastructureVersionFormData = z.infer<typeof DatastructureVersionFormDraftSchema>
export type DatastructureVersionCreateData = z.infer<typeof DatastructureVersionCreateSchema>
export type DatastructureVersionPutData = DatastructureVersionCreateData & WithId
export type DatastructureVersionPatchData = Partial<DatastructureVersionCreateData> & WithId

export type DatastructureVersionsListData = {
  id: string
  name: string
  description: string
  status: DatastructureStatusTypes
  source: DatastructureVersionSource
  versionNumber: string
}

// DATASTRUCTURE TYPES

export const DatastructureApiResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  dataStructureStatus: DatastructureStatusEnum,
  createdFromDataSource: z.boolean(),
  assignments: z.array(AssignmentSchema).optional(),
  inUse: z.boolean().optional(),
  createdAt: z.string(),
  modifiedAt: z.string(),
  dataStructureVersions: z.array(DatastructureVersionSummaryApiResponseSchema),
})

export type Datastructure = z.infer<typeof DatastructureApiResponseSchema>

export const DatastructureApiResponseSummarySchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().nullable(),
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

export const DatastructureCreateFormSchema = z.object({
  name: z.string().trim().min(1, 'common.errors.required'),
})

export const DatastructureCreateDataSchema = z.object({
  name: z.string().trim().min(1, 'common.errors.required'),
  createdFromDataSource: z.boolean(),
  description: z.string().trim().optional(),
  dataStructureVersionIds: z.array(z.string()).optional(),
  assignments: z.array(AssignmentSchema).optional(),
})

export type DatastructureCreateFormData = z.infer<typeof DatastructureCreateFormSchema>
export type DatastructureCreateData = z.infer<typeof DatastructureCreateDataSchema>

export type DatastructurePutData = DatastructureFormDraft
export type DatastructurePatchData = Partial<DatastructureCreateData> & WithId

// DATASTRUCTURE LIST DATA

export type DatastructuresListData = {
  id: string
  name: string
  description: string
  status: DatastructureStatusTypes
  source: DatastructureVersionSource | null
  versionNumber: string | null
  versions: DatastructuresListData[]
}
