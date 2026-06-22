import z from 'zod'

import { AssignmentScopedInputSchema } from './assignments'
import { WithId } from './common'

export const DatapoolTabValues = {
  basicInfo: 'basicInfo',
  accessManagement: 'accessManagement',
  datasets: 'datasets',
  datasources: 'datasources',
}

export type DatapoolTab = (typeof DatapoolTabValues)[keyof typeof DatapoolTabValues]

// --- Backend Schema

const DatapoolBaseApiResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string(),
  contactPerson: z
    .object({
      id: z.string(),
      name: z.string(),
    })
    .nullable(),
  modifiedAt: z.string(),
  createdAt: z.string(),
})

export const DatapoolSummaryApiResponseSchema = DatapoolBaseApiResponseSchema.extend({
  datasets: z.string().array(),
})

export const DatapoolItemSchema = DatapoolBaseApiResponseSchema.pick({ id: true, name: true })
export type DatapoolItem = z.infer<typeof DatapoolItemSchema>

export const DatapoolApiResponseSchema = DatapoolBaseApiResponseSchema

export type DatapoolSummary = z.infer<typeof DatapoolSummaryApiResponseSchema>
export type Datapool = z.infer<typeof DatapoolApiResponseSchema>

export const DatapoolInputBaseSchema = z.object({
  name: z.string().trim().min(1, 'common.errors.required'),
  description: z.string().trim().min(1, 'common.errors.required'),
  contactPersonId: z.string().nullable().optional(),
  assignments: z.array(AssignmentScopedInputSchema).optional(),
})

export type DatapoolCreateData = z.infer<typeof DatapoolInputBaseSchema>

export type DatapoolPatchData = Partial<z.infer<typeof DatapoolInputBaseSchema>> & WithId

// --- Form Schema

export const DatapoolFormSchema = z.object({
  id: z.string(),
  name: z.string().trim().min(1, 'common.errors.required'),
  description: z.string().trim().min(1, 'common.errors.required'),
  contactPersonId: z.string().nullable(),
})

export type DatapoolFormData = z.infer<typeof DatapoolFormSchema>
