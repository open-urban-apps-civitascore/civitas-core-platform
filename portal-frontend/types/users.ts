import { parsePhoneNumberFromString } from 'libphonenumber-js'
import { z } from 'zod'

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
  id: z.string().optional(),
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
  authority: z.string().optional(),
  department: z.string().optional(),
  group: z.string().nullable(),
  phone: PhoneSchema,
  active: z.boolean(),
  position: z.string().min(2).optional(),
  positionDescription: z.string().min(10).or(z.literal('')).optional(),
})

export type UserFormData = z.infer<typeof UserFormSchema>
