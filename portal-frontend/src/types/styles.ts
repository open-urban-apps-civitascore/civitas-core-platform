import { z } from 'zod'

export const StilInputSchema = z.object({
  name: z.string().trim().min(1, 'common.errors.required'),
  sldContent: z.string().min(1, 'common.errors.required'),
})

export type StilInput = z.infer<typeof StilInputSchema>

export const StilSchema = z.object({
  id: z.string(),
  datasetId: z.string(),
  name: z.string(),
  sldContent: z.string(),
  inUse: z.boolean(),
  createdAt: z.string(),
  modifiedAt: z.string(),
})

export type Stil = z.infer<typeof StilSchema>
