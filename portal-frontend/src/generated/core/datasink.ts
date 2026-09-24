// Generated from https://civitasconnect.digital/core/datasink/v1 — do not edit. Run `npm run generate:core-types` to regenerate.
import { z } from 'zod'

// ── $defs ──────────────────────────────────────────────────────────────────

/** Writes to an OGC SensorThings API / FROST-Server. A passthrough sink may carry no configuration; otherwise it references the mapping's Thing-shaped target structure by URN. */
export const FrostDataSinkSchema = z
  .object({
    $schema: z.enum([
      'https://civitasconnect.digital/core/datasink/v1',
      'https://civitasconnect.digital/core-dataset/v1#/$defs/DataSink',
    ]),
    /** Versioned CORE URN of this DataSink. */
    id: z
      .string()
      .regex(/^urn:core:[^:]+:[^:]+:datasink:[^:]+:[^:]+:[^:]+(:[^:]+)?$/, 'Must be a versioned CORE DataSink URN'),
    /** Human-readable display name. */
    title: z.string().optional(),
    description: z.string().optional(),
    /** OGC SensorThings / FROST-Server target. */
    connectionType: z.literal('frost'),
    /**
     * Versioned CORE URN of the DataStructure describing the Thing-shaped target structure.
     * @coreRef { type: "urn:core:type:DataStructure" }
     */
    element: z
      .string()
      .regex(
        /^urn:core:[^:]+:[^:]+:(datastructure|element):[^:]+:[^:]+:[^:]+(:[^:]+)?$/,
        'Must be a versioned CORE DataStructure URN',
      )
      .optional(),
  })
  .strict()

/** Writes rows into a PostGIS table. */
export const PostgisDataSinkSchema = z
  .object({
    $schema: z.enum([
      'https://civitasconnect.digital/core/datasink/v1',
      'https://civitasconnect.digital/core-dataset/v1#/$defs/DataSink',
    ]),
    /** Versioned CORE URN of this DataSink. */
    id: z
      .string()
      .regex(/^urn:core:[^:]+:[^:]+:datasink:[^:]+:[^:]+:[^:]+(:[^:]+)?$/, 'Must be a versioned CORE DataSink URN'),
    /** Human-readable display name. */
    title: z.string().optional(),
    description: z.string().optional(),
    /** PostGIS table target. */
    connectionType: z.literal('postgis'),
    /** Target PostGIS table name. */
    tableName: z.string(),
    /**
     * Versioned CORE URN of the DataStructure describing the row format written to the table.
     * @coreRef { type: "urn:core:type:DataStructure" }
     */
    element: z
      .string()
      .regex(
        /^urn:core:[^:]+:[^:]+:(datastructure|element):[^:]+:[^:]+:[^:]+(:[^:]+)?$/,
        'Must be a versioned CORE DataStructure URN',
      ),
  })
  .strict()

// ── Root schema ────────────────────────────────────────────────────────────

/** Configuration for a data sink that receives transformed data from a CORE pipeline. The target is chosen by 'connectionType' (frost | postgis); each variant declares exactly the fields the pipeline engine (config-adapter → NiFi) reads. 'element' references, by versioned CORE URN, the DataStructure describing the output row/entity format — Model Forge records it as a datasink-element dependency edge. */
export const DataSinkSchema = z.discriminatedUnion('connectionType', [FrostDataSinkSchema, PostgisDataSinkSchema])

// ── Inferred TypeScript types ──────────────────────────────────────────────

export type FrostDataSink = z.infer<typeof FrostDataSinkSchema>
export type PostgisDataSink = z.infer<typeof PostgisDataSinkSchema>
export type DataSink = z.infer<typeof DataSinkSchema>
