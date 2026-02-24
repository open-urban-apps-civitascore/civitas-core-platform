import z from 'zod'

import { enumFromConst } from '@/utils/common'

import { ItemSchema } from './common'

export const ASSIGNMENT_SCOPE_TYPES = {
  DATASTRUCTURE: 'DATASTRUCTURE',
} as const

export const AssignmentScopeEnum = enumFromConst(ASSIGNMENT_SCOPE_TYPES)

export type AssignmentScope = (typeof ASSIGNMENT_SCOPE_TYPES)[keyof typeof ASSIGNMENT_SCOPE_TYPES]

export const DatastructureAssignmentApiResponseSchema = z.object({
  id: z.string(),
  createdAt: z.string(),
  modifiedAt: z.string(),
  group: ItemSchema,
  role: ItemSchema,
  scopeType: AssignmentScopeEnum,
})

export type DatastructureAssignmentResponse = z.infer<typeof DatastructureAssignmentApiResponseSchema>

export const AssignmentSchema = z.object({
  groupId: z.string(),
  roleId: z.string(),
})

export type DatastructureAssignment = z.infer<typeof AssignmentSchema>
