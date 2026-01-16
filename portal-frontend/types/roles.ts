import z from 'zod'

import { Item, WithId } from './common'
import { Group } from './groups'
import { Permission } from './permissions'
import { User } from './users'

export const ROLE_TYPES = {
  SYSTEM: 'system',
  DATA: 'data',
  GOVERNANCE: 'governance',
} as const

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

export type RoleResponse = BaseRole & {
  tenant: string
  permissions: Permission['id'][]
  users: User['id'][]
  createdAt: string
  lastUpdated: string | null
  updatedBy: string | null
  groups: Group['id'][]
  roleOrigin: RoleOrigin
}

export type CreateRoleData = Omit<RoleResponse, 'id'>
export type UpdateRoleData = RoleResponse
export type PatchRoleData = Partial<CreateRoleData> & WithId

export type UserRolesTableData = {
  id: string
  name: string
  group: string | null
  dataspace: Item | null
  inherited: boolean
  type: RoleType
}
export type RoleInput = Omit<RoleResponse, 'id' | 'lastUpdated' | 'updatedBy'>

export type RoleUpdate = RoleResponse

export type Role = {
  id: string
  name: string
  description: string
  tenant: string
  type: string
  permissions: string[]
  user: string[]
  createdAt: string
}

export const roleSchema = z.object({
  name: z.string().trim().min(2, {
    message: 'common.errors.atLeast2',
  }),
  description: z.string().optional(),
  roleOrigin: z.enum(['default', 'custom']),
})

export type FormRole = z.infer<typeof roleSchema>
