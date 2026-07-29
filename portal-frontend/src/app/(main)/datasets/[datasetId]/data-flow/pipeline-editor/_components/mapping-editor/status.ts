import type { Edge } from '@xyflow/react'

import type { PortStatus } from '@/components/node-editor'
import type { PortType } from '@/components/node-editor/types'

import type { FieldNode } from './_types'
import { SOURCE_NODE_ID, TARGET_NODE_ID } from './compile'
import { resolveCoveredLeaf } from './schema/fieldTree'

export interface MappingCounts {
  mapped: number
  unmapped: number
  errors: number
}

export interface MappingStatus {
  sourcePortStatus: Record<string, PortStatus>
  targetPortStatus: Record<string, PortStatus>
  counts: MappingCounts
}

export type EndpointInfo = (nodeId: string, handleId: string) => { type: PortType; sub?: string } | null

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

/** Per-port state (§12) plus toolbar counts derived from the current edges. */
export const computeStatus = (
  edges: Edge[],
  sourceFields: Map<string, FieldNode>,
  targetFields: Map<string, FieldNode>,
  endpointInfo: EndpointInfo,
): MappingStatus => {
  const sourcePortStatus: Record<string, PortStatus> = {}
  const targetPortStatus: Record<string, PortStatus> = {}

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
  let errors = 0

  targetFields.forEach((field, path) => {
    const edge = targetEdges.find(e => (e.targetHandle ?? '') === path)
    const isLeaf = !field.children?.length

    // Directly connected target port.
    if (edge) {
      const src = endpointInfo(edge.source, edge.sourceHandle ?? '')
      // Port category mismatch (scalar/geometry vs object/array), OR — for scalar/geometry
      // ports — a concrete subtype mismatch such as Point vs Polygon. The source's `sub`
      // carries the concrete field type, so comparing it to the target's `type` catches it directly.
      const hasCategoryMismatch = !!src && src.type !== field.portType
      const hasSubtypeMismatch =
        !!src && (field.portType === 'scalar' || field.portType === 'geometry') && !!src.sub && src.sub !== field.type
      const isMismatch = hasCategoryMismatch || hasSubtypeMismatch

      targetPortStatus[path] = isMismatch ? 'mismatch' : 'mapped'

      if (isLeaf && isMismatch) errors++
      else if (isLeaf) mapped++
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

  return { sourcePortStatus, targetPortStatus, counts: { mapped, unmapped, errors } }
}
