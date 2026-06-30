import { describe, expect, it } from 'vitest'

import type { FieldNode, SchemaTree } from '../_types'

import { requiredFieldPaths } from './fieldTree'

const node = (over: Partial<FieldNode> & { path: string; name: string }): FieldNode => ({
  type: 'scalar',
  portType: 'scalar',
  required: false,
  ...over,
})

const tree = (fields: FieldNode[]): SchemaTree => ({ name: 'root', fields })

describe('requiredFieldPaths', () => {
  it('collects required leaf paths and skips optional ones', () => {
    expect(
      requiredFieldPaths(
        tree([
          node({ path: '$.id', name: 'id', required: true }),
          node({ path: '$.nick', name: 'nick', required: false }),
        ]),
      ),
    ).toEqual(['$.id'])
  })

  it('recurses into a required object, emitting required leaves but not the container path', () => {
    expect(
      requiredFieldPaths(
        tree([
          node({
            path: '$.loc',
            name: 'loc',
            type: 'object',
            portType: 'object',
            required: true,
            children: [
              node({ path: '$.loc.lat', name: 'lat', required: true }),
              node({ path: '$.loc.alt', name: 'alt', required: false }),
            ],
          }),
        ]),
      ),
    ).toEqual(['$.loc.lat'])
  })

  it('does not descend into an optional container even when its children are required', () => {
    // the optional-container short-circuit: an optional parent does not force its children
    expect(
      requiredFieldPaths(
        tree([
          node({
            path: '$.meta',
            name: 'meta',
            type: 'object',
            portType: 'object',
            required: false,
            children: [node({ path: '$.meta.k', name: 'k', required: true })],
          }),
        ]),
      ),
    ).toEqual([])
  })

  it('emits array element leaf paths with the [] segment', () => {
    expect(
      requiredFieldPaths(
        tree([
          node({
            path: '$.readings',
            name: 'readings',
            type: 'array',
            portType: 'array',
            required: true,
            children: [node({ path: '$.readings[].value', name: 'value', required: true })],
          }),
        ]),
      ),
    ).toEqual(['$.readings[].value'])
  })
})
