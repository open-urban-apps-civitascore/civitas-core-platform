import { CheckedState } from '@radix-ui/react-checkbox'
import { JSX } from 'react'
import { z } from 'zod'

import { Item } from './common'

export type Status = 'open' | 'closed' | null
export type Creator = { id: string; firstName: string; lastName: string }
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

export type DatasetResponse = {
  id: string
  name: string
  description: string
  creator: Creator[]
  issued: string
  lastUpdated: string
  status: Status
  distribution: (Distribution & { id: string }) | null
  dataspace: Item | null
  department: Item | null
  tags: string[]
}

export type DatasetTableData = {
  id: string
  name: string
  dataspace: string
  department: string
  creator: string[]
  lastUpdated: string
  status: Status
  releaseProcess: null
  distribution: Distribution | null
}

export const DatasetFormSchema = z.object({
  id: z.string(),
  dataspace: z.string(),
  name: z.string().min(2, {
    message: 'common.errors.atLeast2',
  }),
  description: z.string(),
  tags: z.array(z.string()),
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
