// Generated from https://civitasconnect.digital/core/mapping/v1 — do not edit. Run `npm run generate:core-types` to regenerate.
import { z } from 'zod'

// ── $defs ──────────────────────────────────────────────────────────────────

/**
 * A single field mapping rule.
 * MANUAL PATCH: `toUuid`/`toDateTime` are missing from the backend CORE JSON Schema
 * (model-forge-runtime) even though the mapping editor's transform palette already offers both as
 * live, selectable operations — added here so this override is lost on the next
 * `npm run generate:core-types` until that source is fixed.
 */
export const MappingOperationSchema: z.ZodType = z.lazy(() =>
  z.discriminatedUnion('op', [
    CopyFieldOperationSchema,
    ConcatFieldOperationSchema,
    ConstFieldOperationSchema,
    ToStringFieldOperationSchema,
    ToIntFieldOperationSchema,
    ToFloatFieldOperationSchema,
    ToUuidFieldOperationSchema,
    ToDateFieldOperationSchema,
    ToDateTimeFieldOperationSchema,
    FormatFieldOperationSchema,
    GeoPointFieldOperationSchema,
  ]),
)

/** A field mapping — either a shorthand input path string or a full operation object. */
export const MappingFieldSchema: z.ZodType = z.lazy(() => z.union([z.string(), MappingOperationSchema]))

/** Versioned CORE URN of a DataStructure that a Mapping binds (an Element URN is also accepted for backward compatibility). */
export const StructureUrnSchema = z
  .string()
  .regex(
    /^urn:core:[^:]+:[^:]+:(datastructure|element):[^:]+:[^:]+:[^:]+(:[^:]+)?$/,
    'Must be a versioned CORE DataStructure URN',
  )

/** Copies a value from an input field path. The path is given as 'sourcePath' (the mapping editor's field name) or 'input' (legacy alias); at least one must be present. */
export const CopyFieldOperationSchema = z
  .object({
    op: z.literal('copy'),
    /** JSONPath expression, e.g. '$.sensorId'. */
    input: z.string().optional(),
    /** JSONPath expression, e.g. '$.sensorId' (editor field name). */
    sourcePath: z.string().optional(),
  })
  .strict()

/** Writes a constant literal value into the target field regardless of source data. */
export const ConstFieldOperationSchema = z
  .object({
    op: z.literal('const'),
    /** The constant value. Can be a string, number, or boolean. */
    value: z.unknown(),
    /** Optional editor hint for how to interpret 'value' (e.g. string/number/boolean). */
    valueType: z.string().optional(),
  })
  .strict()

/** Converts the input value to its string representation. */
export const ToStringFieldOperationSchema = z
  .object({
    op: z.literal('toString'),
    /** Value to convert — a JSONPath string or a nested operation. */
    input: MappingFieldSchema,
  })
  .strict()

/** Parses the input value as an integer. */
export const ToIntFieldOperationSchema = z
  .object({
    op: z.literal('toInt'),
    /** Value to convert — a JSONPath string or a nested operation. */
    input: MappingFieldSchema,
  })
  .strict()

/** Parses the input value as a floating-point number. */
export const ToFloatFieldOperationSchema = z
  .object({
    op: z.literal('toFloat'),
    /** Value to convert — a JSONPath string or a nested operation. */
    input: MappingFieldSchema,
  })
  .strict()

/** Converts the input value to a UUID. No pattern: the value is passed through as-is. */
export const ToUuidFieldOperationSchema = z
  .object({
    op: z.literal('toUuid'),
    /** Value to convert — a JSONPath string or a nested operation. */
    input: MappingFieldSchema,
  })
  .strict()

/**
 * Parses the input string into a date/time value using a date pattern.
 * `pattern` is optional: the editor node has a default pattern but the user can clear it.
 */
export const ToDateFieldOperationSchema = z
  .object({
    op: z.literal('toDate'),
    /** String value to parse — a JSONPath string or a nested operation. */
    input: MappingFieldSchema,
    /** Date pattern used to parse the input, e.g. 'yyyy-MM-dd'. */
    pattern: z.string().optional(),
  })
  .strict()

/**
 * Parses the input string into a date-time value using a date-time pattern.
 * `pattern` is optional: the editor node has a default pattern but the user can clear it.
 */
