import { z } from 'zod'

import { DataSinkTypeEnum } from './datasinks'
import { API_STANDARDS } from './namedApis'

export const DataSinkRemovalImpactSchema = z.object({
  dataSinks: z.array(z.object({ id: z.string(), dataSinkType: DataSinkTypeEnum, provisioned: z.boolean() })),
  layers: z.array(z.object({ id: z.string(), layerName: z.string(), title: z.string().nullable() })),
  apis: z.array(
    z.object({
      id: z.string(),
      slug: z.string(),
      name: z.string(),
      standard: z.enum([API_STANDARDS.OWS, API_STANDARDS.STA, API_STANDARDS.CUSTOM]),
    }),
  ),
  confirmationIds: z.array(z.string()),
  requiresConfirmation: z.boolean(),
})

export type DataSinkRemovalImpact = z.infer<typeof DataSinkRemovalImpactSchema>

export type RemovalImpactQuery = {
  deletedPipelineIds?: string[]
  removedLinks?: { pipelineId: string; dataSinkId: string }[]
}
