/**
 * Drift guard: the modules in this directory are a generated translation of the CORE-IR contracts
 * in model-forge-runtime. Nothing enforces regeneration, so hand edits and schema changes both go
 * unnoticed — and both have happened (see tickets/2108). Regenerating and diffing turns that into a
 * red test.
 *
 * The committed files are prettier-formatted by the npm script, the generator's raw output is not,
 * so the fresh code is formatted the same way before comparing.
 */

import { readFileSync } from 'node:fs'
import { join } from 'node:path'

import { format, resolveConfig } from 'prettier'
import { describe, expect, it } from 'vitest'

import { generateCoreTypes, SCHEMAS } from '../../../scripts/generate-core-types.mjs'

const OUT_DIR = join(process.cwd(), 'src/generated/core')

const formatted = async (code: string, filePath: string) => {
  const config = await resolveConfig(filePath)
  return format(code, { ...config, filepath: filePath })
}

describe('generated CORE types match their source contracts', () => {
  const generated = generateCoreTypes() as Record<string, string>

  it('generates a module for every declared schema', () => {
    expect(Object.keys(generated).sort()).toEqual((SCHEMAS as { out: string }[]).map(schema => schema.out).sort())
  })

  it.each(['datastructure.ts', 'mapping.ts', 'pipeline.ts', 'datasource.ts', 'datasink.ts'])(
    '%s is unmodified generator output',
    async out => {
      const filePath = join(OUT_DIR, out)
      expect(await formatted(generated[out], filePath)).toBe(readFileSync(filePath, 'utf8'))
    },
  )
})
