'use client'

/**
 * ApiRequestNode Component
 *
 * Pipeline node for REST API request triggers.
 * Shows configured API endpoint or "Not configured" state.
 * Uses the BasePipelineNode for consistent styling.
 *
 */

import type { NodeProps } from '@xyflow/react'
import { Globe } from 'lucide-react'

import type { ApiNodeData } from '../../../_types/nodes'
import { isApiNodeData } from '../../../_types/nodes'
import { BasePipelineNode } from '../base/BasePipelineNode'

// ============================================================================
// Component
// ============================================================================

/**
 * API Request node component - activity style with Globe icon.
 * Displays selected API endpoint information or "Not configured" state.
 *
 */
export const ApiRequestNode: React.FC<NodeProps> = ({ data, selected: isSelected = false }) => {
  // Type guard to ensure we have the correct data shape
  const nodeData = data as ApiNodeData

  // Determine if node is configured (has an entity selected)
  const isConfigured = isApiNodeData(nodeData) && nodeData.entityId !== undefined

  return (
    <BasePipelineNode
      category="trigger"
      isConfigured={isConfigured}
      isSelected={isSelected}
      label={nodeData.label || 'API Request'}
      sublabel={isConfigured ? nodeData.entityName : undefined}
      icon={Globe}
    />
  )
}
