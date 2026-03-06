import z from 'zod'

import { enumFromConst } from '@/utils/common'

import { WithId } from './common'
import { Group } from './groups'
import { Permission } from './permissions'
import { User } from './users'

export type RoleTab = 'basicInformation' | 'permissions' | 'groupAssignment'

export const ROLE_TYPES = {
  SYSTEM: 'system',
  DATA: 'data',
  GOVERNANCE: 'governance',
} as const

export const RoleTypeEnum = enumFromConst(ROLE_TYPES)

export type RoleType = (typeof ROLE_TYPES)[keyof typeof ROLE_TYPES]

export type BaseRole = {
  id: string
  name: string
  description?: string
  type: RoleType
}

export const ROLE_ORIGINS = {
  DEFAULT: 'default',
  CUSTOM: 'custom',
} as const

export type RoleOrigin = (typeof ROLE_ORIGINS)[keyof typeof ROLE_ORIGINS]

export type Role = BaseRole & {
  tenant: string
  permissions: Permission['id'][]
  users: User['id'][]
  createdAt: string
  lastUpdated: string | null
  updatedBy: string | null
  groups: Group['id'][]
  roleOrigin: RoleOrigin
}

export type CreateRoleData = Omit<Role, 'id'>
export type UpdateRoleData = Role
export type PatchRoleData = Partial<CreateRoleData> & WithId

export type UserRolesTableData = {
  id: string
  name: string
  group: string | null
  inherited: boolean
  type: RoleType
  roleId: string
}
export type RoleInput = Omit<Role, 'id' | 'lastUpdated' | 'updatedBy'>

export type RoleUpdate = Role

export const roleSchema = z.object({
  name: z.string().trim().min(2, {
    message: 'common.errors.atLeast2',
  }),
  description: z.string().optional(),
  roleOrigin: z.enum(['default', 'custom']),
})

export type FormRole = z.infer<typeof roleSchema>
