import { z } from 'zod'


export const DatasetFormSchema = z.object({
  id: z.string(),
  dataspace: z.string(),
  title: z.string().min(2, {
    message: 'common.errors.atLeast2',
  }),
  description: z.string(),
  tags: z.array(z.string()),
})

export type DatasetFormData = z.infer<typeof DatasetFormSchema>
