// Generated from https://civitasconnect.digital/core/mapping/v1 — run `node generate.mjs` to regenerate
import { z } from "zod";

// ── $defs ──────────────────────────────────────────────────────────────────

/** Versioned CORE URN of a JSON Schema Element. */
export const ElementUrnSchema = z.string().regex(/^urn:core:[^:]+:[^:]+:element:[^:]+:[^:]+:[^:]+$/, "Must be a versioned CORE Element URN");

/** Copies a value from an input field path. */
export const CopyFieldOperationSchema = z.object({
  op: z.literal("copy"),
  /** JSONPath expression, e.g. '$.sensorId'. */
  input: z.string(),
}).strict();

/** Concatenates multiple input field values with an optional separator. */
export const ConcatFieldOperationSchema = z.object({
  op: z.literal("concat"),
  /** JSONPath expressions whose values are concatenated in order. */
  inputs: z.array(z.string()).min(1),
  separator: z.string().optional(),
}).strict();

/** Writes a constant literal value into the target field regardless of source data. */
export const ConstFieldOperationSchema = z.object({
  op: z.literal("const"),
  /** The constant value. Can be a string, number, or boolean. */
  value: z.unknown(),
}).strict();

/** Converts the input value to its string representation. */
export const ToStringFieldOperationSchema = z.object({
  op: z.literal("toString"),
  /** JSONPath expression of the value to convert. */
  input: z.string(),
}).strict();

/** Parses the input value as an integer. */
export const ToIntFieldOperationSchema = z.object({
  op: z.literal("toInt"),
  /** JSONPath expression of the value to convert. */
  input: z.string(),
}).strict();

/** Parses the input value as a floating-point number. */
export const ToFloatFieldOperationSchema = z.object({
  op: z.literal("toFloat"),
  /** JSONPath expression of the value to convert. */
  input: z.string(),
}).strict();

/** Parses the input string into a date/time value using a date pattern. */
export const ToDateFieldOperationSchema = z.object({
  op: z.literal("toDate"),
  /** JSONPath expression of the string value to parse. */
  input: z.string(),
  /** Date pattern used to parse the input, e.g. 'yyyy-MM-dd'. */
  pattern: z.string(),
}).strict();

/** Formats the input date/time value into a string using a date pattern. */
export const FormatFieldOperationSchema = z.object({
  op: z.literal("format"),
  /** JSONPath expression of the date/time value to format. */
  input: z.string(),
  /** Date pattern used to format the output, e.g. 'yyyy-MM-dd'. */
  pattern: z.string(),
}).strict();

/** A single field mapping rule. */
export const MappingOperationSchema = z.discriminatedUnion("op", [
  CopyFieldOperationSchema,
  ConcatFieldOperationSchema,
  ConstFieldOperationSchema,
  ToStringFieldOperationSchema,
  ToIntFieldOperationSchema,
  ToFloatFieldOperationSchema,
  ToDateFieldOperationSchema,
  FormatFieldOperationSchema
]);

/** A field mapping — either a shorthand input path string or a full operation object. */
export const MappingFieldSchema = z.union([z.string(), MappingOperationSchema]);

// ── Root schema ────────────────────────────────────────────────────────────

/** A declarative field-to-field mapping between two CORE Elements. Each exported Mapping document carries '$schema': 'https://civitasconnect.digital/core/mapping/v1' so it is self-describing. */
export const MappingSchema = z.object({
  $schema: z.enum(["https://civitasconnect.digital/core/mapping/v1", "https://civitasconnect.digital/core-dataset/v1#/$defs/Mapping"]),
  /** Versioned CORE URN of this Mapping. */
  id: z.string().regex(/^urn:core:[^:]+:[^:]+:mapping:[^:]+:[^:]+:[^:]+$/, "Must be a versioned CORE Mapping URN"),
  /** Human-readable display name. */
  title: z.string().optional(),
  description: z.string().optional(),
  /**
   * Versioned CORE URN of the source Element (the schema being read from).
   * @coreRef { type: "urn:core:type:Element" }
   */
  source: ElementUrnSchema,
  /**
   * Versioned CORE URN of the target Element (the schema being written to).
   * @coreRef { type: "urn:core:type:Element" }
   */
  target: ElementUrnSchema,
  /** Map of target field paths to their field operations. */
  fields: z.record(z.string(), MappingFieldSchema),
});

// ── Inferred TypeScript types ──────────────────────────────────────────────

export type ElementUrn = z.infer<typeof ElementUrnSchema>;
export type CopyFieldOperation = z.infer<typeof CopyFieldOperationSchema>;
export type ConcatFieldOperation = z.infer<typeof ConcatFieldOperationSchema>;
export type ConstFieldOperation = z.infer<typeof ConstFieldOperationSchema>;
export type ToStringFieldOperation = z.infer<typeof ToStringFieldOperationSchema>;
export type ToIntFieldOperation = z.infer<typeof ToIntFieldOperationSchema>;
export type ToFloatFieldOperation = z.infer<typeof ToFloatFieldOperationSchema>;
export type ToDateFieldOperation = z.infer<typeof ToDateFieldOperationSchema>;
export type FormatFieldOperation = z.infer<typeof FormatFieldOperationSchema>;
export type MappingOperation = z.infer<typeof MappingOperationSchema>;
export type MappingField = z.infer<typeof MappingFieldSchema>;
export type Mapping = z.infer<typeof MappingSchema>;