export const ToDateTimeFieldOperationSchema = z
  .object({
    op: z.literal('toDateTime'),
    /** String value to parse — a JSONPath string or a nested operation. */
    input: MappingFieldSchema,
    /** Date-time pattern used to parse the input, e.g. 'yyyy-MM-dd\'T\'HH:mm:ssXXX'. */
    pattern: z.string().optional(),
  })
  .strict()

/** Formats the input date/time value into a string using a date pattern. */
export const FormatFieldOperationSchema = z
  .object({
    op: z.literal('format'),
    /** Date/time value to format — a JSONPath string or a nested operation. */
    input: MappingFieldSchema,
    /** Date pattern used to format the output, e.g. 'yyyy-MM-dd'. */
    pattern: z.string(),
  })
  .strict()

/** Builds a GeoJSON-style point from a longitude and latitude input. */
export const GeoPointFieldOperationSchema = z
  .object({
    op: z.literal('geoPoint'),
    /** Longitude — a JSONPath string or a nested operation. */
    lon: MappingFieldSchema,
    /** Latitude — a JSONPath string or a nested operation. */
    lat: MappingFieldSchema,
  })
  .strict()

/**
 * Concatenates multiple input field values with an optional separator.
 * `inputs` may legitimately be empty: the editor's concat node is variadic and drops unconnected
 * ports rather than padding them, so a node with nothing connected yet emits `inputs: []`.
 */
export const ConcatFieldOperationSchema = z
  .object({
    op: z.literal('concat'),
    /** Inputs concatenated in order — each a JSONPath string or a nested operation. */
    inputs: z.array(MappingFieldSchema),
    separator: z.string().optional(),
  })
  .strict()

// ── Root schema ────────────────────────────────────────────────────────────

/** A declarative field-to-field mapping between two CORE DataStructures. Each exported Mapping document carries '$schema': 'https://civitasconnect.digital/core/mapping/v1' so it is self-describing. */
export const MappingSchema = z.object({
  $schema: z.enum([
    'https://civitasconnect.digital/core/mapping/v1',
    'https://civitasconnect.digital/core-dataset/v1#/$defs/Mapping',
  ]),
  /** Versioned CORE URN of this Mapping. */
  id: z
    .string()
    .regex(/^urn:core:[^:]+:[^:]+:mapping:[^:]+:[^:]+:[^:]+(:[^:]+)?$/, 'Must be a versioned CORE Mapping URN'),
  /** Human-readable display name. */
  title: z.string().optional(),
  description: z.string().optional(),
  /**
   * Versioned CORE URN of the source DataStructure (the structure being read from). An Element URN is also accepted for backward compatibility.
   * @coreRef { type: "urn:core:type:DataStructure" }
   */
  source: StructureUrnSchema.optional(),
  /**
   * Versioned CORE URN of the target DataStructure (the structure being written to). An Element URN is also accepted for backward compatibility.
   * @coreRef { type: "urn:core:type:DataStructure" }
   */
  target: StructureUrnSchema.optional(),
  /** Map of target field paths to their field operations. */
  fields: z.record(z.string(), MappingFieldSchema),
})

// ── Inferred TypeScript types ──────────────────────────────────────────────

export type MappingOperation = z.infer<typeof MappingOperationSchema>
export type MappingField = z.infer<typeof MappingFieldSchema>
export type StructureUrn = z.infer<typeof StructureUrnSchema>
export type CopyFieldOperation = z.infer<typeof CopyFieldOperationSchema>
export type ConstFieldOperation = z.infer<typeof ConstFieldOperationSchema>
export type ToStringFieldOperation = z.infer<typeof ToStringFieldOperationSchema>
export type ToIntFieldOperation = z.infer<typeof ToIntFieldOperationSchema>
export type ToFloatFieldOperation = z.infer<typeof ToFloatFieldOperationSchema>
export type ToUuidFieldOperation = z.infer<typeof ToUuidFieldOperationSchema>
export type ToDateFieldOperation = z.infer<typeof ToDateFieldOperationSchema>
export type ToDateTimeFieldOperation = z.infer<typeof ToDateTimeFieldOperationSchema>
export type FormatFieldOperation = z.infer<typeof FormatFieldOperationSchema>
export type GeoPointFieldOperation = z.infer<typeof GeoPointFieldOperationSchema>
export type ConcatFieldOperation = z.infer<typeof ConcatFieldOperationSchema>
export type Mapping = z.infer<typeof MappingSchema>
