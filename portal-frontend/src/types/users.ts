import { parsePhoneNumberFromString } from 'libphonenumber-js'
import { z } from 'zod'

import { Item, WithId } from './common'

export type Contact = {
  id: string
  displayName: string
  email: string
}

export type UserGroup = Item

export const TitleSchema = z.enum(['MR', 'MS', 'OTHER'])

export type TitleType = z.infer<typeof TitleSchema>

export const PhoneSchema = z
  .string()
  .optional()
  .refine(value => !value || /^[0-9+()\s-]+$/.test(value), { message: 'common.errors.invalidPhone' })
  .superRefine((value, ctx) => {
    if (!value) return

    const phoneNumber = parsePhoneNumberFromString(value, 'DE')
    if (!phoneNumber || !phoneNumber.isValid()) {
      ctx.addIssue({
        code: 'custom',
        message: 'common.errors.invalidPhone',
      })
    }
  })

export const UserSchema = z.object({
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
  phone: PhoneSchema.optional(),
  active: z.boolean(),
  groups: z.array(z.string()),
})

export type User = z.infer<typeof UserSchema>

export type ListUser = {
  id: string
  fullName: string
  email: string
  active: boolean
}

export type GroupListUser = ListUser & {
  assignedAt: string
}

export const UserFormSchema = UserSchema.omit({
  groups: true,
})

export type UserFormData = z.infer<typeof UserFormSchema>

export type CreateUserData = Omit<User, 'id'>
export type UpdateUserData = User
export type PatchUserData = Partial<CreateUserData> & WithId
