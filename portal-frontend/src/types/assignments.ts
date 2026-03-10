import z from 'zod'

import { enumFromConst } from '@/utils/common'

import { ItemSchema } from './common'
import { RoleTypeEnum } from './roles'

export const ASSIGNMENT_SCOPE_TYPES = {
  DATASTRUCTURE: 'DATASTRUCTURE',
} as const

export const AssignmentScopeEnum = enumFromConst(ASSIGNMENT_SCOPE_TYPES)

export type AssignmentScope = (typeof ASSIGNMENT_SCOPE_TYPES)[keyof typeof ASSIGNMENT_SCOPE_TYPES]

export const AssignmentRoleSchema = z.object({
  id: z.string(),
  name: z.string(),
  roleType: RoleTypeEnum,
  description: z.string(),
  readonly: z.boolean(),
})

export type AssignmentRole = z.infer<typeof AssignmentRoleSchema>

export const AssignmentApiResponseSchema = z.object({
  id: z.string(),
  createdAt: z.string(),
  modifiedAt: z.string(),
  group: ItemSchema,
  role: AssignmentRoleSchema,
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
