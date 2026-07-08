import type { Edge, Node } from '@xyflow/react'

import type { TransformNodeData } from '@/components/node-editor/types'

import type { MappingConfig, SchemaTree, ValueNode } from './_types'
import { ARRAY_EDGE_STYLE, isOpNode } from './_types'
import type { MegaNodeData } from './nodes/MegaNode'
import { flattenTree } from './schema/fieldTree'
import { concatInputPorts, LITERAL_DEFAULT_TYPE, literalOutputPort, mappingRegistry } from './transforms'

export const SOURCE_NODE_ID = 'source'
export const TARGET_NODE_ID = 'target'

const SOURCE_POS = { x: 0, y: 0 }
const TARGET_POS = { x: 760, y: 0 }
const AUTO_X = 380

const makeEdgeId = (source: string, sourceHandle: string, target: string, targetHandle: string): string =>
  `${source}:${sourceHandle}->${target}:${targetHandle}`

// ---------------------------------------------------------------------------
// Compile: canvas → mapping config fields + positions
// ---------------------------------------------------------------------------

export const compileCanvas = (nodes: Node[], edges: Edge[]): Pick<MappingConfig, 'fields' | 'positions'> => {
  const fields: Record<string, ValueNode> = {}
  const positions: MappingConfig['positions'] = {}
  const byId = new Map(nodes.map(n => [n.id, n]))

  const incoming = (nodeId: string, handleId: string) =>
    edges.find(e => e.target === nodeId && (e.targetHandle ?? '') === handleId)

  const resolve = (nodeId: string, handleId: string, derivedId: string): ValueNode => {
    if (nodeId === SOURCE_NODE_ID) return handleId
    const node = byId.get(nodeId)
    if (!node) throw new Error(`Compilation error: node ${nodeId} not found in canvas`)
    const data = node.data as TransformNodeData
    const def = mappingRegistry.byType[data.defType]
    if (!def) throw new Error(`Compilation error: transform type ${data.defType} not found in registry`)
    positions[derivedId] = node.position
    const ports = data.inputs ?? def.inputs
    const inputs: ValueNode[] = []
    // Keep an empty slot for each unconnected port so fixed-arity transforms
    // (e.g. geoPoint lon/lat) stay positionally aligned with their value tree.
    ports.forEach((port, index) => {
      const edge = incoming(nodeId, port.id)
      if (!edge) {
        inputs.push('')
        return
      }
      const child = byId.get(edge.source)
      const childType = edge.source !== SOURCE_NODE_ID && child ? (child.data as TransformNodeData).defType : null
      const childDerived = childType ? `${derivedId}.${index}#${childType}` : `${derivedId}.${index}`
      inputs.push(resolve(edge.source, edge.sourceHandle ?? '', childDerived))
    })
    // Each def's toValueNode decides how to treat empty slots: positional ops (geoPoint)
    // keep them, variadic ops (concat) drop them.
    return def.toValueNode(inputs, data.config ?? {})
  }

  for (const edge of edges) {
    if (edge.target !== TARGET_NODE_ID || !edge.targetHandle) continue

    const targetPath = edge.targetHandle
    const src = byId.get(edge.source)
    const rootType = edge.source !== SOURCE_NODE_ID && src ? (src.data as TransformNodeData).defType : null
    const rootDerived = rootType ? `${targetPath}#${rootType}` : targetPath
    fields[targetPath] = resolve(edge.source, edge.sourceHandle ?? '', rootDerived)
  }

  return { fields, positions }
}

/**
 * Transform nodes whose output does not (transitively) reach the target node.
 * `compileCanvas` only serializes nodes reachable backwards from the target, so
 * these would be silently dropped on save — the editor warns about them on exit.
 */
export const findUnconnectedTransformNodes = (nodes: Node[], edges: Edge[]): Node[] => {
  const reachable = new Set<string>()
  const queue = edges.filter(e => e.target === TARGET_NODE_ID).map(e => e.source)
  while (queue.length) {
    const id = queue.shift()!
    if (id === SOURCE_NODE_ID || reachable.has(id)) continue
    reachable.add(id)
    for (const e of edges) if (e.target === id) queue.push(e.source)
  }
  return nodes.filter(n => n.type === 'transform' && !reachable.has(n.id))
}

// ---------------------------------------------------------------------------
// Decompile: mapping config → canvas (MegaNodes + transform nodes + edges)
// ---------------------------------------------------------------------------

