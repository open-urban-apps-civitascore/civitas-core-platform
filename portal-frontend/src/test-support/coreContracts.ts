/**
 * Validators for the published CORE-IR contracts — the JSON Schema files in model-forge-runtime
 * that the backend itself validates against.
 *
 * The generated Zod schemas in src/generated/core/ are a translated copy of these files and have
 * drifted from them (see tickets/2108). Asserting an editor artifact against that copy proves only
 * that the copy agrees with itself, so conformance tests must read the originals from here.
 *
 * Ajv is not the engine that rejects in production (model-forge uses networknt), so a green result
 * is contract conformance, not a backend guarantee.
 */

import { readFileSync } from 'node:fs'
import { join } from 'node:path'

import Ajv2020, { type ValidateFunction } from 'ajv/dist/2020'

/** Same directory scripts/generate-core-types.mjs reads; Vitest runs with portal-frontend as cwd. */
const RESOURCES = join(process.cwd(), '../model-forge/model-forge-runtime/src/main/resources')

export const CONTRACT_FILES = {
  datastructure: 'datastructure.schema.json',
  mapping: 'mapping.schema.json',
  pipeline: 'pipeline.schema.json',
  datasource: 'datasource.schema.json',
  datasink: 'datasink.schema.json',
} as const

export type ContractKind = keyof typeof CONTRACT_FILES

export const CONTRACT_URIS: Record<ContractKind, string> = {
  datastructure: 'https://civitasconnect.digital/core-datastructure/v1',
  mapping: 'https://civitasconnect.digital/core/mapping/v1',
  pipeline: 'https://civitasconnect.digital/core/pipeline/v1',
  datasource: 'https://civitasconnect.digital/core/datasource/v1',
  datasink: 'https://civitasconnect.digital/core/datasink/v1',
}

export const readContract = (kind: ContractKind): Record<string, unknown> =>
  JSON.parse(readFileSync(join(RESOURCES, CONTRACT_FILES[kind]), 'utf8'))

// strict:false accepts the x-core-* / x-ui-* extension keywords, like the backend's
// CoreJsonSchemaFactory does via NonValidationKeyword.
const ajv = new Ajv2020({ strict: false, allErrors: true })

const cache = new Map<ContractKind, ValidateFunction>()

export const contractValidator = (kind: ContractKind): ValidateFunction => {
  const cached = cache.get(kind)
  if (cached) return cached
  const validate = ajv.compile(readContract(kind))
  cache.set(kind, validate)
  return validate
}

/**
 * Validates a document against a CORE contract and returns the schema errors, newest Ajv message
 * first. An empty array means conforming — asserting on the array rather than on a boolean keeps a
 * failing test readable.
 */
export const contractErrors = (kind: ContractKind, document: unknown): string[] => {
  const validate = contractValidator(kind)
  if (validate(document)) return []
  return (validate.errors ?? []).map(error => `${error.instancePath || '/'} ${error.message ?? ''}`.trim())
}
