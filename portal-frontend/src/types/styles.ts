import { z } from 'zod'

export const StyleInputSchema = z.object({
  name: z.string().trim().min(1, 'common.errors.required'),
  sldContent: z.string().min(1, 'common.errors.required'),
})

export type StyleInput = z.infer<typeof StyleInputSchema>

export const StyleSchema = z.object({
  id: z.string(),
  datasetId: z.string(),
  name: z.string(),
  sldContent: z.string(),
  inUse: z.boolean(),
  createdAt: z.string(),
  modifiedAt: z.string(),
})

export type Style = z.infer<typeof StyleSchema>

export type UpdateStyleInput = {
  datasetId: string
  stilId: string
  style: StyleInput
}

export type DeleteStyleInput = {
  datasetId: string
  stilId: string
}
