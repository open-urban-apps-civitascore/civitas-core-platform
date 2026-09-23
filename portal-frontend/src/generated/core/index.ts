/**
 * CORE Model-Forge zod schemas + types, vendored from `@civitasconnect/core-model-forge-types`.
 *
 * The frontend emits clean, URN-native CORE documents (Pipeline, Mapping, DataSource, DataSink) and
 * validates its own payloads against these schemas before sending them to the (schema-agnostic)
 * backend. Because the frontend omits the backend-stamped `$schema`/`id` fields, the `*Draft`
 * variants below are the ones used for outbound validation.
 */
import { z } from 'zod'

export * from './pipeline'
export * from './mapping'
export * from './datasource'
export * from './datasink'
export * from './datastructure'

import { MappingSchema } from './mapping'
import { PipelineSchema } from './pipeline'
import { MqttDataSourceSchema, SqlDataSourceSchema } from './datasource'
import { FrostDataSinkSchema, PostgisDataSinkSchema } from './datasink'

/**
 * Pipeline document as produced by the editor: no `$schema`/`id` (Model Forge stamps those on
 * ingest). Validate the outbound pipeline `model` against this.
 */
export const PipelineDraftSchema = PipelineSchema.omit({ $schema: true, id: true })

/**
 * Mapping document as produced by the mapping editor: no `$schema`/`id` (Model Forge stamps those).
 * Validate the outbound mapping body against this.
 */
export const MappingDraftSchema = MappingSchema.omit({ $schema: true, id: true })

/**
 * DataSource / DataSink configuration documents as produced by the editor: no `$schema`/`id` (Model
 * Forge stamps those on ingest). Both CORE schemas are `discriminatedUnion`s keyed by
 * `connectionType`, so the draft is rebuilt by omitting the envelope fields from each variant.
 */
export const DataSourceDraftSchema = z.discriminatedUnion('connectionType', [
  MqttDataSourceSchema.omit({ $schema: true, id: true }),
  SqlDataSourceSchema.omit({ $schema: true, id: true }),
])

export const DataSinkDraftSchema = z.discriminatedUnion('connectionType', [
  FrostDataSinkSchema.omit({ $schema: true, id: true }),
  PostgisDataSinkSchema.omit({ $schema: true, id: true }),
])
