import z from 'zod'

import { Item } from './common'

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

export type RoleResponse = BaseRole & {
  tenant: string
  permissions: string[] | null
  user: string[] | null
  createdAt: string
}

export type UserRolesTableData = {
  id: string
  name: string
  group: string | null
  dataspace: Item | null
  inherited: boolean
  type: RoleType
}

export type RoleInput = Omit<RoleResponse, 'id'>

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
  name: z
    .string()
    .min(2, {
      message: 'roles.form.formErrors.name.minLength',
    })
    .max(30, { message: 'roles.form.formErrors.name.maxLength' }),
  description: z.string().max(100, { message: 'roles.form.formErrors.description.maxLength' }).optional(),
})

export type FormRole = z.infer<typeof roleSchema>
