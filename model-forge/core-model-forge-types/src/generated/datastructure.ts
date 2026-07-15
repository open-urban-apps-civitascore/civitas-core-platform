// Generated from https://civitasconnect.digital/core-datastructure/v1 — run `node generate.mjs` to regenerate
import { z } from "zod";

// ── Root schema ────────────────────────────────────────────────────────────

/** A CORE DataStructure: a named, versioned grouping of Elements. A DataStructure is a stored artifact (not a View) — it records which Elements belong together (e.g. all entities of one imported JSON Schema document). It carries no embedded content of its own; its members are referenced by URN and resolved on read. */
export const DataStructureSchema = z.object({
  $schema: z.literal("https://civitasconnect.digital/core-datastructure/v1"),
  /** Versioned CORE URN of this DataStructure. */
  id: z.string().regex(/^urn:core:[^:]+:[^:]+:datastructure:[^:]+:[^:]+:[^:]+$/, "Must be a versioned CORE DataStructure URN"),
  title: z.string().optional(),
  description: z.string().optional(),
  version: z.string().optional(),
  /** URNs of the Elements grouped by this DataStructure. */
  elementRefs: z.array(z.string().regex(/^urn:/, "Must be a CORE URN")).optional(),
});

// ── Inferred TypeScript types ──────────────────────────────────────────────

export type DataStructure = z.infer<typeof DataStructureSchema>;
