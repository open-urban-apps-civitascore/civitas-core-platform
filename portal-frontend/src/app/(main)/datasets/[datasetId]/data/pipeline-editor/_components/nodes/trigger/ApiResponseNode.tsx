'use client'

/**
 * ApiResponseNode Component
 *
 * Pipeline node for REST API response handling.
 * Shows configured API endpoint or "Not configured" state.
 * Uses the BasePipelineNode for consistent styling.
 *
 */

import type { NodeProps } from '@xyflow/react'
import { Reply } from 'lucide-react'

import type { ApiNodeData } from '../../../_types/nodes'
import { isApiNodeData } from '../../../_types/nodes'
import { BasePipelineNode } from '../base/BasePipelineNode'

// ============================================================================
// Component
// ============================================================================

/**
 * API Response node component - activity style with Reply icon.
 * Displays selected API endpoint information or "Not configured" state.
 *
 */
export const ApiResponseNode: React.FC<NodeProps> = ({ data, selected: isSelected = false }) => {
  // Type guard to ensure we have the correct data shape
  const nodeData = data as ApiNodeData

  // Determine if node is configured (has an entity selected)
  const isConfigured = isApiNodeData(nodeData) && nodeData.entityId !== undefined

  return (
    <BasePipelineNode
      category="trigger"
      isConfigured={isConfigured}
      isSelected={isSelected}
      label={nodeData.label || 'API Response'}
      sublabel={isConfigured ? nodeData.entityName : undefined}
      icon={Reply}
    />
  )
}
