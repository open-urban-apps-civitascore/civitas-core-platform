import type { Edge, Node } from '@xyflow/react'
import { describe, expect, it } from 'vitest'

import type { FieldNode } from './_types'
import { SOURCE_NODE_ID, TARGET_NODE_ID } from './compile'
import type { EndpointInfo, PortInfo } from './status'
import { computeStatus, findInvalidEdges, findNodeConfigErrors, portsCompatible } from './status'

const field = (path: string, type: FieldNode['type'], portType: FieldNode['portType']): [string, FieldNode] => [
  path,
  { path, name: path.slice(2), type, portType },
]

const sourceFields = new Map<string, FieldNode>([field('$.name', 'str', 'scalar')])
const targetFields = new Map<string, FieldNode>([
  field('$.title', 'str', 'scalar'),
  field('$.count', 'int', 'scalar'),
  field('$.geom', 'Point', 'geometry'),
])

/** Resolves mega-node handles from the trees above; transform ports come from `ports`. */
const endpointInfoFor = (ports: Record<string, PortInfo>): EndpointInfo =>
  function resolve(nodeId, handleId) {
    if (nodeId === SOURCE_NODE_ID) {
      const f = sourceFields.get(handleId)
      return f ? { type: f.portType, sub: f.type } : null
    }
    if (nodeId === TARGET_NODE_ID) {
      const f = targetFields.get(handleId)
      return f ? { type: f.portType, sub: f.type } : null
    }
    return ports[`${nodeId}.${handleId}`] ?? null
  }

const edge = (id: string, source: string, sourceHandle: string, target: string, targetHandle: string): Edge => ({
  id,
  source,
  sourceHandle,
  target,
  targetHandle,
})

describe('portsCompatible', () => {
  it('accepts equal category and subtype', () => {
    expect(portsCompatible({ type: 'scalar', sub: 'int' }, { type: 'scalar', sub: 'int' })).toBe(true)
    expect(portsCompatible({ type: 'geometry', sub: 'Point' }, { type: 'geometry', sub: 'Point' })).toBe(true)
  })

  it('rejects a differing subtype within the same category', () => {
    expect(portsCompatible({ type: 'scalar', sub: 'int' }, { type: 'scalar', sub: 'str' })).toBe(false)
    expect(portsCompatible({ type: 'geometry', sub: 'Point' }, { type: 'geometry', sub: 'Polygon' })).toBe(false)
  })

  it('treats an absent subtype as a wildcard, as conversion nodes rely on', () => {
    expect(portsCompatible({ type: 'scalar', sub: 'int' }, { type: 'scalar' })).toBe(true)
    expect(portsCompatible({ type: 'scalar' }, { type: 'scalar', sub: 'date' })).toBe(true)
  })

  it('rejects a differing category and unresolved endpoints', () => {
    expect(portsCompatible({ type: 'scalar', sub: 'str' }, { type: 'geometry', sub: 'Point' })).toBe(false)
    expect(portsCompatible({ type: 'object' }, { type: 'array' })).toBe(false)
    expect(portsCompatible(null, { type: 'scalar', sub: 'str' })).toBe(false)
    expect(portsCompatible({ type: 'scalar', sub: 'str' }, null)).toBe(false)
  })
})

describe('findInvalidEdges', () => {
  const endpointInfo = endpointInfoFor({
    'literal.out': { type: 'scalar', sub: 'int' },
    'point.out': { type: 'geometry', sub: 'Point' },
    'toString.in': { type: 'scalar' },
  })

  it('reports an edge whose endpoints no longer match', () => {
    const edges = [edge('e1', 'literal', 'out', TARGET_NODE_ID, '$.title')]
    expect(findInvalidEdges(edges, endpointInfo).map(e => e.id)).toEqual(['e1'])
  })

  it('accepts matching endpoints and wildcard inputs', () => {
    const edges = [
      edge('e1', 'literal', 'out', TARGET_NODE_ID, '$.count'),
      edge('e2', 'point', 'out', TARGET_NODE_ID, '$.geom'),
      edge('e3', 'literal', 'out', 'toString', 'in'),
    ]
    expect(findInvalidEdges(edges, endpointInfo)).toEqual([])
  })

  it('leaves an edge alone when an endpoint cannot be resolved', () => {
    const edges = [edge('e1', 'literal', 'out', TARGET_NODE_ID, '$.gone')]
    expect(findInvalidEdges(edges, endpointInfo)).toEqual([])
  })
})

