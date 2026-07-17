import z from 'zod'

export const LAYER_DESCRIPTION_MAX_LENGTH = 150

export const BoundingBoxResponseSchema = z.object({
  minX: z.number(),
  minY: z.number(),
  maxX: z.number(),
  maxY: z.number(),
  crs: z.string(),
})

export type BoundingBox = z.infer<typeof BoundingBoxResponseSchema>

export const BoundingBoxPayloadSchema = z.object({
  minX: z.number(),
  minY: z.number(),
  maxX: z.number(),
  maxY: z.number(),
  crs: z.string(),
})

const LayerBaseSchema = z.object({
  dataSinkId: z.uuid(),
  layerName: z.string(),
  title: z.string(),
  description: z.string().optional(),
  keywords: z.array(z.string()),
  attribute: z.array(z.string()),
  geometryColumnRef: z.string(),
  cqlFilter: z.string().nullable(),
  alternativeStyleIds: z.array(z.uuid()),
  crs: z.string(),
})

export const LayerSchema = LayerBaseSchema.extend({
  id: z.uuid(),
  datasetId: z.uuid(),
  defaultStyleId: z.uuid().nullable(),
  geometryType: z.string(),
  nativeCRS: z.string(),
  bboxAutoCalculate: z.boolean(),
  nativeBoundingBox: BoundingBoxResponseSchema.nullable(),
  latLonBoundingBox: BoundingBoxResponseSchema.nullable(),
  createdAt: z.string(),
  modifiedAt: z.string(),
})

const boundingBoxCoord = z
  .string()
  .trim()
  .refine(v => v !== '', { message: 'common.errors.required' })
  .refine(v => Number.isFinite(Number(v)), { message: 'common.errors.invalidNumber' })

export const BoundingBoxStrictSchema = z.object({
  minX: boundingBoxCoord,
  minY: boundingBoxCoord,
  maxX: boundingBoxCoord,
  maxY: boundingBoxCoord,
  crs: z.string(),
})

const BoundingBoxFormFieldsSchema = z.object({
  minX: z.string(),
  minY: z.string(),
  maxX: z.string(),
  maxY: z.string(),
  crs: z.string(),
})

export const LayerFormSchema = z
  .object({
    id: z.string(),
    title: z.string().trim().min(1, 'common.errors.required'),
    layerName: z
      .string()
      .trim()
      .min(1, 'common.errors.required')
      .regex(/^[A-Za-z0-9_-]*$/, 'common.errors.invalidCharacters')
      .regex(/^[^0-9]/, 'common.errors.mustNotStartWithNumber'),
    description: z
      .string()
      .trim()
      .max(LAYER_DESCRIPTION_MAX_LENGTH, 'datasets.overview.completion.apis.config.errors.description.tooLong')
      .optional()
      .or(z.literal('')),
    keywords: z.array(z.string()),
    dataSinkId: z.string().min(1, 'common.errors.required'),
    attribute: z.array(z.string()).min(1, 'common.errors.required'),
    cqlFilter: z.string().trim(),
    geometryColumnRef: z.string().min(1, 'common.errors.required'),
    nativeCRS: z.string().min(1, 'common.errors.required'),
    crs: z.string().min(1, 'common.errors.required'),
    bboxAutoCalculate: z.boolean(),
    nativeBoundingBox: BoundingBoxFormFieldsSchema,
    latLonBoundingBox: BoundingBoxFormFieldsSchema,
    defaultStyleId: z.string().nullable(),
    alternativeStyleIds: z.array(z.string()),
  })
  .superRefine((data, ctx) => {
    if (data.bboxAutoCalculate) return
    const result = BoundingBoxStrictSchema.safeParse(data.nativeBoundingBox)
    if (!result.success) {
      result.error.issues.forEach(issue => {
        ctx.addIssue({ code: 'custom', message: issue.message, path: ['nativeBoundingBox', ...issue.path] })
      })
    }
  })

export const LayerPayloadSchema = LayerBaseSchema.extend({
  defaultStyleId: z.uuid().optional().nullable(),
  bboxAutoCalculate: z.boolean(),
  nativeBoundingBox: BoundingBoxPayloadSchema.nullable(),
  latLonBoundingBox: BoundingBoxPayloadSchema.nullable(),
})

export type Layer = z.infer<typeof LayerSchema>
export type LayerFormData = z.infer<typeof LayerFormSchema>
export type LayerApiPayload = z.infer<typeof LayerPayloadSchema>

export type CreateLayerInput = {
  datasetId: string
  data: LayerApiPayload
}

export type UpdateLayerInput = {
  datasetId: string
  layerId: string
  data: LayerApiPayload
}
