import type { Edge, Node } from '@xyflow/react'

import type { PortStatus } from '@/components/node-editor'
import type { PortType, TransformNodeData } from '@/components/node-editor/types'

import type { FieldNode } from './_types'
import { findUnconnectedTransformNodes, SOURCE_NODE_ID, TARGET_NODE_ID } from './compile'
import { resolveCoveredLeaf } from './schema/fieldTree'
import { mappingRegistry } from './transforms'

export interface MappingCounts {
  mapped: number
  unmapped: number
  errors: number
}

export interface MappingStatus {
  sourcePortStatus: Record<string, PortStatus>
  targetPortStatus: Record<string, PortStatus>
  /** nodeId → portId → status, for transform nodes only. */
  transformPortStatus: Record<string, Record<string, PortStatus>>
  invalidEdgeIds: string[]
  counts: MappingCounts
}

export interface PortInfo {
  type: PortType
  sub?: string
}

export type EndpointInfo = (nodeId: string, handleId: string) => PortInfo | null

/**
 * Two ports are compatible when:
 *  - both have the same portType category (scalar / geometry / array / object)
 *  - AND for scalar/geometry ports: the subtype matches exactly (int↔int, str↔str, Point↔Point, …)
 *    — type conversions must go through an explicit conversion node.
 * An absent `sub` on either side is a wildcard, which is how conversion nodes accept any scalar.
 */
export const portsCompatible = (from: PortInfo | null, to: PortInfo | null): boolean => {
  if (!from || !to) return false
  if (from.type !== to.type) return false
  if ((from.type === 'scalar' || from.type === 'geometry') && from.sub && to.sub && from.sub !== to.sub) return false
  return true
}

/**
 * Edges whose two endpoints resolve but whose types no longer match — e.g. after a literal's
 * type was changed while it was already connected. An endpoint that cannot be resolved is left
 * alone: a saved path the schema no longer carries is a different problem and must not block saving.
 */
export const findInvalidEdges = (edges: Edge[], endpointInfo: EndpointInfo): Edge[] =>
  edges.filter(edge => {
    const from = endpointInfo(edge.source, edge.sourceHandle ?? '')
    const to = endpointInfo(edge.target, edge.targetHandle ?? '')
    return !!from && !!to && !portsCompatible(from, to)
  })

/** Transform nodes that don't reach the target — compileCanvas drops these on save. */
const droppedNodeIds = (nodes: Node[], edges: Edge[]): Set<string> =>
  new Set(findUnconnectedTransformNodes(nodes, edges).map(n => n.id))

export interface NodeConfigErrors {
  /** nodeId → fieldKey → i18n key, for every wired transform node. */
  byNode: Record<string, Record<string, string>>
  /** How many of those reach the target and therefore block saving. */
  blockingCount: number
}

/**
 * Config errors of the transform nodes the user has wired up. A node is checked once it has an
 * outgoing edge, so the error shows the moment the connection is drawn. Only nodes that reach
 * the target count as blocking: compileCanvas drops the rest, and the unconnected-transform
 * warning already covers them.
 */
export const findNodeConfigErrors = (nodes: Node[], edges: Edge[]): NodeConfigErrors => {
  const wired = new Set(edges.map(e => e.source))
  const dropped = droppedNodeIds(nodes, edges)
  const byNode: Record<string, Record<string, string>> = {}
  let blockingCount = 0

  for (const node of nodes) {
    if (node.type !== 'transform' || !wired.has(node.id)) continue
    const data = node.data as TransformNodeData
    const fieldErrors = mappingRegistry.byType[data.defType]?.validate?.(data.config)
    if (!fieldErrors) continue
    byNode[node.id] = fieldErrors
    if (!dropped.has(node.id)) blockingCount++
  }

  return { byNode, blockingCount }
}

/** True when `path` is a descendant of `ancestor` in the JSONPath hierarchy. */
const isDescendantOf = (path: string, ancestor: string): boolean =>
  path !== ancestor && path.startsWith(ancestor) && (path[ancestor.length] === '.' || path[ancestor.length] === '[')

/**
 * Finds the connected ancestor object that covers `path` (the longest connected
 * target prefix), if any.
 */
const findCoveringAncestor = (path: string, connectedTargets: string[]): string | undefined =>
  connectedTargets.filter(ancestor => isDescendantOf(path, ancestor)).sort((a, b) => b.length - a.length)[0]

