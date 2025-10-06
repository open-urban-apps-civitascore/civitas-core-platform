import { parsePhoneNumberFromString } from 'libphonenumber-js'
import { z } from 'zod'

export type Category = {
  id: string
  title: string
}

export type UserGroup = Category

export type Authority = Category & {
  departments: Category[]
}

export type UserAuthority = {
  id: string
  department: {
    id: string
  } | null
} | null

export type UserResponse = {
  id: string
  firstName: string
  lastName: string
  displayName: string
  email: string
  phone: string
  title: TitleSchemaType
  authority: UserAuthority | null
  group: string | null
  active: boolean
  roles: string[]
  position: string | null
  positionDescription: string | null
}
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

export type UpdateUserData = UserResponse
export type CreateUserData = Omit<UpdateUserData, 'id'>

export const TitleSchema = z.enum(['male', 'female'])

export type TitleSchemaType = z.infer<typeof TitleSchema>

export const PhoneSchema = z.string().superRefine((value, ctx) => {
  const phoneNumber = parsePhoneNumberFromString(value, 'DE')
  if (!phoneNumber || !phoneNumber.isValid()) {
    ctx.addIssue({
      code: 'custom',
      message: 'common.errors.invalidPhone',
    })
    return z.NEVER
  }
})

export const UserFormSchema = z.object({
  id: z.string(),
  firstName: z.string().min(2, {
    message: 'common.errors.atLeast2',
  }),

  title: TitleSchema,
  lastName: z.string().min(2, {
    message: 'common.errors.atLeast2',
  }),
  email: z.email({
    message: 'common.errors.invalidEmail',
  }),
  authority: z.string().nullable(),
  department: z.string().nullable(),
  group: z.string().nullable(),
  phone: PhoneSchema,
  active: z.boolean(),
  position: z.string().min(2).or(z.literal('')).nullable(),
  positionDescription: z.string().min(10).or(z.literal('')).nullable(),
  roles: z.array(z.string()),
})

export type UserFormData = z.infer<typeof UserFormSchema>

export const UserUpdateFormSchema = UserFormSchema.extend({
  id: z.string(),
})

export type UserUpdateFormData = z.infer<typeof UserUpdateFormSchema>
