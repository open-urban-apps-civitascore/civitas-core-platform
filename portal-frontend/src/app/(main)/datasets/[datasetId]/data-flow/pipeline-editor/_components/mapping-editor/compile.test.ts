import type { Edge, Node } from '@xyflow/react'
import { describe, expect, it } from 'vitest'

import type { MappingConfig, SchemaTree } from './_types'
import {
  compileCanvas,
  decompileConfig,
  findUnconnectedTransformNodes,
  SOURCE_NODE_ID,
  TARGET_NODE_ID,
} from './compile'
import { concatInputPorts, mappingRegistry } from './transforms'

const sourceTree: SchemaTree = {
  name: 'src',
  fields: [
    { path: '$.id', name: 'id', type: 'int', portType: 'scalar' },
    { path: '$.suffix', name: 'suffix', type: 'str', portType: 'scalar' },
    { path: '$.name', name: 'name', type: 'str', portType: 'scalar' },
    { path: '$.longitude', name: 'longitude', type: 'number', portType: 'scalar' },
    { path: '$.latitude', name: 'latitude', type: 'number', portType: 'scalar' },
  ],
}

const targetTree: SchemaTree = {
  name: 'tgt',
  fields: [
    { path: '$.title', name: 'title', type: 'str', portType: 'scalar' },
    { path: '$.fullCode', name: 'fullCode', type: 'str', portType: 'scalar' },
    { path: '$.geometry_column', name: 'geometry_column', type: 'Point', portType: 'geometry' },
  ],
}

const toString = mappingRegistry.byType.toString
const concat = mappingRegistry.byType.concat

