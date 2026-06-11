'use client'

/**
 * MappingNode Component
 *
 * Entry node for the Schema-as-MegaNode mapping editor.
 * Shows the mapping name and the source → target schemas when configured.
 */

import type { NodeProps } from '@xyflow/react'
import { Workflow } from 'lucide-react'

import type { MappingNodeData } from '../../../_types/nodes'
import { BasePipelineNode } from '../base/BasePipelineNode'

export const MappingNode: React.FC<NodeProps> = ({ data, selected: isSelected = false }) => {
  const nodeData = data as MappingNodeData
  const isConfigured = Boolean(nodeData.configured)
  const sublabel =
    nodeData.sourceName && nodeData.targetName ? `${nodeData.sourceName} → ${nodeData.targetName}` : undefined

  return (
    <BasePipelineNode
      category="transformation"
      isConfigured={isConfigured}
      isSelected={isSelected}
      label={nodeData.label || 'Mapping'}
      sublabel={sublabel}
      icon={Workflow}
    />
  )
}
