import type { Edge, Node } from '@xyflow/react'
import { describe, expect, it } from 'vitest'

import type { MappingConfig, SchemaTree } from './_types'
import { compileCanvas, decompileConfig, SOURCE_NODE_ID, TARGET_NODE_ID } from './compile'
import { concatInputPorts, mappingRegistry } from './transforms'

const sourceTree: SchemaTree = {
  name: 'src',
  fields: [
    { path: '$.id', name: 'id', type: 'int', portType: 'scalar' },
    { path: '$.suffix', name: 'suffix', type: 'str', portType: 'scalar' },
    { path: '$.name', name: 'name', type: 'str', portType: 'scalar' },
  ],
}

const targetTree: SchemaTree = {
  name: 'tgt',
  fields: [
    { path: '$.title', name: 'title', type: 'str', portType: 'scalar' },
    { path: '$.fullCode', name: 'fullCode', type: 'str', portType: 'scalar' },
  ],
}

const intToStr = mappingRegistry.byType.intToStr
const concat = mappingRegistry.byType.concat

const nodes: Node[] = [
  { id: SOURCE_NODE_ID, type: 'mega', position: { x: 0, y: 0 }, data: { role: 'source', fields: sourceTree.fields } },
  { id: TARGET_NODE_ID, type: 'mega', position: { x: 700, y: 0 }, data: { role: 'target', fields: targetTree.fields } },
  {
    id: 'i',
    type: 'transform',
    position: { x: 300, y: 40 },
    data: { defType: 'intToStr', config: {}, inputs: intToStr.inputs, outputs: intToStr.outputs },
  },
  {
    id: 'c',
    type: 'transform',
    position: { x: 520, y: 80 },
    data: { defType: 'concat', config: { separator: '-' }, inputs: concatInputPorts(2), outputs: concat.outputs },
  },
]

const edges: Edge[] = [
  { id: 'e1', source: SOURCE_NODE_ID, sourceHandle: '$.name', target: TARGET_NODE_ID, targetHandle: '$.title' },
  { id: 'e2', source: SOURCE_NODE_ID, sourceHandle: '$.id', target: 'i', targetHandle: 'in' },
  { id: 'e3', source: 'i', sourceHandle: 'out', target: 'c', targetHandle: 'in0' },
  { id: 'e4', source: SOURCE_NODE_ID, sourceHandle: '$.suffix', target: 'c', targetHandle: 'in1' },
  { id: 'e5', source: 'c', sourceHandle: 'out', target: TARGET_NODE_ID, targetHandle: '$.fullCode' },
]

describe('mapping editor compile', () => {
  it('compiles a direct copy and a chained concat(intToStr)', () => {
    const { fields, positions } = compileCanvas(nodes, edges)
    expect(fields['$.title']).toBe('$.name')
    expect(fields['$.fullCode']).toEqual({
      op: 'concat',
      separator: '-',
      inputs: [{ op: 'intToStr', input: '$.id' }, '$.suffix'],
    })
    expect(Object.keys(positions).sort()).toEqual(['$.fullCode#concat', '$.fullCode#concat.0#intToStr'])
  })

  it('round-trips: compile → decompile → compile is stable', () => {
    const compiled = compileCanvas(nodes, edges)
    const config: MappingConfig = {
      sourceDatastructureId: 'a',
      sourceVersionId: 'b',
      targetDatastructureId: 'c',
      targetVersionId: 'd',
      ...compiled,
    }
    const built = decompileConfig(config, sourceTree, targetTree)
    const recompiled = compileCanvas(built.nodes, built.edges)
    expect(recompiled.fields).toEqual(compiled.fields)
    expect(Object.keys(recompiled.positions).sort()).toEqual(Object.keys(compiled.positions).sort())
  })
})
