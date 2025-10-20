import z from 'zod'

export const ROLE_TYPES = {
  SYSTEM: 'System',
  DATA: 'Data',
  GOVERNANCE: 'Governance',
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

export type RoleInput = Omit<RoleResponse, 'id'>

export type RoleUpdate = RoleResponse

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
