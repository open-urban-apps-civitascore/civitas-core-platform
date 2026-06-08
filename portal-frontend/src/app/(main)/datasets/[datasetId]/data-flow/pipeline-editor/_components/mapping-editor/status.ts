import type { Edge } from '@xyflow/react'

import type { PortStatus } from '@/components/node-editor'
import type { PortType } from '@/components/node-editor/types'

import type { FieldNode } from './_types'
import { SOURCE_NODE_ID, TARGET_NODE_ID } from './compile'

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

const isCoveredByAncestor = (path: string, mapped: Set<string>): boolean => {
  for (const ancestor of mapped) {
    if (path === ancestor) return true
    if (path.startsWith(ancestor) && (path[ancestor.length] === '.' || path[ancestor.length] === '[')) return true
  }
  return false
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

  const mappedTargets = new Set(edges.filter(e => e.target === TARGET_NODE_ID).map(e => e.targetHandle ?? ''))

  let mapped = 0
  let unmapped = 0
  let errors = 0

  targetFields.forEach((field, path) => {
    const edge = edges.find(e => e.target === TARGET_NODE_ID && (e.targetHandle ?? '') === path)
    const isLeaf = !field.children?.length

    if (edge) {
      const src = endpointInfo(edge.source, edge.sourceHandle ?? '')
      const isMismatch = !!src && src.type !== field.portType
      targetPortStatus[path] = isMismatch ? 'mismatch' : 'mapped'
      if (isLeaf && isMismatch) errors++
      else if (isLeaf) mapped++
      return
    }

    const isCovered = isCoveredByAncestor(path, mappedTargets)
    targetPortStatus[path] = isCovered ? 'mapped' : 'unmapped'
    if (isLeaf && isCovered) mapped++
    else if (isLeaf) unmapped++
  })

  return { sourcePortStatus, targetPortStatus, counts: { mapped, unmapped, errors } }
}