describe('findNodeConfigErrors', () => {
  const literal = (id: string, value: string): Node => ({
    id,
    type: 'transform',
    position: { x: 0, y: 0 },
    data: { defType: 'const', config: { type: 'String', value } },
  })
  const conv: Node = {
    id: 'conv',
    type: 'transform',
    position: { x: 0, y: 0 },
    data: { defType: 'toString', config: {} },
  }
  const toTarget = (id: string, source: string) => edge(id, source, 'out', TARGET_NODE_ID, '$.title')
  const required = { value: 'transforms.literal.fields.value.required' }

  it('reports a literal wired into a transform that does not reach the target yet', () => {
    const edges = [edge('e1', 'lit', 'out', 'conv', 'in')]
    expect(findNodeConfigErrors([literal('lit', ''), conv], edges)).toEqual({
      byNode: { lit: required },
      blockingCount: 0,
    })
  })

  it('counts a literal that reaches the target through a transform as blocking', () => {
    const edges = [edge('e1', 'lit', 'out', 'conv', 'in'), toTarget('e2', 'conv')]
    expect(findNodeConfigErrors([literal('lit', ''), conv], edges)).toEqual({
      byNode: { lit: required },
      blockingCount: 1,
    })
  })

  it('counts a literal wired straight into the target as blocking', () => {
    expect(findNodeConfigErrors([literal('lit', '')], [toTarget('e1', 'lit')])).toEqual({
      byNode: { lit: required },
      blockingCount: 1,
    })
  })

  it('treats a whitespace-only value as empty', () => {
    expect(findNodeConfigErrors([literal('lit', '   ')], [toTarget('e1', 'lit')])).toEqual({
      byNode: { lit: required },
      blockingCount: 1,
    })
  })

  it('accepts a connected literal that carries a value', () => {
    expect(findNodeConfigErrors([literal('lit', 'x')], [toTarget('e1', 'lit')])).toEqual({
      byNode: {},
      blockingCount: 0,
    })
  })

  it('ignores a literal that is not wired to anything', () => {
    expect(findNodeConfigErrors([literal('lit', '')], [])).toEqual({ byNode: {}, blockingCount: 0 })
  })

  it('ignores nodes whose def has no validate hook', () => {
    expect(findNodeConfigErrors([conv], [toTarget('e1', 'conv')])).toEqual({ byNode: {}, blockingCount: 0 })
  })
})

describe('computeStatus', () => {
  const endpointInfo = endpointInfoFor({
    'literal.out': { type: 'scalar', sub: 'int' },
    'concat.in0': { type: 'scalar', sub: 'str' },
    'concat.out': { type: 'scalar', sub: 'str' },
  })
  const transform = (id: string, defType: string): Node => ({
    id,
    type: 'transform',
    position: { x: 0, y: 0 },
    data: { defType, config: {} },
  })
  const nodes = [transform('literal', 'const'), transform('concat', 'concat')]

  it('reddens a broken edge into a transform input without counting it as an error', () => {
    const edges = [edge('e1', 'literal', 'out', 'concat', 'in0')]
    const status = computeStatus(nodes, edges, sourceFields, targetFields, endpointInfo)

    // concat never reaches the target, so compileCanvas drops the chain — red, but not blocking.
    expect(status.counts.errors).toBe(0)
    expect(status.invalidEdgeIds).toEqual(['e1'])
    expect(status.transformPortStatus).toEqual({
      literal: { out: 'mismatch' },
      concat: { in0: 'mismatch' },
    })
  })

  it('counts the same broken edge once its chain reaches the target', () => {
    const edges = [
      edge('e1', 'literal', 'out', 'concat', 'in0'),
      edge('e2', 'concat', 'out', TARGET_NODE_ID, '$.title'),
    ]
    const status = computeStatus(nodes, edges, sourceFields, targetFields, endpointInfo)

    expect(status.counts.errors).toBe(1)
    expect(status.invalidEdgeIds).toEqual(['e1'])
  })

  it('counts a broken edge into the target and keeps mega ports out of transformPortStatus', () => {
    const edges = [edge('e1', 'literal', 'out', TARGET_NODE_ID, '$.title')]
    const status = computeStatus(nodes, edges, sourceFields, targetFields, endpointInfo)

    expect(status.counts.errors).toBe(1)
    expect(status.targetPortStatus['$.title']).toBe('mismatch')
    expect(status.transformPortStatus).toEqual({ literal: { out: 'mismatch' } })
    expect(status.counts.mapped).toBe(0)
  })

  it('reports no errors for a matching mapping', () => {
    const edges = [edge('e1', SOURCE_NODE_ID, '$.name', TARGET_NODE_ID, '$.title')]
    const status = computeStatus(nodes, edges, sourceFields, targetFields, endpointInfo)

    expect(status.counts).toEqual({ mapped: 1, unmapped: 2, errors: 0 })
    expect(status.invalidEdgeIds).toEqual([])
    expect(status.transformPortStatus).toEqual({})
    expect(status.sourcePortStatus['$.name']).toBe('consumed')
    expect(status.targetPortStatus['$.title']).toBe('mapped')
  })
})
