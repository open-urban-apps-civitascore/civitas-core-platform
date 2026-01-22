import { parsePhoneNumberFromString } from 'libphonenumber-js'
import { z } from 'zod'

import { Item, WithId } from './common'

export type Contact = {
  id: string
  displayName: string
  email: string
}

export type UserGroup = Item

export type Authority = Item & {
  departments: Item[]
}

export type UserAuthority = {
  id: string
  department: {
    id: string
  } | null
} | null

export type User = {
  id: string
  firstName: string
  lastName: string
  email: string
  phone: string
  title: TitleType
  authority: UserAuthority | null
  groups: string[]
  active: boolean
  positionDescription: string | null
}

export type CreateUserData = Omit<User, 'id'>
export type UpdateUserData = User
export type PatchUserData = Partial<CreateUserData> & WithId

export type ListUser = {
  id: string
  fullName: string
  authority: string
  department: string
  email: string
  isActive: boolean
}

export type GroupListUser = Omit<ListUser, 'roles'> & {
  assignedAt: string
}

export type GroupAssignmentUser = {
  id: string
  fullName: string
  email: string
  isActive: boolean
}

export type GroupUser = {
  id: string
  fullName: string
  email: string
  authority: UserAuthority | null
  isActive: boolean
}

export const TitleSchema = z.enum(['MR', 'MS', 'OTHER'])

export type TitleType = z.infer<typeof TitleSchema>

export const PhoneSchema = z.string().superRefine((value, ctx) => {
  const phoneNumber = parsePhoneNumberFromString(value, 'DE')
  if (!phoneNumber) return z.NEVER
  if (!phoneNumber?.isValid()) {
    ctx.addIssue({
      code: 'custom',
      message: 'common.errors.invalidPhone',
    })
    return z.NEVER
  }
})

export const UserFormSchema = z.object({
  id: z.string(),
  title: TitleSchema,
  firstName: z.string().min(2, {
    message: 'common.errors.atLeast2',
  }),

  lastName: z.string().min(2, {
    message: 'common.errors.atLeast2',
  }),
  email: z.email({
    message: 'common.errors.invalidEmail',
  }),
  authority: z.string(),
  department: z.string(),
  phone: PhoneSchema,
  active: z.boolean(),
  positionDescription: z
    .string()
    .min(10, {
      message: 'common.errors.atLeast10',
    })
    .or(z.literal('')),
})

export type UserFormData = z.infer<typeof UserFormSchema>

export const UserUpdateFormSchema = UserFormSchema.extend({
  id: z.string(),
})

export type UserUpdateFormData = z.infer<typeof UserUpdateFormSchema>
