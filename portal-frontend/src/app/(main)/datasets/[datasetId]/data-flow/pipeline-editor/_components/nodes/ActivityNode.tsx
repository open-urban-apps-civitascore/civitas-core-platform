'use client'

/**
 * ActivityNode — the single, registry-driven pipeline node component.
 *
 * Replaces the former per-type node components (StartNode, EndNode, DataSourceNode,
 * FrostNode, GeoPersistenceNode, MappingNode, CronNode). Each node's appearance is
 * derived from its registry definition (`PipelineNodeDef`) plus its data.
 */

import type { NodeProps } from '@xyflow/react'

import { getNodeDef } from '../../_config/nodeRegistry'
import type { PipelineNodeData } from '../../_types/nodes'
import type { PipelineNodeType } from '../../_types/pipeline'
import { BasePipelineNode } from './base/BasePipelineNode'
import { ControlNode } from './base/ControlNode'

/** Build the React Flow node component for a given pipeline node type. */

export const createActivityNode = (type: PipelineNodeType) => {
  const def = getNodeDef(type)

  const ActivityNode: React.FC<NodeProps> = ({ data, selected: isSelected = false }) => {
    const nodeData = data as PipelineNodeData
    const isConfigured = Boolean(nodeData.configured)

    // UML-style circular control nodes (start / end) render their own shape.
    if (def?.shape === 'controlStart' || def?.shape === 'controlEnd') {
      return <ControlNode variant={def.shape === 'controlStart' ? 'start' : 'end'} isSelected={isSelected} />
    }

    // Configured nodes may resolve their sublabel via a component (async / permission-gated)
    // or a pure function. Unconfigured nodes show no sublabel so the "not configured" hint wins.
    const SublabelComponent = def?.SublabelComponent
    let sublabel: React.ReactNode
    if (SublabelComponent) {
      sublabel = isConfigured ? <SublabelComponent data={nodeData} /> : undefined
    } else {
      sublabel = def?.getSublabel?.(nodeData)
    }

    return (
      <BasePipelineNode
        category={def?.category ?? 'general'}
        isConfigured={isConfigured}
        isSelected={isSelected}
        label={nodeData.label || (def?.type ?? '')}
        sublabel={sublabel}
        icon={def?.icon}
        hasLeftHandle={def?.handles.left ?? true}
        hasRightHandle={def?.handles.right ?? true}
      />
    )
  }
  ActivityNode.displayName = `ActivityNode(${type})`
  return ActivityNode
}
