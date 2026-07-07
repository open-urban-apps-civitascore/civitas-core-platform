import { describe, expect, it } from 'vitest'

import legacyFlatRoot from './__fixtures__/datastructure-model/legacy-flat-root.json'
import multiRoot from './__fixtures__/datastructure-model/multi-root.json'
import wrapperRoot from './__fixtures__/datastructure-model/wrapper-root.json'
import { modelToSchemaTree } from './modelAdapter'

/**
 * Pins the TS model walker against the shared DataStructure-model contract fixtures. The same JSON
 * documents live in config-adapter-api's test resources (fixtures/datastructure-model/), where
 * `DataStructureSchemaContractTest` asserts the Java resolution — both sides must keep agreeing on
 * how a persisted model resolves, since the editor's field tree and the engine's record shape are
 * two readings of one artifact. When one side's fixture changes, change the other side identically.
 */
describe('datastructure-model contract fixtures', () => {
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
    const root = tree.fields[0]
    expect(root).toMatchObject({ path: '$', name: 'MultiRoot', type: 'object' })
    expect(root.children?.map(f => f.path)).toEqual(['$.building', '$.street'])
    expect(root.children?.[0].children).toEqual([
      { path: '$.building.floors', name: 'floors', type: 'int', portType: 'scalar', required: true },
    ])
    expect(root.children?.[1].children).toEqual([
      { path: '$.street.name', name: 'name', type: 'str', portType: 'scalar' },
    ])
  })

  it('legacy-flat-root: a root carrying its own properties is the record', () => {
    const tree = modelToSchemaTree(legacyFlatRoot as Record<string, unknown>, 'fallback')

    expect(tree.name).toBe('Legacy')
    expect(tree.fields[0]).toMatchObject({ path: '$', name: 'Legacy', required: true })
    expect(tree.fields[0].children).toEqual([
      { path: '$.attribut', name: 'attribut', type: 'str', portType: 'scalar', required: true },
    ])
  })
})
