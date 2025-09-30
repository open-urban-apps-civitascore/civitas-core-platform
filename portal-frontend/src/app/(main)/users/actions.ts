import { E164Number, parsePhoneNumberFromString } from 'libphonenumber-js'
import z from 'zod'

export const TitleSchema = z.enum(['male', 'female'])
const PhoneSchema = z.string().transform((value, ctx) => {
  const phoneNumber = parsePhoneNumberFromString(value, 'DE')
  if (!phoneNumber || !phoneNumber.isValid()) {
    ctx.addIssue({
      code: 'custom',
      message: 'Ungültige Telefonnummer',
    })
    return z.NEVER
  }
  return phoneNumber.number as E164Number
})

export const UserFormSchema = z.object({
  id: z.string().optional(),
  firstName: z.string().min(2, {
    message: 'First name must be at least 2 characters.',
  }),
  
  title: TitleSchema,
  lastName: z.string().min(2, {
    message: 'Name must be at least 2 characters.',
  }),
  email: z.email({
    message: 'Please enter a valid email address.',
  }),
  authority: z.string().optional(),
  department: z.string().optional(),
  group: z
    .string()
    .nullable(),
  phone: PhoneSchema.optional(),
  isActive: z.boolean().default(true),
})

export type UserFormData = z.infer<typeof UserFormSchema>

export const saveUser = async (data: UserFormData) => {
  const parsed = UserFormSchema.parse(data)
  console.log('User gespeichert:', parsed)
}