/** Strips the last JSONPath segment, e.g. "$.a.b[].c" → "$.a.b[]". */
const parentPath = (path: string): string => path.replace(/(\.[^.[]+|\[\])$/, '')

/** Builds the chain of field names from a covering ancestor down to a leaf. */
const relativeNameChain = (ancestorPath: string, leafPath: string, targetFields: Map<string, FieldNode>): string[] => {
  const names: string[] = []
  let current = leafPath
  while (current !== ancestorPath && current.length > 0) {
    const field = targetFields.get(current)
    if (!field) break
    names.unshift(field.name)
    const next = parentPath(current)
    if (next === current) break
    current = next
  }
  return names
}

/** Per-port state (§12) plus toolbar counts derived from the current nodes and edges. */
export const computeStatus = (
  nodes: Node[],
  edges: Edge[],
  sourceFields: Map<string, FieldNode>,
  targetFields: Map<string, FieldNode>,
  endpointInfo: EndpointInfo,
): MappingStatus => {
  const sourcePortStatus: Record<string, PortStatus> = {}
  const targetPortStatus: Record<string, PortStatus> = {}
  const transformPortStatus: Record<string, Record<string, PortStatus>> = {}

  const invalidEdges = findInvalidEdges(edges, endpointInfo)
  // Both ends of a broken edge go red so the cause (e.g. the retyped literal) is visible too.
  // Mega-node ports are covered by source/targetPortStatus instead.
  const markMismatch = (nodeId: string, handleId: string) => {
    if (nodeId === SOURCE_NODE_ID || nodeId === TARGET_NODE_ID) return
    transformPortStatus[nodeId] = { ...transformPortStatus[nodeId], [handleId]: 'mismatch' }
  }
  invalidEdges.forEach(edge => {
    markMismatch(edge.source, edge.sourceHandle ?? '')
    markMismatch(edge.target, edge.targetHandle ?? '')
  })

  const consumed = new Set(edges.filter(e => e.source === SOURCE_NODE_ID).map(e => e.sourceHandle ?? ''))
  sourceFields.forEach((_field, path) => {
    sourcePortStatus[path] = consumed.has(path) ? 'consumed' : 'unused'
  })

  // Connected target paths and, for direct source→target edges, the source field they map to.
  const targetEdges = edges.filter(e => e.target === TARGET_NODE_ID)
  const connectedTargets = targetEdges.map(e => e.targetHandle ?? '')
  const sourcePathByTarget = new Map(
    targetEdges.filter(e => e.source === SOURCE_NODE_ID).map(e => [e.targetHandle ?? '', e.sourceHandle ?? '']),
  )

  let mapped = 0
  let unmapped = 0
  // Only edges that end up in the compiled mapping block saving; the rest are dropped on save
  // and merely show red. The direct-edge branch below must not count them again.
  const dropped = droppedNodeIds(nodes, edges)
  let errors = invalidEdges.filter(e => !dropped.has(e.target)).length

  targetFields.forEach((field, path) => {
    const edge = targetEdges.find(e => (e.targetHandle ?? '') === path)
    const isLeaf = !field.children?.length

    // Directly connected target port.
    if (edge) {
      const src = endpointInfo(edge.source, edge.sourceHandle ?? '')
      const isMismatch = !!src && !portsCompatible(src, { type: field.portType, sub: field.type })

      targetPortStatus[path] = isMismatch ? 'mismatch' : 'mapped'

      if (isLeaf && !isMismatch) mapped++
      return
    }

    // Covered by a connected ancestor object: only auto-map when an exact-name,
    // matching-type source counterpart exists. Otherwise the field stays unmapped.
    const ancestorPath = findCoveringAncestor(path, connectedTargets)
    const ancestorSourcePath = ancestorPath ? sourcePathByTarget.get(ancestorPath) : undefined
    const ancestorSource = ancestorSourcePath ? sourceFields.get(ancestorSourcePath) : undefined

    if (ancestorPath && ancestorSource) {
      const names = relativeNameChain(ancestorPath, path, targetFields)
      const counterpart = resolveCoveredLeaf(ancestorSource, names)
      // Geometries are first-class types (Point, Polygon, …), so exact-type equality
      // already enforces Point↔Point and rejects Point↔Polygon.
      if (counterpart && counterpart.type === field.type) {
        targetPortStatus[path] = 'mapped'
        if (isLeaf) mapped++
        return
      }
      if (counterpart) {
        // Same-name field exists but the type differs.
        targetPortStatus[path] = 'mismatch'
        if (isLeaf) errors++
        return
      }
    }

    targetPortStatus[path] = 'unused'
    if (isLeaf) unmapped++
  })

  return {
    sourcePortStatus,
    targetPortStatus,
    transformPortStatus,
    invalidEdgeIds: invalidEdges.map(e => e.id),
    counts: { mapped, unmapped, errors },
  }
}
