import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

import { describe, expect, it } from 'vitest'

import brokenMissingParent from './__fixtures__/datastructure-model/broken-missing-parent.json'
import legacyDefinitions from './__fixtures__/datastructure-model/legacy-definitions.json'
import legacyFlatRoot from './__fixtures__/datastructure-model/legacy-flat-root.json'
import multiRoot from './__fixtures__/datastructure-model/multi-root.json'
import wrapperRoot from './__fixtures__/datastructure-model/wrapper-root.json'
import { modelToSchemaTree } from './modelAdapter'

/**
 * Pins the TS model walker against the shared DataStructure-model contract fixtures. The same JSON
 * documents live in config-adapter-api's test resources (fixtures/datastructure-model/), where
 * `DataStructureSchemaContractTest` asserts the Java resolution — both sides must keep agreeing on
 * how a persisted model resolves, since the editor's field tree and the engine's record shape are
 * two readings of one artifact. The byte-level hash pins below (duplicated in the Java test) turn
 * silent fixture drift between the two copies into a red test on whichever side lags behind.
 */
const FIXTURE_HASHES: Record<string, string> = {
  'broken-missing-parent.json': '4b8aa70c36ee85b86db5adf34cb9214f4a7d6c14254c8be5217437199f69fa82',
  'legacy-definitions.json': 'dff36ea7e76be8fb31a69b848654a73a844fcefe7b3704f11531287aff14fb85',
  'legacy-flat-root.json': '2503d11def93c1ee4a189cb66d9bd2309cfad9645f009be2badfea910ceb6fbe',
  'multi-root.json': '6c4d22c7f796c069f3e0386beac609f8af28ef895cddd910747c83dacaea0def',
  'wrapper-root.json': '95b6b74650a5a01350f251240ec003e215cfda30c2378aabef7c11a679a3d84d',
}

describe('datastructure-model contract fixtures', () => {
  it.each(Object.entries(FIXTURE_HASHES))('fixture %s matches the pinned contract hash', (name, hash) => {
    const path = join(
      process.cwd(),
      'src/app/(main)/datasets/[datasetId]/data-flow/pipeline-editor/_components/mapping-editor/schema/__fixtures__/datastructure-model',
      name,
    )
    expect(createHash('sha256').update(readFileSync(path)).digest('hex')).toBe(hash)
  })

  it('wrapper-root: resolves through the wrapper and inlines the allOf parent, parents first', () => {
    const tree = modelToSchemaTree(wrapperRoot as Record<string, unknown>, 'fallback')

    expect(tree.name).toBe('WrapperRoot')
    const root = tree.fields[0]
    expect(root).toMatchObject({ path: '$', name: 'Dog', type: 'object', required: true })
    expect(root.children?.map(f => ({ path: f.path, type: f.type, required: f.required ?? false }))).toEqual([
      { path: '$.id', type: 'str', required: true },
      { path: '$.name', type: 'str', required: false },
      { path: '$.breed', type: 'str', required: true },
      { path: '$.home', type: 'Point', required: false },
    ])
  })

  it('multi-root: the record itself is the root and holds one object property per tree', () => {
    const tree = modelToSchemaTree(multiRoot as Record<string, unknown>, 'fallback')

    expect(tree.name).toBe('MultiRoot')
    // The record is the document root itself, so the root properties sit directly in the tree.
    expect(tree.fields.map(f => f.path)).toEqual(['$.building', '$.street'])
    expect(tree.fields[0].children).toEqual([
      { path: '$.building.floors', name: 'floors', type: 'int', portType: 'scalar', required: true },
    ])
    expect(tree.fields[1].children).toEqual([{ path: '$.street.name', name: 'name', type: 'str', portType: 'scalar' }])
  })

  it('legacy-flat-root: a root carrying its own properties is the record', () => {
    const tree = modelToSchemaTree(legacyFlatRoot as Record<string, unknown>, 'fallback')

    expect(tree.name).toBe('Legacy')
    expect(tree.fields).toEqual([
      { path: '$.attribut', name: 'attribut', type: 'str', portType: 'scalar', required: true },
    ])
  })

  it('legacy-definitions: $defs shadows the legacy definitions section on key collision', () => {
    const tree = modelToSchemaTree(legacyDefinitions as Record<string, unknown>, 'fallback')

    expect(tree.fields[0]).toMatchObject({ path: '$', name: 'Reading' })
    expect(tree.fields[0].children?.map(f => ({ name: f.name, type: f.type, required: f.required ?? false }))).toEqual([
      { name: 'shadowed', type: 'str', required: true },
      { name: 'current', type: 'float', required: false },
    ])
  })

  it('broken-missing-parent: a dangling inheritance parent is rejected, not silently skipped', () => {
    expect(() => modelToSchemaTree(brokenMissingParent as Record<string, unknown>, 'fallback')).toThrow(/Gone/)
  })
})
