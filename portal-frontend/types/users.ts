import { z } from 'zod'

const TitleSchema = z.enum(['male', 'female'])

export type TitleSchemaType = z.infer<typeof TitleSchema>
