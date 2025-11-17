import { z } from 'zod'

export type DatasetCreationProgress = {
  metadata: boolean
  groups: boolean
  dataConfiguration: boolean
  distribution: boolean
  permissions: boolean
  publication: boolean
}

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

export type DatasetOverviewData = DatasetFormData & {
  creationProgress: DatasetCreationProgress
}
