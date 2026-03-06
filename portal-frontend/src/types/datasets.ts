import { CheckedState } from '@radix-ui/react-checkbox'
import { JSX } from 'react'
import { z } from 'zod'

import { ItemType, STATUS_TYPES, WithId } from './common'

export const DATASET_STATUS_TYPES = {
  [STATUS_TYPES.DRAFT]: 'DRAFT',
  READY: 'READY',
  [STATUS_TYPES.AVAILABLE]: 'AVAILABLE',
} as const

export type DatasetStatusTypes = (typeof DATASET_STATUS_TYPES)[keyof typeof DATASET_STATUS_TYPES]

const enumFromConst = <T extends Record<string, string>>(obj: T) =>
  z.enum(Object.values(obj) as [T[keyof T], ...T[keyof T][]])

export const DatasetStatusSchema = enumFromConst(DATASET_STATUS_TYPES)

export type Distribution = {
  id: string
  accessUrl: string
}

export type PipelineBasicInfo = {
  id: string
  name: string
}

// BACKEND COMMUNICATION

// ---------- API Response ----------

export const DatasetApiResponseSchema = z.object({
  id: z.string(),
  createdAt: z.string(),
  createdBy: z.object({ id: z.string(), name: z.string() }),
  modifiedAt: z.string(),
  name: z.string(),
  description: z.string(),
  dataSetStatus: DatasetStatusSchema,
  openDataAccess: z.boolean(),
  distributions: z.array(z.object({ id: z.string(), accessUrl: z.string() })),
  pipelines: z.array(z.object({ id: z.string(), name: z.string() })),
})

export type Dataset = z.infer<typeof DatasetApiResponseSchema>

// ---------- API Create ----------

export const DatasetCreateApiSchema = z.object({
  name: z.string(),
  description: z.string().optional(),
  openDataAccess: z.boolean().optional(),
  dataSetStatus: DatasetStatusSchema.optional(), // Is neccessary for JSON server usage. Needs to be removed, when backend API is implemented.
})

export type DatasetCreateApiData = z.infer<typeof DatasetCreateApiSchema>

// ---------- API Update ----------

export const DatasetUpdateApiSchema = DatasetCreateApiSchema.extend({
  assignments: z.array(z.any()).optional(),
})

export type DatasetUpdateApiData = z.infer<typeof DatasetUpdateApiSchema> & WithId

//  FORM SCHEMAS

// ---------- Base Form Shape ----------

export const DatasetBaseFormSchema = z.object({
  id: z.string(),
  name: z.string().trim().min(3, 'common.errors.atLeast3'),
  description: z.string().trim().min(1, 'common.errors.descriptionRequired'),
  openDataAccess: z.boolean(),
})

export type DatasetBaseFormData = z.input<typeof DatasetBaseFormSchema>

// ---------- Create Form ----------

export const DatasetCreateFormSchema = z.object({
  name: z.string().trim().min(3, 'common.errors.atLeast3'),
})

export type DatasetCreateFormData = z.input<typeof DatasetCreateFormSchema>

// ---------- Draft Mode (minimal validation) ----------

export const DatasetFormDraftSchema = DatasetBaseFormSchema.partial().extend({
  id: z.string(),
  name: z.string().trim().min(3, 'common.errors.atLeast3'),
})

export type DatasetFormDraft = z.input<typeof DatasetFormDraftSchema>

// ---------- Available Mode (strict validation) ----------

export const DatasetFormAvailableSchema = z.object({
  id: z.string().min(1),
  name: z.string().trim().min(2, 'common.errors.atLeast3'),
  description: z.string().trim().min(1, 'common.errors.descriptionRequired'),
  openDataAccess: z.boolean(),
})

export type DatasetTableData = {
  id: string
  name: string
  modifiedAt: string
  createdBy: ItemType
  dataSetStatus: DatasetStatusTypes
}

export type CompletionStepParam = 'accessManagement' | 'data-flow'

export type CompletionStepData = {
  title: string
  isCompleted: CheckedState
  buttons: {
    text: string
    routeParam: CompletionStepParam
    queryParam?: string
  }[]
  content?: JSX.Element
}