const nodes: Node[] = [
  { id: SOURCE_NODE_ID, type: 'mega', position: { x: 0, y: 0 }, data: { role: 'source', fields: sourceTree.fields } },
  { id: TARGET_NODE_ID, type: 'mega', position: { x: 700, y: 0 }, data: { role: 'target', fields: targetTree.fields } },
  {
    id: 'i',
    type: 'transform',
    position: { x: 300, y: 40 },
    data: { defType: 'toString', config: {}, inputs: toString.inputs, outputs: toString.outputs },
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
  it('compiles a direct copy and a chained concat(toString)', () => {
    const { fields, positions } = compileCanvas(nodes, edges)
    expect(fields['$.title']).toBe('$.name')
    expect(fields['$.fullCode']).toEqual({
      op: 'concat',
      separator: '-',
      inputs: [{ op: 'toString', input: '$.id' }, '$.suffix'],
    })
    expect(Object.keys(positions).sort()).toEqual(['$.fullCode#concat', '$.fullCode#concat.0#toString'])
  })

  it('compiles a geoPoint node from longitude + latitude', () => {
    const geoPointDef = mappingRegistry.byType.geoPoint

    const geoNodes: Node[] = [
      {
        id: SOURCE_NODE_ID,
        type: 'mega',
        position: { x: 0, y: 0 },
        data: { role: 'source', fields: sourceTree.fields },
      },
      {
        id: TARGET_NODE_ID,
        type: 'mega',
        position: { x: 700, y: 0 },
        data: { role: 'target', fields: targetTree.fields },
      },
      {
        id: 'gp',
        type: 'transform',
        position: { x: 350, y: 100 },
        data: { defType: 'geoPoint', config: {}, inputs: geoPointDef.inputs, outputs: geoPointDef.outputs },
      },
    ]

    const geoEdges: Edge[] = [
      { id: 'e1', source: SOURCE_NODE_ID, sourceHandle: '$.longitude', target: 'gp', targetHandle: 'lon' },
      { id: 'e2', source: SOURCE_NODE_ID, sourceHandle: '$.latitude', target: 'gp', targetHandle: 'lat' },
      { id: 'e3', source: 'gp', sourceHandle: 'out', target: TARGET_NODE_ID, targetHandle: '$.geometry_column' },
    ]

    const { fields } = compileCanvas(geoNodes, geoEdges)
    expect(fields['$.geometry_column']).toEqual({
      op: 'geoPoint',
      lon: '$.longitude',
      lat: '$.latitude',
    })
  })

  it('keeps geoPoint inputs positionally aligned when only latitude is wired', () => {
    const geoPointDef = mappingRegistry.byType.geoPoint

    const geoNodes: Node[] = [
      {
        id: SOURCE_NODE_ID,
        type: 'mega',
        position: { x: 0, y: 0 },
        data: { role: 'source', fields: sourceTree.fields },
      },
      {
        id: TARGET_NODE_ID,
        type: 'mega',
        position: { x: 700, y: 0 },
        data: { role: 'target', fields: targetTree.fields },
      },
      {
        id: 'gp',
        type: 'transform',
        position: { x: 350, y: 100 },
        data: { defType: 'geoPoint', config: {}, inputs: geoPointDef.inputs, outputs: geoPointDef.outputs },
      },
    ]

    // Only the latitude port is connected; longitude is left empty.
    const geoEdges: Edge[] = [
      { id: 'e2', source: SOURCE_NODE_ID, sourceHandle: '$.latitude', target: 'gp', targetHandle: 'lat' },
      { id: 'e3', source: 'gp', sourceHandle: 'out', target: TARGET_NODE_ID, targetHandle: '$.geometry_column' },
    ]

    const { fields } = compileCanvas(geoNodes, geoEdges)
    expect(fields['$.geometry_column']).toEqual({ op: 'geoPoint', lon: '', lat: '$.latitude' })

    // Reopening the editor must keep the wire on the latitude port, not longitude.
    const config: MappingConfig = {
      $schema: 'https://civitasconnect.digital/core/mapping/v1',
      source: 'urn:core:datastructure:a:b',
      target: 'urn:core:datastructure:c:d',
      fields,
      positions: {},
    }
    const built = decompileConfig(config, sourceTree, targetTree)
    const gpEdge = built.edges.find(e => e.source === SOURCE_NODE_ID && e.sourceHandle === '$.latitude')
    expect(gpEdge?.targetHandle).toBe('lat')
  })

  it('keeps geoPoint inputs positionally aligned when only longitude is wired', () => {
    const geoPointDef = mappingRegistry.byType.geoPoint

    const geoNodes: Node[] = [
      {
        id: SOURCE_NODE_ID,
        type: 'mega',
        position: { x: 0, y: 0 },
        data: { role: 'source', fields: sourceTree.fields },
      },
      {
        id: TARGET_NODE_ID,
        type: 'mega',
        position: { x: 700, y: 0 },
        data: { role: 'target', fields: targetTree.fields },
      },
      {
        id: 'gp',
        type: 'transform',
        position: { x: 350, y: 100 },
        data: { defType: 'geoPoint', config: {}, inputs: geoPointDef.inputs, outputs: geoPointDef.outputs },
      },
    ]

    const geoEdges: Edge[] = [
      { id: 'e1', source: SOURCE_NODE_ID, sourceHandle: '$.longitude', target: 'gp', targetHandle: 'lon' },
      { id: 'e3', source: 'gp', sourceHandle: 'out', target: TARGET_NODE_ID, targetHandle: '$.geometry_column' },
    ]

    const { fields } = compileCanvas(geoNodes, geoEdges)
    expect(fields['$.geometry_column']).toEqual({ op: 'geoPoint', lon: '$.longitude', lat: '' })
  })

  it('drops an unconnected interior concat port instead of persisting an empty input', () => {
    // in0 and in2 are wired, in1 is left empty — the compiled inputs must be compacted so the
    // backend never receives a bare '' (which it rejects as a blank copy source path).
    const concatDef = mappingRegistry.byType.concat

    const cNodes: Node[] = [
      {
        id: SOURCE_NODE_ID,
        type: 'mega',
        position: { x: 0, y: 0 },
        data: { role: 'source', fields: sourceTree.fields },
      },
      {
        id: TARGET_NODE_ID,
        type: 'mega',
        position: { x: 700, y: 0 },
        data: { role: 'target', fields: targetTree.fields },
      },
      {
        id: 'c',
        type: 'transform',
        position: { x: 350, y: 100 },
        data: { defType: 'concat', config: {}, inputs: concatInputPorts(3), outputs: concatDef.outputs },
      },
    ]

    const cEdges: Edge[] = [
      { id: 'e1', source: SOURCE_NODE_ID, sourceHandle: '$.name', target: 'c', targetHandle: 'in0' },
      { id: 'e2', source: SOURCE_NODE_ID, sourceHandle: '$.suffix', target: 'c', targetHandle: 'in2' },
      { id: 'e3', source: 'c', sourceHandle: 'out', target: TARGET_NODE_ID, targetHandle: '$.fullCode' },
    ]

    const { fields } = compileCanvas(cNodes, cEdges)
    expect(fields['$.fullCode']).toEqual({ op: 'concat', inputs: ['$.name', '$.suffix'] })
  })

  it('does not wire an unconnected conversion input to the source node', () => {
    const config: MappingConfig = {
      $schema: 'https://civitasconnect.digital/core/mapping/v1',
      source: 'urn:core:datastructure:a:b',
      target: 'urn:core:datastructure:c:d',
      fields: { '$.title': { op: 'toString', input: '' } },
      positions: {},
    }
    const built = decompileConfig(config, sourceTree, targetTree)
    expect(built.edges.some(e => e.source === SOURCE_NODE_ID)).toBe(false)
    expect(built.edges).toHaveLength(1)
    expect(built.edges[0]).toMatchObject({ target: TARGET_NODE_ID, targetHandle: '$.title' })
  })

  it('restores a single shared node when one transform feeds multiple target ports', () => {
    const config: MappingConfig = {
      $schema: 'https://civitasconnect.digital/core/mapping/v1',
      source: 'urn:core:datastructure:a:b',
      target: 'urn:core:datastructure:c:d',
      fields: {
        '$.title': { op: 'const', value: 'x', valueType: 'String' },
        '$.fullCode': { op: 'const', value: 'x', valueType: 'String' },
      },
      positions: {},
    }
    const built = decompileConfig(config, sourceTree, targetTree)
    const transformNodes = built.nodes.filter(n => n.type === 'transform')
    expect(transformNodes).toHaveLength(1)
    const targetEdges = built.edges.filter(e => e.target === TARGET_NODE_ID)
    expect(targetEdges).toHaveLength(2)
    expect(targetEdges.every(e => e.source === transformNodes[0].id)).toBe(true)
    expect(targetEdges.map(e => e.targetHandle).sort()).toEqual(['$.fullCode', '$.title'])
  })

  it('flags a transform whose output is not wired to the target', () => {
    // 'i' (toString) only has its input connected; 'c' (concat) reaches the target.
    const partialEdges: Edge[] = [
      { id: 'e2', source: SOURCE_NODE_ID, sourceHandle: '$.id', target: 'i', targetHandle: 'in' },
      { id: 'e4', source: SOURCE_NODE_ID, sourceHandle: '$.suffix', target: 'c', targetHandle: 'in1' },
      { id: 'e5', source: 'c', sourceHandle: 'out', target: TARGET_NODE_ID, targetHandle: '$.fullCode' },
    ]
    const unconnected = findUnconnectedTransformNodes(nodes, partialEdges)
    expect(unconnected.map(n => n.id)).toEqual(['i'])
  })

  it('flags nothing when every transform reaches the target', () => {
    expect(findUnconnectedTransformNodes(nodes, edges)).toHaveLength(0)
  })

  it('round-trips: compile → decompile → compile is stable', () => {
    const compiled = compileCanvas(nodes, edges)

    const config: MappingConfig = {
      $schema: 'https://civitasconnect.digital/core/mapping/v1',
      source: 'urn:core:datastructure:a:b',
      target: 'urn:core:datastructure:c:d',
      ...compiled,
    }
    const built = decompileConfig(config, sourceTree, targetTree)
    const recompiled = compileCanvas(built.nodes, built.edges)
    expect(recompiled.fields).toEqual(compiled.fields)
    expect(Object.keys(recompiled.positions).sort()).toEqual(Object.keys(compiled.positions).sort())
  })
})
