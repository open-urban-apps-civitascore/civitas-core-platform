import { z } from 'zod'
export interface DataSpace {
  id: string
  name: string
  description: string
  protected: boolean
}

export type DataSpaceFormData = Omit<DataSpace, 'id'>

export const dataSpaceSchema = z.object({
  name: z.string().min(2, 'Name must be at least 2 characters.').max(50, 'Name must be at most 50 characters.'),
  description: z
    .string()
    .min(10, 'Description must be at least 10 characters.')
    .max(500, 'Description must be at most 500 characters.'),
  protected: z.boolean(),
})
