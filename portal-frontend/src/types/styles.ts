import z from 'zod'

export const StyleSchema = z.object({
  id: z.uuid(),
  datasetId: z.uuid(),
  name: z.string(),
  sldContent: z.string(),
  inUse: z.boolean(),
  createdAt: z.string(),
  modifiedAt: z.string(),
})

export type Style = z.infer<typeof StyleSchema>

export const StyleInputSchema = z.object({
  name: z.string().trim().min(1, 'common.errors.required'),
  sldContent: z
    .string()
    .min(1, 'common.errors.required')
    .regex(/^[A-Za-z0-9_-]+$/, 'common.errors.invalidCharacters'),
})

export type StyleInput = z.infer<typeof StyleInputSchema>

export const StyleFormSchema = StyleInputSchema.extend({
  id: z.string(),
})

export type StyleFormData = z.infer<typeof StyleFormSchema>

export type UpdateStyleInput = {
  datasetId: string
  stilId: string
  style: StyleInput
}

export type DeleteStyleInput = {
  datasetId: string
  stilId: string
}