const megaNode = (
  id: string,
  role: MegaNodeData['role'],
  tree: SchemaTree,
  position: { x: number; y: number },
): Node => ({
  id,
  type: 'mega',
  position,
  deletable: false,
  data: { role, schemaName: tree.name, fields: tree.fields } as MegaNodeData,
})

export const decompileConfig = (
  config: MappingConfig,
  source: SchemaTree,
  target: SchemaTree,
): { nodes: Node[]; edges: Edge[] } => {
  const sourceFields = flattenTree(source)
  const nodes: Node[] = [
    megaNode(SOURCE_NODE_ID, 'source', source, SOURCE_POS),
    megaNode(TARGET_NODE_ID, 'target', target, TARGET_POS),
  ]
  const edges: Edge[] = []
  let autoRow = 0
  const positionFor = (id: string) => config.positions[id] ?? { x: AUTO_X, y: 40 + autoRow++ * 90 }

  const addEdge = (srcNode: string, srcHandle: string, tgtNode: string, tgtHandle: string, isArray: boolean) => {
    edges.push({
      id: makeEdgeId(srcNode, srcHandle, tgtNode, tgtHandle),
      source: srcNode,
      sourceHandle: srcHandle,
      target: tgtNode,
      targetHandle: tgtHandle,
      ...(isArray ? { style: ARRAY_EDGE_STYLE } : {}),
    })
  }

  // The config stores one value tree per target field, so a transform node feeding
  // multiple target ports appears as identical trees. Cache materialized op nodes by
  // their canonical shape to restore a single shared node with multiple outgoing edges.
  const nodeCache = new Map<string, { nodeId: string; handleId: string; isArray: boolean }>()

  const materialize = (vn: ValueNode, derivedId: string): { nodeId: string; handleId: string; isArray: boolean } => {
    if (typeof vn === 'string') {
      return { nodeId: SOURCE_NODE_ID, handleId: vn, isArray: sourceFields.get(vn)?.portType === 'array' }
    }
    if (vn.op === 'copy') {
      return {
        nodeId: SOURCE_NODE_ID,
        handleId: vn.sourcePath,
        isArray: sourceFields.get(vn.sourcePath)?.portType === 'array',
      }
    }

    const cacheKey = JSON.stringify(vn)
    const cached = nodeCache.get(cacheKey)
    if (cached) return cached

    const def = mappingRegistry.byType[vn.op]

    const childVns = def?.opInputs(vn) ?? []
    // concat keeps a spare trailing port so users can add inputs without replacing wires
    const inputs = vn.op === 'concat' ? concatInputPorts(childVns.length + 1) : (def?.inputs ?? [])
    // For literal (const) nodes: restore the typed output port from the saved valueType (UML name)
    const outputs = vn.op === 'const' ? [literalOutputPort(vn.valueType ?? LITERAL_DEFAULT_TYPE)] : (def?.outputs ?? [])

    nodes.push({
      id: derivedId,
      type: 'transform',
      position: positionFor(derivedId),
      data: { defType: vn.op, config: def?.opConfig(vn) ?? {}, inputs, outputs } as TransformNodeData,
    })

    childVns.forEach((child, i) => {
      // Empty-string input means the port was left unconnected; don't materialize
      if (child === '') return
      const childType = isOpNode(child) && child.op !== 'copy' ? child.op : null
      const childDerived = childType ? `${derivedId}.${i}#${childType}` : `${derivedId}.${i}`
      const endpoint = materialize(child, childDerived)
      addEdge(endpoint.nodeId, endpoint.handleId, derivedId, inputs[i]?.id ?? 'in', endpoint.isArray)
    })

    const endpoint = { nodeId: derivedId, handleId: outputs[0]?.id ?? 'out', isArray: outputs[0]?.type === 'array' }
    nodeCache.set(cacheKey, endpoint)
    return endpoint
  }

  for (const [targetPath, vn] of Object.entries(config.fields)) {
    const rootType = isOpNode(vn) && vn.op !== 'copy' ? vn.op : null
    const rootDerived = rootType ? `${targetPath}#${rootType}` : targetPath
    const endpoint = materialize(vn, rootDerived)
    addEdge(endpoint.nodeId, endpoint.handleId, TARGET_NODE_ID, targetPath, endpoint.isArray)
  }

  return { nodes, edges }
}
