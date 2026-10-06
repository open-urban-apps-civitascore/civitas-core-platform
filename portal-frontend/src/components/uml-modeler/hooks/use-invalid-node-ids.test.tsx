import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { cls, enm } from '@/test-support/datastructureFixtures'

import type { UMLNode } from '../types/diagram'
import { InvalidNodeIdsProvider, useInvalidNodeIds } from './use-invalid-node-ids'

let invalidNodeIds: ReadonlySet<string> | undefined

const Probe = () => {
  invalidNodeIds = useInvalidNodeIds()
  return null
}

const tree = (nodes: UMLNode[]) => (
  <InvalidNodeIdsProvider nodes={nodes}>
    <Probe />
  </InvalidNodeIdsProvider>
)

const duplicateNodes = () => [cls('c1', 'Sensor', []), cls('c2', 'Sensor', [])] as unknown as UMLNode[]

describe('useInvalidNodeIds', () => {
  it('returns the ids of the nodes with an invalid name', () => {
    render(tree(duplicateNodes()))

    expect(invalidNodeIds).toEqual(new Set(['node-c1', 'node-c2']))
  })

  it('keeps the same set when only a position changes', () => {
    const nodes = duplicateNodes()
    const { rerender } = render(tree(nodes))
    const firstSet = invalidNodeIds

    rerender(tree(nodes.map(node => ({ ...node, position: { x: 100, y: 50 } }))))

    expect(invalidNodeIds).toBe(firstSet)
  })

  it('updates the invalid node ids after a class is renamed', () => {
    const nodes = duplicateNodes()
    const { rerender } = render(tree(nodes))

    rerender(tree([nodes[0], cls('c2', 'Station', []) as unknown as UMLNode]))

    expect(invalidNodeIds).toEqual(new Set())
  })

  it('updates the invalid node ids after an attribute is renamed', () => {
    const attributeName = 'value'
    const node = cls('c1', 'Sensor', [
      { id: 'a1', name: attributeName },
      { id: 'a2', name: attributeName },
    ]) as unknown as UMLNode
    const { rerender } = render(tree([node]))

    rerender(
      tree([
        cls('c1', 'Sensor', [
          { id: 'a1', name: attributeName },
          { id: 'a2', name: 'level' },
        ]) as unknown as UMLNode,
      ]),
    )

    expect(invalidNodeIds).toEqual(new Set())
  })

  it('updates the invalid node ids after an enumeration value is renamed', () => {
    const valueName = 'ACTIVE'
    const { rerender } = render(tree([enm('e1', 'Status', [valueName, valueName]) as unknown as UMLNode]))

    rerender(tree([enm('e1', 'Status', [valueName, 'INACTIVE']) as unknown as UMLNode]))

    expect(invalidNodeIds).toEqual(new Set())
  })
})
