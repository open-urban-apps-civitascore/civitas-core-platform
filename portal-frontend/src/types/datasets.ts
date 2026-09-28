import { CheckedState } from '@radix-ui/react-checkbox'
import { JSX, ReactNode } from 'react'
import { z } from 'zod'

import { AssignmentScopedInputSchema } from './assignments'
import {
  ItemType,
  MAX_DESCRIPTION_LENGTH,
  MAX_NAME_LENGTH,
  MIN_DESCRIPTION_LENGTH,
  STATUS_TYPES,
  WithId,
} from './common'
import { DatapoolItem, DatapoolItemSchema } from './datapools'
import { NamedApiPayloadSchema, NamedApiSchema } from './namedApis'

export const DATASET_STATUS_TYPES = {
  [STATUS_TYPES.DRAFT]: 'DRAFT',
  READY: 'READY',
  [STATUS_TYPES.AVAILABLE]: 'AVAILABLE',
} as const

export type DatasetStatusTypes = (typeof DATASET_STATUS_TYPES)[keyof typeof DATASET_STATUS_TYPES]

const enumFromConst = <T extends Record<string, string>>(obj: T) =>
  z.enum(Object.values(obj) as [T[keyof T], ...T[keyof T][]])

export const DatasetStatusSchema = enumFromConst(DATASET_STATUS_TYPES)

export type PipelineBasicInfo = {
  id: string
  name: string
  description?: string
  runtimeStatus?: PipelineRuntimeStatus
}

export type PipelineRuntimeStatus = {
  state: 'OK' | 'ERROR' | 'UNKNOWN'
  message?: string | null
  sanitizedStacktrace?: string | null
  occurredAt?: string | null
  source?: 'DEPLOYMENT' | 'RUNTIME' | null
  lastEventId?: string | null
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
  pipelines: z.array(
    z.object({
      id: z.string(),
      name: z.string(),
      description: z.string().optional(),
      runtimeStatus: z
        .object({
          state: z.enum(['OK', 'ERROR', 'UNKNOWN']),
          message: z.string().nullable().optional(),
          sanitizedStacktrace: z.string().nullable().optional(),
          occurredAt: z.string().nullable().optional(),
          source: z.enum(['DEPLOYMENT', 'RUNTIME']).nullable().optional(),
          lastEventId: z.string().nullable().optional(),
        })
        .optional(),
    }),
  ),
  namedApis: z.array(NamedApiSchema).optional(),
  datapool: DatapoolItemSchema.nullable(),
  // True once the data-holding sink (PostGIS table / FROST project) physically exists. Stays true
  // across an unrelease. Drives the data-loss warning before a destructive sink change.
  provisioned: z.boolean().optional(),
})

export type Dataset = z.infer<typeof DatasetApiResponseSchema>

// ---------- API Base Input ----------

export const DatasetBaseInputSchema = z.object({
  name: z.string(),
  description: z.string(),
  openDataAccess: z.boolean(),
  assignments: AssignmentScopedInputSchema.array(),
  datapoolId: z.string().nullable(),
  namedApis: z.array(NamedApiPayloadSchema).optional(),
})

// ---------- API Create ----------

export const DatasetCreateApiSchema = DatasetBaseInputSchema.partial().required({
  name: true,
})

export type DatasetCreateApiData = z.infer<typeof DatasetCreateApiSchema>

// ---------- API Update ----------

export const DatasetUpdateApiSchema = DatasetBaseInputSchema.partial().required({
  name: true,
})

export type DatasetUpdateApiData = z.infer<typeof DatasetUpdateApiSchema> & WithId

// ---------- API Patch ----------

export const DatasetPatchApiSchema = DatasetBaseInputSchema.partial()

export type DatasetPatchApiData = z.infer<typeof DatasetPatchApiSchema> & WithId

//  FORM SCHEMAS

// ---------- Base Form Shape ----------

export const DatasetBaseFormSchema = z.object({
  id: z.string(),
  // Backend currently enforces name to be at least 3 characters
  name: z.string().trim().min(3, 'common.errors.atLeast3').max(MAX_NAME_LENGTH, 'common.errors.nameMaxLength'),
  description: z
    .string()
    .trim()
    .min(MIN_DESCRIPTION_LENGTH, 'common.errors.descriptionRequired')
    .max(MAX_DESCRIPTION_LENGTH, 'common.errors.descriptionMaxLength'),
  openDataAccess: z.boolean(),
  datapoolId: z.string().nullable(),
})

export type DatasetBaseFormData = z.input<typeof DatasetBaseFormSchema>

// ---------- Create Form ----------

export const DatasetCreateFormSchema = z.object({
  name: z.string().trim().min(3, 'common.errors.atLeast3').max(MAX_NAME_LENGTH, 'common.errors.nameMaxLength'),
  datapoolId: z.string().nullable(),
  description: z
    .string()
    .trim()
    .min(MIN_DESCRIPTION_LENGTH, 'common.errors.descriptionRequired')
    .max(MAX_DESCRIPTION_LENGTH, 'common.errors.descriptionMaxLength'),
})

export type DatasetCreateFormData = z.input<typeof DatasetCreateFormSchema>

// ---------- Draft Mode (minimal validation) ----------

export const DatasetFormDraftSchema = DatasetBaseFormSchema.partial().extend({
  id: z.string(),
  name: z.string().trim().min(3, 'common.errors.atLeast3').max(MAX_NAME_LENGTH, 'common.errors.nameMaxLength'),
  description: z
    .string()
    .trim()
    .min(MIN_DESCRIPTION_LENGTH, 'common.errors.descriptionRequired')
    .max(MAX_DESCRIPTION_LENGTH, 'common.errors.descriptionMaxLength'),
})

export type DatasetFormDraft = z.input<typeof DatasetFormDraftSchema>

// ---------- Available Mode (strict validation) ----------

export const DatasetFormAvailableSchema = z.object({
  id: z.string().min(1),
  name: z.string().trim().min(3, 'common.errors.atLeast3').max(MAX_NAME_LENGTH, 'common.errors.nameMaxLength'),
  description: z
    .string()
    .trim()
    .min(MIN_DESCRIPTION_LENGTH, 'common.errors.descriptionRequired')
    .max(MAX_DESCRIPTION_LENGTH, 'common.errors.descriptionMaxLength'),
  openDataAccess: z.boolean(),
  datapoolId: z.string().nullable(),
})

export type BaseDatasetTableData = {
  id: string
  name: string
  modifiedAt: string
  createdBy: ItemType
  dataSetStatus: DatasetStatusTypes
}

export type DatasetTableData = BaseDatasetTableData & {
  datapool: DatapoolItem | null
}

export type CompletionStepParam = 'access-management' | 'data-flow' | 'apis'

export type CompletionStepData = {
  title: string
  description?: string
  isCompleted?: CheckedState
  buttons: {
    text: string
    routeParam: CompletionStepParam
    queryParam?: string
  }[]
  content?: JSX.Element
  actionElement?: ReactNode
}
