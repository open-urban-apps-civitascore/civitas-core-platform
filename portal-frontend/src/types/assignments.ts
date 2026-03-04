import z from 'zod'

import { enumFromConst } from '@/utils/common'

import { ItemSchema } from './common'

export const ASSIGNMENT_SCOPE_TYPES = {
  DATASTRUCTURE: 'DATASTRUCTURE',
} as const

export const AssignmentScopeEnum = enumFromConst(ASSIGNMENT_SCOPE_TYPES)

export const AssignmentApiResponseSchema = z.object({
  id: z.string(),
  createdAt: z.string(),
  modifiedAt: z.string(),
  group: ItemSchema,
  role: ItemSchema,
  scopeType: AssignmentScopeEnum,
  scope: ItemSchema,
})

export const AssignmentSchema = z.object({
  groupId: z.string(),
  roleId: z.string(),
})
export type Assignment = z.infer<typeof AssignmentApiResponseSchema>

export type CreateAssignmentData = { groupId: string; roleId: string }

export type UpdateAssignmentData = CreateAssignmentData & { id: string }

export const AssignmentScopedInputSchema = z.object({
  groupId: z.string().trim().min(1, 'common.errors.required'),
  roleId: z.string().trim().min(1, 'common.errors.required'),
})
