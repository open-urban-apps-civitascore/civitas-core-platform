'use client'

/**
 * ApiResponseNode Component
 *
 * Pipeline node for REST API response handling.
 * Auto-configured with a dynamically generated API path.
 * Uses the BasePipelineNode for consistent styling.
 *
 */

import type { NodeProps } from '@xyflow/react'
import { Reply } from 'lucide-react'

import type { ApiNodeData } from '../../../_types/nodes'
import { BasePipelineNode } from '../base/BasePipelineNode'

// ============================================================================
// Component
// ============================================================================

/**
 * API Response node component - activity style with Reply icon.
 * Always configured with the platform's generated API path.
 *
 */
export const ApiResponseNode: React.FC<NodeProps> = ({ data, selected: isSelected = false }) => {
  const nodeData = data as ApiNodeData

  return (
    <BasePipelineNode
      category="trigger"
      isConfigured={true}
      isSelected={isSelected}
      label={nodeData.label || 'API Response'}
      sublabel={nodeData.apiPath}
      icon={Reply}
    />
  )
}
