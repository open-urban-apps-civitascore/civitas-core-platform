import { CheckedState } from '@radix-ui/react-checkbox'
import { JSX } from 'react'
import { z } from 'zod'

import { Item2, WithId } from './common'

export const DATASET_STATUS = {
  DRAFT: 'draft',
  READY: 'ready',
  PUBLISHED: 'published',
} as const

export type DatasetStatus = (typeof DATASET_STATUS)[keyof typeof DATASET_STATUS]

export type Contact = { id: string; firstName: string; lastName: string }
export type Distribution = {
  format: string
  title: string
  url: string
}

export type Catalog = {
  id: string
  [`dct:title`]: string
  [`dct:description`]: string
  [`dct:publisherId`]: string
  [`dcat:datasetIds`]: string[]
  [`dct:issued`]: string
  [`dct:modified`]: string
}

export type Dataset = {
  id: string
  name: string
  description: string
  contact: Contact | null
  issued: string
  lastUpdated: string
  access: boolean
  status: DatasetStatus
  dataspace: Item2 | null
}

export type CreateDatasetData = Omit<Dataset, 'id'>
export type UpdateDatasetData = Dataset
export type PatchDatasetData = Partial<Dataset> & WithId

export type DatasetTableData = {
  id: string
  name: string
  dataspace: Item2 | null
  contact: Contact | null
  lastUpdated: string
  access: boolean
  status: DatasetStatus
}

export const DatasetFormSchema = z.object({
  id: z.string(),
  dataspace: z.string(),
  name: z.string().min(2, {
    message: 'common.errors.atLeast2',
  }),
  description: z.string(),
})

export type DatasetFormData = z.infer<typeof DatasetFormSchema>

export type CompletionStepParam =
  | 'metadata'
  | 'accessPermissions'
  | 'data'
  | 'distribution'
  | 'usagePermissions'
  | 'applications'
  | 'publication'

export type CompletionStepData = {
  title: string
  isCompleted: CheckedState
  buttons: { text: string; routeParam: CompletionStepParam; queryParam?: string }[]
  content?: JSX.Element
}
