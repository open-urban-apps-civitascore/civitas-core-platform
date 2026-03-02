'use client'

/**
 * FrostNode Component
 *
 * Pipeline node for FROST (SensorThings API) persistence.
 * Auto-configured with the platform's fixed FROST server.
 * Uses the BasePipelineNode for consistent styling.
 *
 */

import type { NodeProps } from '@xyflow/react'
import { Snowflake } from 'lucide-react'

import type { FrostNodeData } from '../../../_types/nodes'
import { BasePipelineNode } from '../base/BasePipelineNode'

// ============================================================================
// Component
// ============================================================================

/**
 * FROST node component - activity style with Snowflake icon.
 * Always configured with the platform's fixed FROST server.
 *
 */
export const FrostNode: React.FC<NodeProps> = ({ data, selected: isSelected = false }) => {
  const nodeData = data as FrostNodeData

  return (
    <BasePipelineNode
      category="storage"
      isConfigured={true}
      isSelected={isSelected}
      label={nodeData.label || 'FROST'}
      sublabel={nodeData.serverName}
      icon={Snowflake}
    />
  )
}
