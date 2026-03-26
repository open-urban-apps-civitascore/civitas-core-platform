import z from 'zod'

import { enumFromConst } from '@/utils/common'

import { MAX_DESCRIPTION_LENGTH, MAX_NAME_LENGTH, MIN_NAME_LENGTH, WithId } from './common'

export type RoleTab = 'basicInformation' | 'permissions' | 'groupAssignment'

export const ROLE_TYPES = {
  SYSTEM: 'SYSTEM',
  DATA: 'DATA',
} as const

export const RoleTypeEnum = enumFromConst(ROLE_TYPES)

export type RoleType = (typeof ROLE_TYPES)[keyof typeof ROLE_TYPES]

export const ROLE_CATEGORIES = {
  TENANTADMINISTRATION: 'TENANT_ADMINISTRATION',
  DATA: 'DATA',
} as const

export const RoleCategoryEnum = enumFromConst(ROLE_CATEGORIES)

export type RoleCategory = (typeof ROLE_CATEGORIES)[keyof typeof ROLE_CATEGORIES]

export type PermissionSummary = {
  id: string
  name: string
  permissionType: string
}

export type UserSummary = {
  id: string
  name: string
}

export type BaseRole = {
  id: string
  name: string
  description: string | null
  roleType: RoleType
}

export type Role = BaseRole & {
  permissions: PermissionSummary[]
  readonly: boolean
  modifiedBy: UserSummary | null
  modifiedAt: string | null
  createdAt: string
  groupCount: number
  userCount: number
}

export type CreateRoleData = {
  name: string
  description?: string
  roleType: RoleType
  permissionIds?: string[]
  readonly?: boolean
}

export type UpdateRoleData = CreateRoleData & WithId

export type PatchRoleData = Partial<CreateRoleData> & WithId

export type UserRolesTableData = {
  id: string
  name: string
  group: string | null
  inherited: boolean
  type: RoleType
  roleId: string
}

export type RoleInput = Omit<CreateRoleData, 'id'>

export type RoleUpdate = UpdateRoleData

export const RoleSchema = z.object({
  name: z
    .string()
    .trim()
    .min(MIN_NAME_LENGTH, {
      message: 'common.errors.nameRequired',
    })
    .max(MAX_NAME_LENGTH, 'common.errors.nameMaxLength'),
  description: z.string().trim().max(MAX_DESCRIPTION_LENGTH, 'common.errors.descriptionMaxLength').optional(),
  readonly: z.boolean(),
})

export type FormRole = z.infer<typeof RoleSchema>
