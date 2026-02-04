'use client'

/**
 * FrostNode Component
 *
 * Pipeline node for FROST (SensorThings API) persistence.
 * Shows configured FROST server or "Not configured" state.
 * Uses the BasePipelineNode for consistent styling.
 *
 */

import type { NodeProps } from '@xyflow/react'
import { Snowflake } from 'lucide-react'

import type { FrostNodeData } from '../../../_types/nodes'
import { isFrostNodeData } from '../../../_types/nodes'
import { BasePipelineNode } from '../base/BasePipelineNode'

// ============================================================================
// Component
// ============================================================================

/**
 * FROST node component - activity style with Snowflake icon.
 * Displays selected FROST server information or "Not configured" state.
 *
 */
export const FrostNode: React.FC<NodeProps> = ({ data, selected: isSelected = false }) => {
  // Type guard to ensure we have the correct data shape
  const nodeData = data as FrostNodeData

  // Determine if node is configured (has an entity selected)
  const isConfigured = isFrostNodeData(nodeData) && nodeData.entityId !== undefined

  return (
    <BasePipelineNode
      category="storage"
      isConfigured={isConfigured}
      isSelected={isSelected}
      label={nodeData.label || 'FROST'}
      sublabel={isConfigured ? nodeData.entityName : undefined}
      icon={Snowflake}
    />
  )
}
