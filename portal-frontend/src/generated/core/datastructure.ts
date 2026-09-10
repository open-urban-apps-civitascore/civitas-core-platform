// Generated from https://civitasconnect.digital/core-datastructure/v1 — do not edit. Run `npm run generate:core-types` to regenerate.
import { z } from 'zod'

// ── Root schema ────────────────────────────────────────────────────────────

/** A CORE DataStructure is a JSON-Schema '$defs' library of member Elements, identified by its ':datastructure:' '$id' URN. Each '$defs' entry is EITHER an inline member Element schema carrying its own Element CORE URN as '$id' (the canonical, split-ready form the editor emits — Model Forge splits it into a separate Element artifact under that URN, without minting) OR a bare '$ref' to an already-stored member Element URN (the form Model Forge persists after splitting, and the form used to reference existing Elements). A raw imported JSON Schema whose members carry no '$id' is also accepted (Model Forge then mints the URNs). An optional top-level '$ref' designates one member Element as the root shape. The UML diagram travels under 'x-ui-styles'. */
export const DataStructureSchema = z.object({
  $schema: z.string(),
  /** CORE URN of this DataStructure (logical, or with a trailing :version). */
  $id: z
    .string()
    .regex(
      /^urn:core:[^:]+:[^:]+:datastructure:[^:]+:[^:]+:[^:]+(:[^:]+)?$/,
      'Must be a versioned CORE DataStructure URN',
    ),
  title: z.string().optional(),
  description: z.string().optional(),
  /** The member Elements, keyed by member name. Each value is either a bare Element '$ref' or an inline Element schema (see the DataStructure description). */
  $defs: z
    .record(
      z.string(),
      z.union([
        z
          .object({
            /**
             * CORE URN of the member Element.
             * @coreRef { type: "urn:core:type:Element" }
             */
            $ref: z
              .string()
              .regex(
                /^urn:core:[^:]+:[^:]+:element:[^:]+:[^:]+:[^:]+(:[^:]+)?$/,
                'Must be a versioned CORE Element URN',
              ),
          })
          .strict(),
        z.object({
          /**
           * Element CORE URN this inline member is stored under.
           * @coreRef { type: "urn:core:type:Element" }
           */
          $id: z
            .string()
            .regex(/^urn:core:[^:]+:[^:]+:element:[^:]+:[^:]+:[^:]+(:[^:]+)?$/, 'Must be a versioned CORE Element URN')
            .optional(),
        }),
      ]),
    )
    .optional(),
  /**
   * Optional root shape: the CORE URN of the member Element that is the structure's entry point.
   * @coreRef { type: "urn:core:type:Element" }
   */
  $ref: z
    .string()
    .regex(/^urn:core:[^:]+:[^:]+:element:[^:]+:[^:]+:[^:]+(:[^:]+)?$/, 'Must be a versioned CORE Element URN')
    .optional(),
})

// ── Inferred TypeScript types ──────────────────────────────────────────────

export type DataStructure = z.infer<typeof DataStructureSchema>
